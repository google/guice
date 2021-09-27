/*
 * Copyright (C) 2020 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.inject.internal.aop;

import static java.lang.reflect.Modifier.PUBLIC;
import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ARRAYLENGTH;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_8;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.internal.InternalFlags;
import com.google.inject.internal.InternalFlags.CustomClassLoadingOption;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

/**
 * {@link ClassDefiner} that defines classes using {@code sun.misc.Unsafe}.
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
final class UnsafeClassDefiner implements ClassDefiner {

  private static final Logger logger = Logger.getLogger(UnsafeClassDefiner.class.getName());

  private static final ClassDefiner UNSAFE_DEFINER;

  static {
    ClassDefiner unsafeDefiner =
        tryPrivileged(AnonymousClassDefiner::new, "Cannot bind Unsafe.defineAnonymousClass");
    if (unsafeDefiner == null) {
      unsafeDefiner =
          tryPrivileged(
              HiddenClassDefiner::new, "Cannot bind MethodHandles.Lookup.defineHiddenClass");
    }
    UNSAFE_DEFINER = unsafeDefiner;
  }

  private static final boolean ALWAYS_DEFINE_ANONYMOUSLY =
      InternalFlags.getCustomClassLoadingOption() == CustomClassLoadingOption.ANONYMOUS;

  private static final String DEFINEACCESS_BY_GUICE_MARKER = "$$DefineAccessByGuice$$";

  private static final String[] DEFINEACCESS_API = {"java/util/function/BiFunction"};

  private static final String CLASS_LOADER_TYPE = Type.getInternalName(ClassLoader.class);

  private static final String BYTE_ARRAY_TYPE = Type.getInternalName(byte[].class);

  // initialization-on-demand...
  private static class ClassLoaderDefineClassHolder {
    static final ClassDefiner CLASS_LOADER_DEFINE_CLASS =
        tryPrivileged(
            () -> accessDefineClass(ClassLoader.class), "Cannot access ClassLoader.defineClass");
  }

  // initialization-on-demand...
  private static class DefineClassCacheHolder {
    static final LoadingCache<Class<?>, ClassDefiner> DEFINE_CLASS_CACHE =
        CacheBuilder.newBuilder()
            .weakKeys()
            .build(CacheLoader.from(UnsafeClassDefiner::tryAccessDefineClass));
  }

  /** Do we have access to {@code sun.misc.Unsafe}? */
  public static boolean isAccessible() {
    return UNSAFE_DEFINER != null;
  }

  /** Returns true if it's possible to load by name proxies defined from the given host. */
  public static boolean canLoadProxyByName(Class<?> hostClass) {
    return findClassDefiner(hostClass.getClassLoader()) != UNSAFE_DEFINER;
  }

  /** Returns true if it's possible to downcast to proxies defined from the given host. */
  public static boolean canDowncastToProxy(Class<?> hostClass) {
    return !(findClassDefiner(hostClass.getClassLoader()) instanceof AnonymousClassDefiner);
  }

  @Override
  public Class<?> define(Class<?> hostClass, byte[] bytecode) throws Exception {
    return findClassDefiner(hostClass.getClassLoader()).define(hostClass, bytecode);
  }

  /** Finds the appropriate class definer for the given class loader. */
  private static ClassDefiner findClassDefiner(ClassLoader hostLoader) {
    if (hostLoader == null || ALWAYS_DEFINE_ANONYMOUSLY) {
      return UNSAFE_DEFINER;
    } else if (ClassLoaderDefineClassHolder.CLASS_LOADER_DEFINE_CLASS != null) {
      // we have access to the defineClass method of anything extending ClassLoader
      return ClassLoaderDefineClassHolder.CLASS_LOADER_DEFINE_CLASS;
    } else {
      // can't access ClassLoader, try accessing the specific sub-class instead
      return DefineClassCacheHolder.DEFINE_CLASS_CACHE.getUnchecked(hostLoader.getClass());
    }
  }

  static <T> T tryPrivileged(PrivilegedExceptionAction<T> action, String errorMessage) {
    try {
      return AccessController.doPrivileged(action);
    } catch (Throwable e) {
      logger.log(Level.FINE, errorMessage, e);
      return null;
    }
  }

  static ClassDefiner tryAccessDefineClass(Class<?> loaderClass) {
    try {
      logger.log(Level.FINE, "Accessing defineClass method in %s", loaderClass);
      return AccessController.doPrivileged(
          (PrivilegedExceptionAction<ClassDefiner>) () -> accessDefineClass(loaderClass));
    } catch (Throwable e) {
      logger.log(Level.FINE, "Cannot access defineClass method in " + loaderClass, e);
      return UNSAFE_DEFINER;
    }
  }

  /** Generates helper in same package as the {@link ClassLoader} so it can access defineClass */
  @SuppressWarnings("unchecked")
  static ClassDefiner accessDefineClass(Class<?> loaderClass) throws Exception {
    byte[] bytecode = buildDefineClassAccess(loaderClass);
    Class<?> accessClass = UNSAFE_DEFINER.define(loaderClass, bytecode);
    return new GeneratedClassDefiner(
        (BiFunction<ClassLoader, byte[], Class<?>>)
            accessClass.getDeclaredConstructor().newInstance());
  }

  /** {@link ClassLoader} helper that sits in the same package and passes on defineClass requests */
  private static byte[] buildDefineClassAccess(Class<?> loaderClass) {
    ClassWriter cw = new ClassWriter(COMPUTE_MAXS);

    // target Java8 because that's all we need for the generated helper
    cw.visit(
        V1_8,
        PUBLIC | ACC_SUPER,
        loaderClass.getName().replace('.', '/') + DEFINEACCESS_BY_GUICE_MARKER,
        null,
        "java/lang/Object",
        DEFINEACCESS_API);

    MethodVisitor mv;

    mv = cw.visitMethod(PUBLIC, "<init>", "()V", null, null);
    mv.visitCode();
    mv.visitVarInsn(ALOAD, 0);
    mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    mv.visitInsn(RETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();

    mv =
        cw.visitMethod(
            PUBLIC,
            "apply",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
            null,
            null);

    mv.visitCode();

    mv.visitVarInsn(ALOAD, 1);
    mv.visitTypeInsn(CHECKCAST, CLASS_LOADER_TYPE);
    mv.visitInsn(ACONST_NULL);
    mv.visitVarInsn(ALOAD, 2);
    mv.visitTypeInsn(CHECKCAST, BYTE_ARRAY_TYPE);
    mv.visitInsn(ICONST_0);
    mv.visitVarInsn(ALOAD, 2);
    mv.visitTypeInsn(CHECKCAST, BYTE_ARRAY_TYPE);
    mv.visitInsn(ARRAYLENGTH);

    mv.visitMethodInsn(
        INVOKEVIRTUAL,
        "java/lang/ClassLoader",
        "defineClass",
        "(Ljava/lang/String;[BII)Ljava/lang/Class;",
        false);

    mv.visitInsn(ARETURN);

    mv.visitMaxs(0, 0);
    mv.visitEnd();
    cw.visitEnd();

    return cw.toByteArray();
  }
}
