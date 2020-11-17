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
import static java.lang.reflect.Modifier.STATIC;
import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ARRAYLENGTH;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.V1_8;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.internal.InternalFlags;
import com.google.inject.internal.InternalFlags.CustomClassLoadingOption;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

/**
 * {@link ClassDefiner} that defines classes using {@code sun.misc.Unsafe}.
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
final class UnsafeClassDefiner implements ClassDefiner {

  private static final Logger logger = Logger.getLogger(UnsafeClassDefiner.class.getName());

  private static final Object THE_UNSAFE =
      tryPrivileged(UnsafeClassDefiner::bindTheUnsafe, "Cannot bind the Unsafe instance");

  private static final Method ANONYMOUS_DEFINE_METHOD =
      tryPrivileged(
          UnsafeClassDefiner::bindAnonymousDefineMethod, "Cannot bind Unsafe.defineAnonymousClass");

  private static final boolean ALWAYS_DEFINE_ANONYMOUSLY =
      InternalFlags.getCustomClassLoadingOption() == CustomClassLoadingOption.ANONYMOUS;

  private static final String DEFINEACCESS_BY_GUICE_MARKER = "$$DefineAccessByGuice$$";

  // initialization-on-demand...
  private static class ClassLoaderDefineMethodHolder {
    static final Method CLASS_LOADER_DEFINE_METHOD =
        tryPrivileged(
            () -> accessDefineMethod(ClassLoader.class), "Cannot access ClassLoader.defineClass");
  }

  // initialization-on-demand...
  private static class DefineMethodCacheHolder {
    static final LoadingCache<Class<?>, Method> DEFINE_METHOD_CACHE =
        CacheBuilder.newBuilder()
            .weakKeys()
            .weakValues()
            .build(CacheLoader.from(UnsafeClassDefiner::tryAccessDefineMethod));
  }

  /** Do we have access to {@code sun.misc.Unsafe}? */
  public static boolean isAccessible() {
    return ANONYMOUS_DEFINE_METHOD != null;
  }

  /** Does the given class host new types anonymously, ie. by using defineAnonymousClass? */
  @SuppressWarnings("ReferenceEquality") // intentional
  public static boolean isAnonymousHost(Class<?> hostClass) {
    return findDefineMethod(hostClass.getClassLoader()) == ANONYMOUS_DEFINE_METHOD;
  }

  @SuppressWarnings("ReferenceEquality") // intentional
  @Override
  public Class<?> define(Class<?> hostClass, byte[] bytecode) throws Exception {
    ClassLoader hostLoader = hostClass.getClassLoader();
    Method defineMethod = findDefineMethod(hostLoader);
    if (defineMethod == ANONYMOUS_DEFINE_METHOD) {
      return defineAnonymously(hostClass, bytecode);
    }
    return (Class<?>) defineMethod.invoke(null, hostLoader, bytecode);
  }

  /** Finds the appropriate class defining method for the given class loader. */
  private static Method findDefineMethod(ClassLoader hostLoader) {
    if (hostLoader == null || ALWAYS_DEFINE_ANONYMOUSLY) {
      return ANONYMOUS_DEFINE_METHOD;
    } else if (ClassLoaderDefineMethodHolder.CLASS_LOADER_DEFINE_METHOD != null) {
      // we have access to the defineClass method of anything extending ClassLoader
      return ClassLoaderDefineMethodHolder.CLASS_LOADER_DEFINE_METHOD;
    } else {
      // can't access ClassLoader, try accessing the specific sub-class instead
      return DefineMethodCacheHolder.DEFINE_METHOD_CACHE.getUnchecked(hostLoader.getClass());
    }
  }

  private static Class<?> defineAnonymously(Class<?> hostClass, byte[] bytecode) throws Exception {
    return (Class<?>) ANONYMOUS_DEFINE_METHOD.invoke(THE_UNSAFE, hostClass, bytecode, null);
  }

  private static Object bindTheUnsafe() throws Exception {
    Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
    Field theUnsafeField = unsafeType.getDeclaredField("theUnsafe");
    theUnsafeField.setAccessible(true);
    return theUnsafeField.get(null);
  }

  private static Method bindAnonymousDefineMethod() throws Exception {
    Class<?> unsafeType = THE_UNSAFE.getClass();
    // defineAnonymousClass has all the functionality we need and is available in Java 7 onwards
    return unsafeType.getMethod("defineAnonymousClass", Class.class, byte[].class, Object[].class);
  }

  static <T> T tryPrivileged(PrivilegedExceptionAction<T> action, String errorMessage) {
    try {
      return AccessController.doPrivileged(action);
    } catch (Throwable e) {
      logger.log(Level.FINE, errorMessage, e);
      return null;
    }
  }

  static Method tryAccessDefineMethod(Class<?> loaderClass) {
    try {
      logger.log(Level.FINE, "Accessing defineClass method in %s", loaderClass);
      return AccessController.doPrivileged(
          (PrivilegedExceptionAction<Method>) () -> accessDefineMethod(loaderClass));
    } catch (Throwable e) {
      logger.log(Level.FINE, "Cannot access defineClass method in " + loaderClass, e);
      return ANONYMOUS_DEFINE_METHOD;
    }
  }

  /** Generates helper in same package as the {@link ClassLoader} so it can access defineClass */
  static Method accessDefineMethod(Class<?> loaderClass) throws Exception {
    byte[] bytecode = buildDefineClassAccess(loaderClass);
    Class<?> accessClass = defineAnonymously(loaderClass, bytecode);
    return accessClass.getMethod("defineClass", ClassLoader.class, byte[].class);
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
        null);

    MethodVisitor mv =
        cw.visitMethod(
            PUBLIC | STATIC,
            "defineClass",
            "(Ljava/lang/ClassLoader;[B)Ljava/lang/Class;",
            null,
            null);

    mv.visitCode();

    mv.visitVarInsn(ALOAD, 0);
    mv.visitInsn(ACONST_NULL);
    mv.visitVarInsn(ALOAD, 1);
    mv.visitInsn(ICONST_0);
    mv.visitVarInsn(ALOAD, 1);
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
