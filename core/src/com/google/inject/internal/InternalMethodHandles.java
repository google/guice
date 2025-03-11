/*
 * Copyright (C) 2025 Google Inc.
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
package com.google.inject.internal;

import static java.lang.invoke.MethodType.methodType;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PROTECTED;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V11;

import com.google.errorprone.annotations.ForOverride;
import com.google.errorprone.annotations.Keep;
import com.google.inject.Provider;
import com.google.inject.internal.InjectorImpl.InjectorOptions;
import com.google.inject.internal.aop.ClassDefining;
import com.google.inject.spi.Dependency;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

/** Utility methods for working with method handles and our internal guice protocols. */
public final class InternalMethodHandles {
  private static final MethodHandles.Lookup lookup = MethodHandles.lookup();
  private static final MethodType OBJECT_FACTORY_TYPE =
      methodType(Object.class, InternalContext.class);

  static MethodType makeFactoryType(Dependency<?> dependency) {
    return methodType(dependency.getKey().getTypeLiteral().getRawType(), InternalContext.class);
  }

  /** Direct handle for {@link InternalFactory#get} */
  static final MethodHandle INTERNAL_FACTORY_GET_HANDLE =
      findVirtualOrDie(
          InternalFactory.class,
          "get",
          methodType(Object.class, InternalContext.class, Dependency.class, boolean.class));

  static MethodHandle findStaticOrDie(Class<?> clazz, String name, MethodType type) {
    try {
      return lookup.findStatic(clazz, name, type);
    } catch (ReflectiveOperationException e) {
      throw new LinkageError("missing method: " + name + ": " + type, e);
    }
  }

  static MethodHandle findVirtualOrDie(Class<?> clazz, String name, MethodType type) {
    try {
      return lookup.findVirtual(clazz, name, type);
    } catch (ReflectiveOperationException e) {
      throw new LinkageError("missing method: " + name + ": " + type, e);
    }
  }

  static MethodHandle findConstructorOrDie(Class<?> clazz, MethodType type) {
    try {
      return lookup.findConstructor(clazz, type);
    } catch (ReflectiveOperationException e) {
      throw new LinkageError("missing constructor: " + type, e);
    }
  }

  /**
   * Generates a provider instance that delegates to the given factory.
   *
   * <p>This leverages the {@link InternalFactory#getHandle} method, but it only invokes it lazily.
   */
  static <T> Provider<T> makeProvider(
      InternalFactory<T> factory, InjectorImpl injector, Dependency<?> dependency) {
    // This is safe due to the implementation of InternalFactory.getHandle which we cannot enforce
    // with generic type constraints.
    InjectorOptions options = injector.options;
    @SuppressWarnings("unchecked")
    Provider<T> typedProvider =
        (Provider)
            ProviderMaker.defineClass(
                injector,
                dependency,
                /* name= */ dependency.getKey().getTypeLiteral().getRawType().getSimpleName(),
                // TODO(b/366058184): Decide if this laziness is required. Because `makeProvider` is
                // called during injector initialization it is possible that it is too early for
                // some factories.  However, by binding a lazy provider we delay that linkage until
                // the first time the provider is actually invoked which is by definition late
                // enough.
                () ->
                    factory
                        .getHandle(options, dependency, /* linked= */ false)
                        .asType(OBJECT_FACTORY_TYPE),
                factory.toString());
    return typedProvider;
  }

  /**
   * A base class for generated providers.
   *
   * <p>This class encapsulates the logic for entering and exiting the injector context, and
   * handling {@link InternalProvisionException}s.
   */
  public abstract static class GeneratedProvider<T> implements Provider<T> {
    private final InjectorImpl injector;
    private final Dependency<?> dependency;

    protected GeneratedProvider(InjectorImpl injector, Dependency<?> dependency) {
      this.injector = injector;
      this.dependency = dependency;
    }

    @Override
    public T get() {
      InternalContext currentContext = injector.enterContext();
      try {
        return doGet(currentContext);
      } catch (InternalProvisionException e) {
        throw e.addSource(dependency).toProvisionException();
      } finally {
        currentContext.close();
      }
    }

    @ForOverride
    protected abstract T doGet(InternalContext context) throws InternalProvisionException;
  }

  /**
   * A class that can be used to generate a provider instance that delegates to a method handle.
   *
   * <p>Ideally we would just use the jdk internal mechanism for this that lambdas use, but they
   * require 'direct' MethodHandles and our MethodHandles are typically built out of combinators.
   */
  static final class ProviderMaker {
    private static final Type PROVIDER_TYPE = Type.getType(GeneratedProvider.class);
    private static final String CTOR_TYPE =
        methodType(void.class, InjectorImpl.class, Dependency.class).toMethodDescriptorString();
    private static final String HANDLE_DESCRIPTOR = OBJECT_FACTORY_TYPE.toMethodDescriptorString();
    private static final Handle BOOSTRAP_HANDLE =
        new Handle(
            H_INVOKESTATIC,
            Type.getType(InternalMethodHandles.class).getInternalName(),
            "bootstrapHandle",
            methodType(CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class)
                .toMethodDescriptorString(),
            /* isInterface= */ false);

    private static final ConcurrentHashMap<String, Integer> nameUses = new ConcurrentHashMap<>();

