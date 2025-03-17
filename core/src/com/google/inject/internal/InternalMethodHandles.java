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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Streams.stream;
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.ForOverride;
import com.google.errorprone.annotations.Keep;
import com.google.inject.Provider;
import com.google.inject.internal.InjectorImpl.InjectorOptions;
import com.google.inject.internal.ProvisionListenerStackCallback.ProvisionCallback;
import com.google.inject.internal.aop.ClassDefining;
import com.google.inject.spi.Dependency;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

/** Utility methods for working with method handles and our internal guice protocols. */
public final class InternalMethodHandles {
  private static final MethodHandles.Lookup lookup = MethodHandles.lookup();
  static final MethodType OBJECT_FACTORY_TYPE = makeFactoryType(Object.class);

  static MethodType makeFactoryType(Dependency<?> dependency) {
    return makeFactoryType(dependency.getKey().getTypeLiteral().getRawType());
  }

  static MethodType makeFactoryType(Class<?> forType) {
    return methodType(forType, InternalContext.class);
  }

  static MethodHandle castReturnToObject(MethodHandle handle) {
    return castReturnTo(handle, Object.class);
  }

  static MethodHandle castReturnTo(MethodHandle handle, Class<?> returnType) {
    return handle.asType(handle.type().changeReturnType(returnType));
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
   * Returns a method handle with the signature {@code (InternalContext) -> T} that returns the
   * given instance.
   */
  static MethodHandle constantFactoryGetHandle(Dependency<?> dependency, Object instance) {
    return constantFactoryGetHandle(dependency.getKey().getTypeLiteral().getRawType(), instance);
  }

  /**
   * Returns a method handle with the signature {@code (InternalContext) -> T} that returns the
   * given instance.
   */
  static MethodHandle constantFactoryGetHandle(Class<?> forType, Object instance) {
    var constant = MethodHandles.constant(forType, instance);
    return MethodHandles.dropArguments(constant, 0, InternalContext.class);
  }

  /**
   * Returns a method handle with the signature {@code (InternalContext) -> T} that returns the
   * result of the given initializable.
   */
  static MethodHandle initializableFactoryGetHandle(
      Initializable<?> initializable, Dependency<?> dependency) {
    return new InitializableCallSite(makeFactoryType(dependency), initializable).dynamicInvoker();
  }

  /**
   * Returns a method handle with the signature {@code (InternalContext) -> T} that returns the
   * result of the given initializable.
   */
  static MethodHandle initializableFactoryGetHandle(
      Initializable<?> initializable, Class<?> forType) {
    return new InitializableCallSite(makeFactoryType(forType), initializable).dynamicInvoker();
  }

  /**
   * A callsite is a code location that can dispatch to a method handle. Here we constructe one in
   * order to implement a simple 'code patching' pattern. Because `Initializable` is guaranteed to
   * always return the same value after constructions we can take advantage of that to implement a
   * lazy init pattern.
   *
   * <p>This generates a method handle that replaces itself with a constant. This is similar to a
   * normal lazy-init pattern but implemented via 'code patching'.
   *
   * <p>In normal java you would implement this like
   *
   * <pre>{@code
   * private static Initialiable delegate = <init>;
   * get(InternalContext context) {
   *   var value = delegate.get(context);
   *   delegate = (ctx) -> value;
   *   return value;
   * }
   * }</pre>
   *
   * <p>Basically we replace the Initializable with a {@link #constantFactoryGetHandle} as soon as
   * the initializable succeeds. This technique is transparent to the JVM and allows the jit to
   * inline and propagate the constant..
   */
  private static final class InitializableCallSite extends MutableCallSite {
    static final MethodHandle BOOTSTRAP_CALL_MH =
        findVirtualOrDie(
            InitializableCallSite.class,
            "bootstrapCall",
            methodType(Object.class, Initializable.class, InternalContext.class));

    InitializableCallSite(MethodType type, Initializable<?> initializable) {
      super(type);
      // Have the first call go to `bootstrapCall` which will set the target to the constant
      // factory get handle if the initializable succeeds.
      setTarget(
          MethodHandles.insertArguments(BOOTSTRAP_CALL_MH.bindTo(this), 0, initializable)
              .asType(type));
    }

    @Keep
    Object bootstrapCall(Initializable<?> initializable, InternalContext context)
        throws InternalProvisionException {
      // If this throws, we will call back through here on the next call which is the typical
      // behavior of initializables.
      // We also do not synchronize and instead rely on the underlying initializable to enforce
      // single-initialization semantics, but we publish via a volatile call site.
      Object value = initializable.get(context);
      setTarget(constantFactoryGetHandle(this.type().returnType(), value));
      // This ensures that other threads will see the new target.  This isn't strictly necessary
      // since Initializable is both ThreadSafe and idempotent, but it should improve performance
      // by allowing the JIT to inline the constant.
      MutableCallSite.syncAll(new MutableCallSite[] {this});
      return value;
    }
  }

  /**
   * Returns a handle that checks the result of the delegate and throws an
   * InternalProvisionException if the result is null using the dependency and source information
   *
   * <p>The returned handle has the same type as the delegate.
   */
  static MethodHandle nullCheckResult(
      MethodHandle delegate, Object source, Dependency<?> dependency) {
    var returnType = delegate.type().returnType();
    if (dependency.isNullable() || returnType.isPrimitive()) {
      return delegate;
    }
    // (T) -> T
    var nullCheckResult =
        MethodHandles.insertArguments(NULL_CHECK_RESULT_MH, 1, source, dependency)
            // Cast the parameter and return types to the delegates types.
            .asType(methodType(returnType, returnType));
    return MethodHandles.filterReturnValue(delegate, nullCheckResult);
  }

  private static final MethodHandle NULL_CHECK_RESULT_MH =
      findStaticOrDie(
          InternalMethodHandles.class,
          "doNullCheckResult",
          methodType(Object.class, Object.class, Object.class, Dependency.class));

  @Keep
  private static Object doNullCheckResult(Object result, Object source, Dependency<?> dependency)
      throws InternalProvisionException {
    if (result == null) {
      // This will either throw, warn or do nothing.
      InternalProvisionException.onNullInjectedIntoNonNullableDependency(source, dependency);
    }
    return result;
  }

  /**
   * Returns a handle with the same signature as the delegate that invokes it through the provision
   * callback, if it is non-null.
   *
   * <p>The returned handle has the same type as the delegate.
   *
   * <p>TODO(lukes): This is a simple and 'manual' solution to the problem of adapting handles to
   * the ProvisionCallback interface. The alternative solution is to generate bytecode for each one,
   * which would unlock more efficient invocations, but bring complexity and overhead.
   * ProvisionListeners should be rare in performance-sensitive code anyway.
   */
  static final MethodHandle invokeThroughProvisionCallback(
      MethodHandle delegate,
      Dependency<?> dependency,
      @Nullable ProvisionListenerStackCallback<?> listener) {
    var type = delegate.type();
    checkArgument(type.parameterType(0).equals(InternalContext.class));
    if (listener == null) {
      return delegate;
    }
    // (InternalContext, ProvisionCallback)->Object
    var provision =
        MethodHandles.insertArguments(
            PROVISION_CALLBACK_PROVISION_HANDLE.bindTo(listener), 1, dependency);
    // Support a few kinds of provision callbacks, as needed.
    // The problem we have is that we need to create a MethodHandle that constructs a
    // ProvisionCallback around the delegate.  If the delegate only accepts an InternalContext then
    // we can construct a constant ProvisionCallback that just calls the delegate.  If the delegate
    // has other 'free' parameters we will need to construct a MethodHandle that accepts those free
    // parameters and constructs a ProvisionCallback that calls the delegate with those parameters
    // plus the InternalContext and the ProvisionCallback.
    switch (type.parameterCount()) {
      case 1:
        {
          // By allocating the delegate here it will be a constant across all invocations.
          var finalDelegate = delegate.asType(OBJECT_FACTORY_TYPE);
          ProvisionCallback<Object> callback =
              (ctx, dep) -> {
                try {
                  // This invokeExact is not as fast as it could be since the `delegate` is not
                  // constant foldable. The only solution to that is custom bytecode generation, but
                  // that probably isn't worth the effort since provision callbacks are rare.
                  return (Object) finalDelegate.invokeExact(ctx);
                } catch (Throwable t) {
                  // This is lame but is needed to work around the different exception semantics of
                  // method handles.  In reality this is either a InternalProvisionException or a
                  // RuntimeException. Both of which are throwable by this method.
                  // The only alternative is to generate bytecode to call the method handle which
                  // avoids this problem at great expense.
                  throw sneakyThrow(t);
                }
              };
          return MethodHandles.insertArguments(provision, 1, callback).asType(delegate.type());
        }
      case 2:
        var callback =
            MethodHandles.insertArguments(
                MAKE_PROVISION_CALLBACK_1_HANDLE,
                0,
                delegate.asType(
                    delegate
                        .type()
                        .changeReturnType(Object.class)
                        .changeParameterType(1, Object.class)));
        return MethodHandles.filterArguments(provision, 1, callback).asType(delegate.type());
      default:
        // We could add support for more cases here as needed.
        throw new IllegalArgumentException(
            "Unexpected number of parameters to delegate: " + type.parameterCount());
    }
  }

  private static final MethodHandle MAKE_PROVISION_CALLBACK_1_HANDLE =
      findStaticOrDie(
          InternalMethodHandles.class,
          "makeProvisionCallback",
          methodType(ProvisionCallback.class, MethodHandle.class, Object.class));

  // A simple factory for the case where the delegate only has a single free parameter.
  @Keep
  private static final ProvisionCallback<Object> makeProvisionCallback(
      MethodHandle delegate, Object capture) {
    return (ctx, dep) -> {
      try {
        return (Object) delegate.invokeExact(ctx, capture);
      } catch (Throwable t) {
        throw sneakyThrow(t);
      }
    };
  }

  private static final MethodHandle PROVISION_CALLBACK_PROVISION_HANDLE =
      findVirtualOrDie(
          ProvisionListenerStackCallback.class,
          "provision",
          methodType(
              Object.class, InternalContext.class, Dependency.class, ProvisionCallback.class));

  private static final MethodHandle INTERNAL_PROVISION_EXCEPTION_ADD_SOURCE_HANDLE =
      findVirtualOrDie(
          InternalProvisionException.class,
          "addSource",
          methodType(InternalProvisionException.class, Object.class));
  private static final MethodHandle INTERNAL_PROVISION_EXCEPTION_ERROR_IN_PROVIDER_HANDLE =
      findStaticOrDie(
          InternalProvisionException.class,
          "errorInProvider",
          methodType(InternalProvisionException.class, Throwable.class));

  /**
   * Surrounds the delegate with a catch block that rethrows the exception with the given source.
   *
   * <pre>{@code
   * try {
   *   return delegate(...);
   * } catch (InternalProvisionException ipe) {
   *   throw ipe.addSource(source);
   * }
   * }</pre>
   */
  static MethodHandle catchInternalProvisionExceptionAndRethrowWithSource(
      MethodHandle delegate, Object source) {
    var addSourceAndRethrow =
        MethodHandles.filterReturnValue(
            MethodHandles.insertArguments(
                INTERNAL_PROVISION_EXCEPTION_ADD_SOURCE_HANDLE, 1, source),
            MethodHandles.throwException(
                delegate.type().returnType(), InternalProvisionException.class));
    return MethodHandles.catchException(
        delegate, InternalProvisionException.class, addSourceAndRethrow);
  }

  /**
   * Surrounds the delegate with a catch block that rethrows the exception with the given source.
   *
   * <pre>{@code
   * try {
   *   return delegate(...);
   * } catch (RuntimeException re) {
   *   throw InternalProvisionException.errorInProvider(re).addSource(source);
   * }
   * }</pre>
   */
  static MethodHandle catchErrorInProviderAndRethrowWithSource(
      MethodHandle delegate, Object source) {
    var addSourceAndRethrow =
        MethodHandles.filterReturnValue(
            MethodHandles.filterReturnValue(
                INTERNAL_PROVISION_EXCEPTION_ERROR_IN_PROVIDER_HANDLE,
                MethodHandles.insertArguments(
                    INTERNAL_PROVISION_EXCEPTION_ADD_SOURCE_HANDLE, 1, source)),
            MethodHandles.throwException(
                delegate.type().returnType(), InternalProvisionException.class));
    return MethodHandles.catchException(delegate, RuntimeException.class, addSourceAndRethrow);
  }

  /**
   * Surrounds the delegate with a try..finally.. that calls `finishConstruction` on the context.
   */
  static MethodHandle finishConstruction(MethodHandle delegate, int circularFactoryId) {
    // (Throwable, Object, InternalContext)->Object
    var finishConstruction =
        MethodHandles.insertArguments(FINISH_CONSTRUCTION_HANDLE, 3, circularFactoryId);

    return MethodHandles.tryFinally(delegate, finishConstruction).asType(delegate.type());
  }

  private static final MethodHandle FINISH_CONSTRUCTION_HANDLE =
      findStaticOrDie(
          InternalMethodHandles.class,
          "finallyFinishConstruction",
          methodType(
              Object.class, Throwable.class, Object.class, InternalContext.class, int.class));

  @Keep
  private static Object finallyFinishConstruction(
      Throwable ignored, Object result, InternalContext context, int circularFactoryId) {
    context.finishConstruction(circularFactoryId, result);
    return result;
  }

  /**
   * Adds a call to {@link InternalContext#tryStartConstruction} and an early return if it returns
   * non-null.
   *
   * <pre>{@code
   * T result = context.tryStartConstruction(circularFactoryId, dependency);
   * if (result != null) {
   *   return result;
   * }
   * return delegate(...);
   * }</pre>
   */
  static MethodHandle tryStartConstruction(
      InjectorOptions options,
      MethodHandle delegate,
      Dependency<?> dependency,
      int circularFactoryId) {

    // (InternalContext)->Object
    var tryStartConstruction =
        MethodHandles.insertArguments(
            TRY_START_CONSTRUCTION_HANDLE, 1, circularFactoryId, dependency);
    if (options.disableCircularProxies
        || !dependency.getKey().getTypeLiteral().getRawType().isInterface()) {
      // When proxies are disabled or the class is not an interface we don't need to check the
      // result of tryStartConstruction since it will always return null or throw.
      tryStartConstruction = dropReturn(tryStartConstruction);
      return MethodHandles.foldArguments(delegate, tryStartConstruction);
    }
    // This takes the first Object parameter and casts it to the return type of the delegate.
    // It also ignores all the other parameters.
    var returnProxy =
        MethodHandles.dropArguments(
            MethodHandles.identity(Object.class)
                .asType(methodType(delegate.type().returnType(), Object.class)),
            1,
            delegate.type().parameterList());

    // Otherwise we need to test the return value of tryStartConstruction since it might be a
    // proxy.
    var guard =
        MethodHandles.guardWithTest(
            IS_NULL_HANDLE,
            // Ignore the 'null' return from tryStartConstruction and call the delegate.
            MethodHandles.dropArguments(delegate, 0, Object.class),
            // Just return the result of tryStartConstruction
            returnProxy);
    // Call tryStartConstruction and then execute the guard.
    return MethodHandles.foldArguments(guard, tryStartConstruction).asType(delegate.type());
  }

  /**
   * Drops the return value from a method handle.
   *
   * <p>TODO(lukes): once guice is on jdk16+ we can use MEthodHandles.dropReturn directly.
   */
  static MethodHandle dropReturn(MethodHandle handle) {
    if (handle.type().returnType().equals(void.class)) {
      return handle;
    }
    return MethodHandles.filterReturnValue(
        handle, MethodHandles.empty(methodType(void.class, handle.type().returnType())));
  }

  private static final MethodHandle IS_NULL_HANDLE =
      findStaticOrDie(
          InternalMethodHandles.class, "isNull", methodType(boolean.class, Object.class));

  @Keep
  private static boolean isNull(Object result) {
    return result == null;
  }

  private static final MethodHandle TRY_START_CONSTRUCTION_HANDLE =
      findVirtualOrDie(
          InternalContext.class,
          "tryStartConstruction",
          methodType(Object.class, int.class, Dependency.class));

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
                        .getHandle(new LinkageContext(options), dependency, /* linked= */ false)
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

  // The `throws` clause tricks Java type inference into deciding that E must be some subtype of
  // RuntimeException but because the cast is unchecked it doesn't check.  So the compiler cannot
  // tell that this might be a checked exception.
  @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals", "CheckedExceptionNotThrown"})
  private static <E extends Throwable> E sneakyThrow(Throwable e) throws E {
    throw (E) e;
  }

  /**
   * Builds an ImmutableSet factory that delegates to the given element handles.
   *
   * <p>This generates code that looks like:
   *
   * <pre>{@code
   * ImmutableSet impl(InternalContext ctx) {
   *   return ImmutableSet.builderWithExpectedSize(elements.size())
   *       .add(<value>(ctx))
   *       ...
   *       .build();
   * }
   * }</pre>
   *
   * <p>Plus handling for some special cases.
   */
  static MethodHandle buildImmutableSetFactory(Iterable<MethodHandle> elementHandles) {
    var elementHandlesList =
        stream(elementHandles)
            // Cast return types to object and enforce that this is a factory type.
            .map(InternalMethodHandles::castReturnToObject)
            .collect(toImmutableList());
    if (elementHandlesList.isEmpty()) {
      // ImmutableSet.of()
      return constantFactoryGetHandle(ImmutableSet.class, ImmutableSet.of());
    }
    if (elementHandlesList.size() == 1) {
      // ImmutableSet.of(<element>(ctx))
      return MethodHandles.filterReturnValue(elementHandlesList.get(0), IMMUTABLE_SET_OF_HANDLE);
    }
    // ImmutableSet.builderWithExpectedSize(<size>)
    var builder =
        MethodHandles.insertArguments(
            IMMUTABLE_SET_BUILDER_OF_SIZE_HANDLE, 0, elementHandlesList.size());
    // Add any InternalContext arguments to the builder.
    builder = MethodHandles.dropArguments(builder, 0, InternalContext.class);
    for (var handle : elementHandlesList) {
      // builder = builder.add(<handle>(ctx))
      builder =
          MethodHandles.foldArguments(
              MethodHandles.filterArguments(IMMUTABLE_SET_BUILDER_ADD_HANDLE, 1, handle), builder);
    }
    return MethodHandles.filterReturnValue(builder, IMMUTABLE_SET_BUILDER_BUILD_HANDLE);
  }

  private static final MethodHandle IMMUTABLE_SET_OF_HANDLE =
      InternalMethodHandles.findStaticOrDie(
          ImmutableSet.class, "of", methodType(ImmutableSet.class, Object.class));
  private static final MethodHandle IMMUTABLE_SET_BUILDER_OF_SIZE_HANDLE =
      InternalMethodHandles.findStaticOrDie(
          ImmutableSet.class,
          "builderWithExpectedSize",
          methodType(ImmutableSet.Builder.class, int.class));
  private static final MethodHandle IMMUTABLE_SET_BUILDER_ADD_HANDLE =
      InternalMethodHandles.findVirtualOrDie(
          ImmutableSet.Builder.class, "add", methodType(ImmutableSet.Builder.class, Object.class));
  private static final MethodHandle IMMUTABLE_SET_BUILDER_BUILD_HANDLE =
      InternalMethodHandles.findVirtualOrDie(
          ImmutableSet.Builder.class, "build", methodType(ImmutableSet.class));

  /**
   * Builds an ImmutableMap factory that delegates to the given entries.
   *
   * <p>This generates code that looks like:
   *
   * <pre>{@code
   * ImmutableMap impl(InternalContext ctx) {
   *   return ImmutableMap.builderWithExpectedSize(entries.size())
   *       .put(<key>, <value>(ctx))
   *       ...
   *       .buildOrThrow();
   * }
   * }</pre>
   *
   * <p>Plus handling for some special cases.
   */
  static <T> MethodHandle buildImmutableMapFactory(List<Map.Entry<T, MethodHandle>> entries) {
    if (entries.isEmpty()) {
      return InternalMethodHandles.constantFactoryGetHandle(ImmutableMap.class, ImmutableMap.of());
    }
    // ImmutableMap.Builder.of(K, V) has a special case for a single entry.
    if (entries.size() == 1) {
      var entry = entries.get(0);
      return MethodHandles.filterReturnValue(
          castReturnToObject(entry.getValue()),
          MethodHandles.insertArguments(IMMUTABLE_MAP_OF_HANDLE, 0, entry.getKey()));
    }
    // Otherwise, we use the builder API by chaining a bunch of put() calls.
    // It might be slightly more efficient to bind to one of the ImmutableMap.of(...) overloads
    // since that would eliminate the need for the builder (and all the `put` calls).
    // The other option is to call `ImmutableMap.ofEntries(entries)` which also might be slightly
    // more efficient.  But these are probably pretty minor optimizations.
    var builder =
        MethodHandles.insertArguments(
            IMMUTABLE_MAP_BUILDER_WITH_EXPECTED_SIZE_HANDLE, 0, entries.size());
    builder = MethodHandles.dropArguments(builder, 0, InternalContext.class);
    for (Map.Entry<T, MethodHandle> entry : entries) {
      // `put` has the signature `put(Builder, K, V)->Builder` (the first parameter is 'this').
      // Insert the 'constant' key to get this signature:
      // (Builder, V)->Builder
      var put = MethodHandles.insertArguments(IMMUTABLE_MAP_BUILDER_PUT_HANDLE, 1, entry.getKey());
      // Construct the value, by calling the factory method handle to supply the first argument (the
      // value).  Because the entry is a MethodHandle with signature `(InternalContext)->V` we need
      // to cast the return type to `Object` to match the signature of `put`.
      // (Builder, InternalContext)->Builder
      put = MethodHandles.filterArguments(put, 1, castReturnToObject(entry.getValue()));
      // Fold works by invoking the 'builder' and then passing it to the first argument of the
      // `put` method, and then passing the arguments to `builder` to put as well. Like this:
      //  Builder fold(InternalContext ctx) {
      //   bar builder = <builder-handle>(ctx);
      //   return <put-handle>(builder, ctx);
      // }
      // (InternalContext)->Builder
      // Now, that has the same signture as builder, so assign back and loop.
      builder = MethodHandles.foldArguments(put, builder);
    }
    return MethodHandles.filterReturnValue(builder, IMMUTABLE_MAP_BUILDER_BUILD_OR_THROW_HANDLE);
  }

  private static final MethodHandle IMMUTABLE_MAP_OF_HANDLE =
      InternalMethodHandles.findStaticOrDie(
          ImmutableMap.class, "of", methodType(ImmutableMap.class, Object.class, Object.class));
  private static final MethodHandle IMMUTABLE_MAP_BUILDER_WITH_EXPECTED_SIZE_HANDLE =
      InternalMethodHandles.findStaticOrDie(
          ImmutableMap.class,
          "builderWithExpectedSize",
          methodType(ImmutableMap.Builder.class, int.class));
  private static final MethodHandle IMMUTABLE_MAP_BUILDER_PUT_HANDLE =
      InternalMethodHandles.findVirtualOrDie(
          ImmutableMap.Builder.class,
          "put",
          methodType(ImmutableMap.Builder.class, Object.class, Object.class));
  private static final MethodHandle IMMUTABLE_MAP_BUILDER_BUILD_OR_THROW_HANDLE =
      InternalMethodHandles.findVirtualOrDie(
          ImmutableMap.Builder.class, "buildOrThrow", methodType(ImmutableMap.class));

  private InternalMethodHandles() {}
}