    /**
     * Defines and constructs a stateless provider class that delegates to the given handleCreator.
     *
     * <p>The handleCreator is called at most once per generated provider instance.
     */
    static Provider<?> defineClass(
        InjectorImpl injector,
        Dependency<?> dependency,
        String name,
        Supplier<MethodHandle> handleCreator,
        String toString) {
      // Even if we are using the anonymous classloading mechanisms we still need to pick a name,
      // it just ends up being ignored.
      String actualName =
          InternalMethodHandles.class.getPackageName().replace('.', '/')
              + "/GeneratedProvider$"
              + name
              + "$"
              + nameUses.compute(name, (key, value) -> value == null ? 0 : value + 1);

      ClassWriter cw = new ClassWriter(0);
      cw.visit(
          V11, // need access to invokeDynamic and constant dynamic
          ACC_PUBLIC | ACC_SUPER | ACC_FINAL,
          actualName,
          /* signature= */ null,
          PROVIDER_TYPE.getInternalName(),
          /* interfaces= */ null);
      // Allocate a static field to store the handle creator or the resolved callsite.
      cw.visitField(
          ACC_PUBLIC | ACC_STATIC,
          "definer",
          "Ljava/lang/Object;",
          /* signature= */ null,
          /* value= */ null);
      {
        // generate a default constructor
        // This is just a default constructor that calls the Object constructor.
        // basically `super();`
        MethodVisitor mv =
            cw.visitMethod(
                ACC_PUBLIC, "<init>", CTOR_TYPE, /* signature= */ null, /* exceptions= */ null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ALOAD, 2);
        mv.visitMethodInsn(
            INVOKESPECIAL,
            PROVIDER_TYPE.getInternalName(),
            "<init>",
            CTOR_TYPE,
            /* isInterface= */ false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(
            /* maxStack= */ 3, // Just pushing the parameters
            /* maxLocals= */ 3 // all the parameters
            );
        mv.visitEnd();
      }
      {
        // generate a toString() method
        // This just returns the the `toString` argument.
        MethodVisitor mv =
            cw.visitMethod(
                ACC_PUBLIC | ACC_FINAL,
                "toString",
                "()Ljava/lang/String;",
                /* signature= */ null,
                /* exceptions= */ null);
        mv.visitCode();
        mv.visitLdcInsn(toString);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(
            /* maxStack= */ 1, // Just pushing the string constant
            /* maxLocals= */ 1 // Just the 'this' variable.
            );
        mv.visitEnd();
      }
      {
        // generate a doGet() method
        MethodVisitor mv =
            cw.visitMethod(
                ACC_PROTECTED | ACC_FINAL,
                "doGet",
                OBJECT_FACTORY_TYPE.toMethodDescriptorString(),
                /* signature= */ null,
                /* exceptions= */ null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 1); // ctx
        mv.visitInvokeDynamicInsn("get", HANDLE_DESCRIPTOR, BOOSTRAP_HANDLE);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(
            /* maxStack= */ 1, // Just the return value of the dynamic call
            /* maxLocals= */ 2 // Just the 'this' and ctx parameters
            );
        mv.visitEnd();
      }
      // We must define the class as collectable so that it can be garbage collected.  The static
      // field will hold a reference to an InternalFactory which may hold references to the
      // injector.
      Class<?> clazz;
      try {
        clazz =
            ClassDefining.defineCollectable(
                /* lifetimeOwner= */ injector, GeneratedProvider.class, cw.toByteArray());
      } catch (Exception e) {
        throw new LinkageError("failed to define class", e);
      }
      try {
        clazz.getField("definer").set(null, handleCreator);
      } catch (ReflectiveOperationException e) {
        throw new LinkageError("missing or inaccessible field: definer", e);
      }
      try {
        return (Provider<?>)
            clazz
                .getConstructor(InjectorImpl.class, Dependency.class)
                .newInstance(injector, dependency);
      } catch (ReflectiveOperationException e) {
        throw new LinkageError("missing or inaccessible constructor", e);
      }
    }

    private ProviderMaker() {}
  }

  /**
   * Our bootstrap method that is called by the generated provider.
   *
   * <p>The JVM calls this the first time the invokeDynamic instruction is executed. See
   * https://docs.oracle.com/javase/8/docs/api/java/lang/invoke/package-summary.html for a high
   * level description of how this works.
   */
  @Keep
  public static CallSite bootstrapHandle(MethodHandles.Lookup lookup, String name, MethodType type)
      throws NoSuchFieldException, IllegalAccessException {
    VarHandle varHandle = lookup.findStaticVarHandle(lookup.lookupClass(), "definer", Object.class);

    // Use double-checked locking to ensure we only invoke the supplier once.
    // The JVM may call this method multiple times in parallel, though only one will ultimately
    // succeed in bootstrapping the instruction so we synchronize on the class to ensure that
    // we only create one handle.
    Object handleCreator = varHandle.getVolatile();
    if (handleCreator instanceof Supplier) {
      synchronized (lookup.getClass()) {
        handleCreator = varHandle.get();
        if (handleCreator instanceof Supplier) {
          ConstantCallSite callSite =
              new ConstantCallSite((MethodHandle) ((Supplier) handleCreator).get());
          varHandle.setVolatile((Object) callSite);
          return callSite;
        }
      }
    }
    return (ConstantCallSite) handleCreator;
  }

  private InternalMethodHandles() {}
}
