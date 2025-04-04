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
import static java.lang.invoke.MethodType.methodType;
import static java.util.Arrays.stream;
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

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ObjectArrays;
import com.google.errorprone.annotations.ForOverride;
import com.google.errorprone.annotations.Keep;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.internal.ProvisionListenerStackCallback.ProvisionCallback;
import com.google.inject.internal.aop.ClassDefining;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.InjectionPoint;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

/** Utility methods for working with method handles and our internal guice protocols. */
public final class InternalMethodHandles {
  private static final MethodHandles.Lookup lookup = MethodHandles.lookup();

  /**
   * The type of the method handle for {@link InternalFactory#getHandle}.
   *
   * <p>In theory we could specialize types for handles to be more specific, and this might even
   * allow us to do something cool like support {@code @Provides int provideInt() { return 1; }}
   * without boxing. However, this adds a fair bit of complexity as every single factory helper in
   * this file would need to support generic returns (e.g. the try-catch and finally adapters), this
   * is achievable but would be a fair bit of complexity for a small performance win. Instead we
   * model everything as returning Object and rely on various injection sinks to enforce
   * correctness, which is the same strategy that we use for the `InternalFactory.get` protocol.
   */
  static final MethodType FACTORY_TYPE = methodType(Object.class, InternalContext.class);

  /**
   * A handle for {@link BiFunction#apply}, useful for targeting fastclasses.
   *
   * <p>TODO(b/366058184): Have the fastclass code hand us a handle directly so we can avoid the
   * `BiFunction` abstraction.
   */
  static final MethodHandle BIFUNCTION_APPLY_HANDLE =
      findVirtualOrDie(
          BiFunction.class, "apply", methodType(Object.class, Object.class, Object.class));

  /**
   * A handle for {@link Method#invoke}, useful when we can neither use a fastclass or direct method
   * handle.
   */
  static final MethodHandle METHOD_INVOKE_HANDLE =
      findVirtualOrDie(
          Method.class, "invoke", methodType(Object.class, Object.class, Object[].class));

  /**
   * A handle for {@link Method#invoke}, useful when we can neither use a fastclass or direct method
   * handle.
   */
  static final MethodHandle CONSTRUCTOR_NEWINSTANCE_HANDLE =
      findVirtualOrDie(Constructor.class, "newInstance", methodType(Object.class, Object[].class));

  /**
   * The maximum arity of a method handle that can be bound.
   *
   * <ul>
   *   <li>There is a hard limit imposed by the JVM of 255 parameters.
   *   <li>MethodHandles add another parameter to represent the 'direct bound handle'
   *   <li>A MethodHandle that is invoked from Java by `invokeExact` adds another parameter to
   *       represent the receiver `MethodHandle` of `invokeExact`.
   * </ul>
   *
   * <p>This leaves us with 255 - 2 = 253 parameters as the maximum arity of a method handle that
   * can be bound.
   *
   * <p>TODO(b/366058184): For now we use this to decide when to back off and have callers
   * workaround generally by generating a fastclass. We should just enforce this limit on users
   * basically no one should really need to use so many parameters in a constructor/method and for
   * guice injectable methods the workarounds are generally quite simple anyway, so we can just make
   * this an error.
   */
  private static final int MAX_BINDABLE_ARITY = 253;

  /**
   * Returns a method handle for the given method, or null if the method is not accessible.
   *
   * <p>Ideally we would change guice APIs to accept MethodHandle.Lookup objects instead of trying
   * to access user methods with Guice's internal permissions. However, this is sufficient for now.
   */
  @Nullable
  static MethodHandle unreflect(Method method) {
    // account for the `this` param if any
    int paramSize = Modifier.isStatic(method.getModifiers()) ? 1 : 0;
    for (var param : method.getParameterTypes()) {
      paramSize += param.isPrimitive() ? Type.getType(param).getSize() : 1;
    }
    if (paramSize > MAX_BINDABLE_ARITY) {
      return null;
    }
    try {
      method.setAccessible(true);
    } catch (SecurityException | InaccessibleObjectException e) {
      return null;
    }
    try {
      return lookup.unreflect(method);
    } catch (IllegalAccessException e) {
      // setAccessible should have handled this.
      throw new LinkageError("inaccessible method: " + method, e);
    }
  }

  /**
   * Returns a method handle for the given constructor, or null if it is not accessible.
   *
   * <p>Ideally we would change Guice APIs to accept MethodHandle.Lookup objects instead of trying
   * to access user methods with Guice's internal permissions. However, this is sufficient for now.
   */
  @Nullable
  static MethodHandle unreflectConstructor(Constructor<?> ctor) {
    int paramSize = 1; // constructors always have a `this` param
    for (var param : ctor.getParameterTypes()) {
      paramSize += param.isPrimitive() ? Type.getType(param).getSize() : 1;
    }
    if (paramSize > MAX_BINDABLE_ARITY) {
      return null;
    }
    try {
      ctor.setAccessible(true);
    } catch (SecurityException | InaccessibleObjectException e) {
      return null;
    }
    try {
      return lookup.unreflectConstructor(ctor);
    } catch (IllegalAccessException e) {
      // setAccessible should have handled this.
      throw new LinkageError("inaccessible constructor: " + ctor, e);
    }
  }

  /**
   * Returns a method handle for setting the given field, or null if the field is not accessible.
   */
  @Nullable
  static MethodHandle unreflectSetter(Field field) {
    try {
      field.setAccessible(true);
    } catch (SecurityException | InaccessibleObjectException e) {
      return null;
    }
    try {
      return lookup.unreflectSetter(field);
    } catch (IllegalAccessException e) {
      // setAccessible should have handled this.
      throw new LinkageError("inaccessible field: " + field, e);
    }
  }

  static MethodHandle castReturnToObject(MethodHandle handle) {
    return castReturnTo(handle, Object.class);
  }

  static MethodHandle castReturnTo(MethodHandle handle, Class<?> returnType) {
    return handle.asType(handle.type().changeReturnType(returnType));
  }

  private static final MethodHandle PROVIDER_GET_HANDLE =
      findVirtualOrDie(jakarta.inject.Provider.class, "get", methodType(Object.class));

  /**
   * Returns a method handle that calls {@code <providerHandle>.get()}.
   *
   * <p>The returned handle has the same parameters as the delegate and returns {@link Object}.
   */
  static MethodHandle getProvider(MethodHandle providerHandle) {
    return MethodHandles.filterReturnValue(
        // need to cast to jakarta.inject.Provider to exactly match the signature of Provider.get
        castReturnTo(providerHandle, jakarta.inject.Provider.class), PROVIDER_GET_HANDLE);
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
   * Returns a method handle with the signature {@code (InternalContext) -> Object} that returns the
   * given instance.
   */
  static MethodHandle constantFactoryGetHandle(Object instance) {
    return MethodHandles.dropArguments(
        MethodHandles.constant(Object.class, instance), 0, InternalContext.class);
  }

  /**
   * Returns a method handle with the signature {@code (InternalContext) -> T} that returns the
   * result of the given initializable.
   */
  static MethodHandle initializableFactoryGetHandle(Initializable<?> initializable) {
    return new InitializableCallSite(initializable).dynamicInvoker();
  }

  /**
   * Returns a method handle with the signature {@code (InternalContext) -> T} that returns the
   * result of the given initializable.
   */
  static MethodHandle initializableFactoryGetHandle(
      Initializable<?> initializable, Class<?> forType) {
    return new InitializableCallSite(initializable).dynamicInvoker();
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

    InitializableCallSite(Initializable<?> initializable) {
      super(FACTORY_TYPE);
      // Have the first call go to `bootstrapCall` which will set the target to the constant
      // factory get handle if the initializable succeeds.
      setTarget(MethodHandles.insertArguments(BOOTSTRAP_CALL_MH.bindTo(this), 0, initializable));
    }

    @Keep
    Object bootstrapCall(Initializable<?> initializable, InternalContext context)
        throws InternalProvisionException {
      // If this throws, we will call back through here on the next call which is the typical
      // behavior of initializables.
      // We also do not synchronize and instead rely on the underlying initializable to enforce
      // single-initialization semantics, but we publish via a volatile call site.
      Object value = initializable.get(context);
      setTarget(constantFactoryGetHandle(value));
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
    if (dependency.isNullable()) {
      return delegate;
    }
    // (Object) -> Object
    var nullCheckResult =
        MethodHandles.insertArguments(NULL_CHECK_RESULT_MH, 1, source, dependency);
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
          var finalDelegate = delegate.asType(FACTORY_TYPE);
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
            delegate, InternalProvisionException.class, addSourceAndRethrow)
        .asType(delegate.type());
  }

  /**
   * Surrounds the delegate with a catch block that rethrows the exception with the given source.
   *
   * <pre>{@code
   * try {
   *   return delegate(...);
   * } catch (InternalProvisionException ipe) {
   *   throw ipe.addSource(source);
   * } catch (Throwable re) {
   *   throw InternalProvisionException.errorInProvider(re).addSource(source);
   * }
   * }</pre>
   */
  static MethodHandle catchErrorInProviderAndRethrowWithSource(
      MethodHandle delegate, Object source) {
    var rethrow =
        MethodHandles.insertArguments(
            CATCH_ERROR_IN_PROVIDER_AND_RETHROW_WITH_SOURCE_HANDLE, 1, source);

    return MethodHandles.catchException(castReturnToObject(delegate), Throwable.class, rethrow)
        .asType(delegate.type());
  }

  private static final MethodHandle CATCH_ERROR_IN_PROVIDER_AND_RETHROW_WITH_SOURCE_HANDLE =
      findStaticOrDie(
          InternalMethodHandles.class,
          "doCatchErrorInProviderAndRethrowWithSource",
          methodType(Object.class, Throwable.class, Object.class));

  @Keep
  private static Object doCatchErrorInProviderAndRethrowWithSource(Throwable re, Object source)
      throws InternalProvisionException {
    // MethodHandles don't support 'parallel catch clauses' so we need to do this manually with a
    // single catch block that does both.
    if (re instanceof InternalProvisionException) {
      throw ((InternalProvisionException) re).addSource(source);
    }
    throw InternalProvisionException.errorInProvider(re).addSource(source);
  }

  /**
   * Surrounds the delegate with a catch block that rethrows the exception with the given source.
   *
   * <pre>{@code
   * try {
   *   delegate(...);
   * } catch (Throwable re) {
   *   throw InternalProvisionException.errorInProvider(re).addSource(source);
   * }
   * }</pre>
   */
  static MethodHandle catchErrorInMethodAndRethrowWithSource(MethodHandle delegate, Object source) {
    checkArgument(
        delegate.type().returnType().equals(void.class),
        "MethodHandle must return void: %s",
        delegate.type());
    var rethrow =
        MethodHandles.insertArguments(
            CATCH_ERROR_IN_METHOD_AND_RETHROW_WITH_SOURCE_HANDLE, 1, source);

    return MethodHandles.catchException(delegate, Throwable.class, rethrow);
  }

  private static final MethodHandle CATCH_ERROR_IN_METHOD_AND_RETHROW_WITH_SOURCE_HANDLE =
      findStaticOrDie(
          InternalMethodHandles.class,
          "doCatchErrorInMethodAndRethrowWithSource",
          methodType(void.class, Throwable.class, Object.class));

  @Keep
  private static void doCatchErrorInMethodAndRethrowWithSource(Throwable re, Object source)
      throws InternalProvisionException {
    if (re instanceof InternalProvisionException) {
      // This error is from a dependency, just let it propagate
      throw ((InternalProvisionException) re);
    }
    throw InternalProvisionException.errorInjectingMethod(re).addSource(source);
  }

  /**
   * Surrounds the delegate with a catch block that rethrows the exception with the given source.
   *
   * <pre>{@code
   * try {
   *   return delegate(...);
   * } catch (Throwable re) {
   *   throw InternalProvisionException.errorInProvider(re).addSource(source);
   * }
   * }</pre>
   */
  static MethodHandle catchErrorInConstructorAndRethrowWithSource(
      MethodHandle delegate, InjectionPoint source) {
    var rethrow =
        MethodHandles.insertArguments(
            CATCH_ERROR_IN_CONSTRUCTOR_AND_RETHROW_WITH_SOURCE_HANDLE, 1, source);

    return MethodHandles.catchException(delegate, Throwable.class, rethrow);
  }

  private static final MethodHandle CATCH_ERROR_IN_CONSTRUCTOR_AND_RETHROW_WITH_SOURCE_HANDLE =
      findStaticOrDie(
          InternalMethodHandles.class,
          "doCatchErrorInConstructorAndRethrowWithSource",
          methodType(Object.class, Throwable.class, InjectionPoint.class));

  @Keep
  private static Object doCatchErrorInConstructorAndRethrowWithSource(
      Throwable re, InjectionPoint source) throws InternalProvisionException {
    if (re instanceof InternalProvisionException) {
      // This error is from a dependency, just let it propagate
      throw ((InternalProvisionException) re);
    }
    throw InternalProvisionException.errorInjectingConstructor(re).addSource(source);
  }

  /**
   * Surrounds the delegate with a try..finally.. that calls `finishConstruction` on the context.
   */
  static MethodHandle finishConstruction(MethodHandle delegate, int circularFactoryId) {
    // (Throwable, Object, InternalContext)->Object
    var finishConstruction =
        MethodHandles.insertArguments(FINISH_CONSTRUCTION_HANDLE, 3, circularFactoryId);

    return MethodHandles.tryFinally(delegate, finishConstruction);
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
   * Surrounds the delegate with a try..finally.. that calls `finishConstructionAndSetReference` on
   * the context.
   */
  static MethodHandle finishConstructionAndSetReference(
      MethodHandle delegate, int circularFactoryId) {
    var finishConstruction =
        MethodHandles.insertArguments(
            FINISH_CONSTRUCTION_AND_SET_REFERENCE_HANDLE, 3, circularFactoryId);

    return MethodHandles.tryFinally(delegate, finishConstruction);
  }

  private static final MethodHandle FINISH_CONSTRUCTION_AND_SET_REFERENCE_HANDLE =
      findStaticOrDie(
          InternalMethodHandles.class,
          "finallyFinishConstructionAndSetReference",
          methodType(
              Object.class, Throwable.class, Object.class, InternalContext.class, int.class));

  @Keep
  private static Object finallyFinishConstructionAndSetReference(
      Throwable ignored, Object result, InternalContext context, int circularFactoryId) {
    context.finishConstructionAndSetReference(circularFactoryId, result);
    return result;
  }

  /**
   * Surrounds the delegate with a try..finally.. that calls {@link
   * InternalContext#clearCurrentReference}.
   */
  static MethodHandle clearReference(MethodHandle delegate, int circularFactoryId) {
    var clearReference =
        MethodHandles.insertArguments(CLEAR_REFERENCE_HANDLE, 3, circularFactoryId);

    return MethodHandles.tryFinally(delegate, clearReference);
  }

  private static final MethodHandle CLEAR_REFERENCE_HANDLE =
      findStaticOrDie(
          InternalMethodHandles.class,
          "finallyClearReference",
          methodType(
              Object.class, Throwable.class, Object.class, InternalContext.class, int.class));

  @Keep
  private static Object finallyClearReference(
      Throwable ignored, Object result, InternalContext context, int circularFactoryId) {
    context.clearCurrentReference(circularFactoryId);
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
      MethodHandle delegate, Dependency<?> dependency, int circularFactoryId) {
    // NOTE: we cannot optimize this further by assuming that the return value is always null when
    // circular proxies are disabled, because if parent and child injectors differ in that setting
    // then injectors with circularProxiesDisabled may still run in a context where they are enabled
    // and visa versa.

    // (InternalContext)->Object
    var tryStartConstruction =
        MethodHandles.insertArguments(
            TRY_START_CONSTRUCTION_HANDLE, 1, circularFactoryId, dependency);
    // This takes the first Object parameter and casts it to the return type of the delegate.
    // It also ignores all the other parameters.
    var returnProxy =
        MethodHandles.dropArguments(
            MethodHandles.identity(Object.class), 1, delegate.type().parameterList());

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
    return MethodHandles.foldArguments(guard, tryStartConstruction);
  }

  private static final MethodHandle TRY_START_CONSTRUCTION_HANDLE =
      findVirtualOrDie(
          InternalContext.class,
          "tryStartConstruction",
          methodType(Object.class, int.class, Dependency.class));

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

  static MethodHandle catchInvocationTargetExceptionAndRethrowCause(MethodHandle handle) {
    return MethodHandles.catchException(
        handle,
        InvocationTargetException.class,
        CATCH_INVOCATION_TARGET_EXCEPTION_AND_RETHROW_CAUSE_HANDLE);
  }

  private static final MethodHandle CATCH_INVOCATION_TARGET_EXCEPTION_AND_RETHROW_CAUSE_HANDLE =
      findStaticOrDie(
          InternalMethodHandles.class,
          "doCatchInvocationTargetExceptionAndRethrowCause",
          methodType(Object.class, InvocationTargetException.class));

  @Keep
  private static Object doCatchInvocationTargetExceptionAndRethrowCause(InvocationTargetException e)
      throws Throwable {
    throw e.getCause();
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
    @SuppressWarnings("unchecked")
    Provider<T> typedProvider =
        (Provider)
            ProviderMaker.defineClass(
                injector,
                factory,
                dependency,
                () ->
                    new ConstantCallSite(
                        factory
                            .getHandle(new LinkageContext(), dependency, /* linked= */ false)
                            .asType(FACTORY_TYPE)),
                /* isScoped= */ false);
    return typedProvider;
  }

  /**
   * Returns a {@link ProviderToInternalFactoryAdapter} subtype to support scoping.
   *
   * <p>The unique thing about scoping is that we need to support many different {@link Dependency}
   * instances.
   */
  static <T> ProviderToInternalFactoryAdapter<T> makeScopedProvider(
      InternalFactory<? extends T> factory, InjectorImpl injector, Key<T> forKey) {
    var dependency = Dependency.get(forKey);
    // This is safe due to the implementation of InternalFactory.getHandle which we cannot enforce
    // with generic type constraints.
    @SuppressWarnings("unchecked")
    ProviderToInternalFactoryAdapter<T> typedProvider =
        (ProviderToInternalFactoryAdapter)
            ProviderMaker.defineClass(
                injector,
                factory,
                dependency,
                () -> new ScopedDelegateCallSite(factory, dependency),
                /* isScoped= */ true);
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
    private final InternalFactory<? extends T> factory;
    private final Dependency<?> dependency;

    protected GeneratedProvider(
        InjectorImpl injector, InternalFactory<T> factory, Dependency<?> dependency) {
      this.injector = injector;
      this.factory = factory;
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

    @Override
    public String toString() {
      return factory.toString();
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
    private static final Type SCOPED_PROVIDER_TYPE =
        Type.getType(ProviderToInternalFactoryAdapter.class);
    private static final MethodType CTOR_TYPE =
        methodType(void.class, InjectorImpl.class, InternalFactory.class, Dependency.class);

    private static final MethodType SCOPED_CTOR_TYPE =
        methodType(void.class, InjectorImpl.class, InternalFactory.class);

    private static final Handle BOOSTRAP_HANDLE =
        new Handle(
            H_INVOKESTATIC,
            Type.getType(InternalMethodHandles.class).getInternalName(),
            "bootstrapHandle",
            methodType(CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class)
                .toMethodDescriptorString(),
            /* isInterface= */ false);

    private static final ConcurrentHashMap<String, Integer> nameUses = new ConcurrentHashMap<>();

    // These are character that are not allowed as part of an unqualified class name part.
    private static final CharMatcher NAME_MANGLE_CHARS = CharMatcher.anyOf("[].;/<>");

    /**
     * Defines and constructs a stateless provider class that delegates to the given handleCreator.
     *
     * <p>The handleCreator is called at most once per generated provider instance.
     */
    static Provider<?> defineClass(
        InjectorImpl injector,
        InternalFactory<?> factory,
        Dependency<?> dependency,
        Supplier<CallSite> handleCreator,
        boolean isScoped) {
      // Even if we are using the anonymous classloading mechanisms we still need to pick a name,
      // it just ends up being ignored.
      var baseType = isScoped ? SCOPED_PROVIDER_TYPE : PROVIDER_TYPE;
      var name = dependency.getKey().getTypeLiteral().getRawType().getSimpleName();
      // We need to mangle this name to avoid `[]` characters, for now we just remove them.
      name = NAME_MANGLE_CHARS.removeFrom(name);
      String actualName =
          baseType.getInternalName()
              + "$"
              + name
              + "$"
              + nameUses.compute(name, (key, value) -> value == null ? 0 : value + 1);

      ClassWriter cw = new ClassWriter(0);
      cw.visit(
          V11, // need access to invokeDynamic and constant dynamic
          ACC_PUBLIC | ACC_SUPER | ACC_FINAL,
          actualName,
          /* signature= */ null,
          baseType.getInternalName(),
          /* interfaces= */ null);
      // Allocate a static field to store the handle creator or the resolved callsite.
      cw.visitField(
          ACC_PUBLIC | ACC_STATIC,
          "definer",
          "Ljava/lang/Object;",
          /* signature= */ null,
          /* value= */ null);
      var ctorType = isScoped ? SCOPED_CTOR_TYPE : CTOR_TYPE;
      {
        // generate a default constructor
        // This is just a default constructor that calls the super class constructor.
        // basically `super(injector, factory, dependency?);`
        int stackHeight = 0;
        MethodVisitor mv =
            cw.visitMethod(
                ACC_PUBLIC,
                "<init>",
                ctorType.toMethodDescriptorString(),
                /* signature= */ null,
                /* exceptions= */ null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, stackHeight++); // this
        mv.visitVarInsn(ALOAD, stackHeight++); // injector
        mv.visitVarInsn(ALOAD, stackHeight++); // factory
        if (!isScoped) {
          mv.visitVarInsn(ALOAD, stackHeight++); // maybe dependency
        }
        mv.visitMethodInsn(
            INVOKESPECIAL,
            baseType.getInternalName(),
            "<init>",
            ctorType.toMethodDescriptorString(),
            /* isInterface= */ false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(
            /* maxStack= */ stackHeight, // Just pushing the parameters
            /* maxLocals= */ stackHeight // all the parameters
            );
        mv.visitEnd();
      }
      {
        String descriptor = FACTORY_TYPE.toMethodDescriptorString();
        // generate a doGet() method
        MethodVisitor mv =
            cw.visitMethod(
                ACC_PROTECTED | ACC_FINAL,
                "doGet",
                descriptor,
                /* signature= */ null,
                /* exceptions= */ null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 1); // ctx
        mv.visitInvokeDynamicInsn("get", descriptor, BOOSTRAP_HANDLE);
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
                /* lifetimeOwner= */ injector,
                isScoped ? ProviderToInternalFactoryAdapter.class : GeneratedProvider.class,
                cw.toByteArray());
      } catch (Exception e) {
        throw new LinkageError("failed to define class", e);
      }
      try {
        clazz.getField("definer").set(null, handleCreator);
      } catch (ReflectiveOperationException e) {
        throw new LinkageError("missing or inaccessible field: definer", e);
      }
      try {
        MethodHandle ctor = lookup.findConstructor(clazz, ctorType);
        return (Provider<?>)
            (isScoped
                ? ctor.invoke(injector, factory)
                : ctor.invoke(injector, factory, dependency));
      } catch (Throwable e) {
        throw new LinkageError("missing or inaccessible constructor", e);
      }
    }

    private ProviderMaker() {}
  }

  /**
   * A callsite to implement the logic for the implementation of InternalFactories that are being
   * scoped when circular proxies are enabled.
   *
   * <p>To support circular proxies the {@link InternalFactoryToScopedProviderAdapter} will call
   * {@link InternalContext#setDependency} to propagate dependency information across the scope
   * implementation. This allows proxies to be constructed for super-interfaces that are needed at
   * the injection point rather than the concrete type of the binding.
   *
   * <p>This uses a self-patching callsite that will re-patch whenever we see a new dependency. When
   * a new dependency is seen the callsite will call through to the {@link #fallback} method which
   * will compute the new handle and update the callsite with it. This is threadsafe because handle
   * allocation is done under a lock and we are essentially accumulating new handles so prior ones
   * remain available. So even if multiple threads are racing to update the callsite, they will all
   * end up with the same set of handles.
   */
  private static final class ScopedDelegateCallSite extends MutableCallSite {
    private static final MethodHandle FALLBACK_GET_HANDLE =
        findVirtualOrDie(
            ScopedDelegateCallSite.class,
            "fallback",
            methodType(Object.class, InternalContext.class));
    private static final MethodHandle DEPENDENCY_EQUALS_HANDLE =
        findStaticOrDie(
            ScopedDelegateCallSite.class,
            "dependencyEquals",
            methodType(boolean.class, InternalContext.class, Dependency.class, Dependency.class));
    private static final MethodHandle INVOKE_HANDLE_HANDLE =
        findStaticOrDie(
            ScopedDelegateCallSite.class,
            "invokeHandle",
            methodType(
                Object.class,
                ImmutableMap.class,
                MethodHandle[].class,
                Dependency.class,
                MethodHandle.class,
                InternalContext.class));

    private static final MethodHandle SWITCH_KEY_HANDLE =
        findStaticOrDie(
            ScopedDelegateCallSite.class,
            "switchKey",
            methodType(int.class, ImmutableMap.class, Dependency.class, InternalContext.class));

    /** {@link MethodHandles#tableSwitch} if it is available. */
    @Nullable private static final MethodHandle TABLE_SWITCH_HANDLE;

    static {
      MethodHandle handle;
      try {
        handle =
            lookup.findStatic(
                MethodHandles.class,
                "tableSwitch",
                methodType(MethodHandle.class, MethodHandle.class, MethodHandle[].class));
      } catch (Throwable t) {
        handle = null;
      }
      TABLE_SWITCH_HANDLE = handle;
    }

    private final InternalFactory<?> factory;
    private final MethodHandle fallback;

    /**
     * A dependency object for factory being scoped. So for:
     * `bind(X.class).to(Y.class).in(Scopes.SINGLETON)`, we would allocate one of these for the `Y`
     * provider and the this dependency object would be {@code Dependency.get(Key.get(Y.class))}.
     *
     * <p>This is needed because {@link InternalFactory#getHandle} requires a non-null dependency
     * object (unlike {@link InternalFactory#get} which only sometimes requires it), so if scoped
     * providers interacted with outside an injector context this will be the dependency that is
     * used.
     */
    private final Dependency<?> bindingDependency;

    // We update this map under a lock in a 'copy on write' fashion.
    @GuardedBy("this")
    private ImmutableMap<Dependency<?>, Integer> handleIndices = ImmutableMap.of();

    @GuardedBy("this")
    private MethodHandle[] handles = new MethodHandle[0];

    ScopedDelegateCallSite(InternalFactory<?> factory, Dependency<?> bindingDependency) {
      super(FACTORY_TYPE);
      this.factory = factory;
      this.bindingDependency = bindingDependency;
      this.fallback = FALLBACK_GET_HANDLE.bindTo(this);
      setTarget(fallback);
    }

    @Keep
    Object fallback(InternalContext context) throws Throwable {
      MethodHandle handle;
      synchronized (this) {
        Dependency<?> dependency = getDependency(context, bindingDependency);
        int handleIndex = handleIndices.getOrDefault(dependency, -1);
        if (handleIndex == -1) {
          // Always pretend that we are a linked binding, to support
          // scoping implicit bindings.  If we are not actually a linked
          // binding, we'll fail properly elsewhere in the chain.
          handle = factory.getHandle(new LinkageContext(), dependency, /* linked= */ true);
          handleIndices =
              ImmutableMap.<Dependency<?>, Integer>builder()
                  .putAll(handleIndices)
                  .put(dependency, handleIndices.size())
                  .buildOrThrow();
          handles = ObjectArrays.concat(handles, handle);
          // Now rebuild the target
          // Special case for a single handle, we can just use a guard.  This case is very common
          // for bindings that are scoped and bound to a single interface.
          // Generate `dependencyEquals(ctx, dependency) ? handle(ctx) : fallback(ctx)`
          if (handles.length == 1) {
            // This is a handle of type (InternalContext) -> boolean
            var isEqual =
                MethodHandles.insertArguments(
                    DEPENDENCY_EQUALS_HANDLE, 1, bindingDependency, dependency);
            setTarget(MethodHandles.guardWithTest(isEqual, handle, fallback));
          } else {
            // If we have multiple handles we need to pick the right one based on the dependency.
            // in jdk17+ we can use a switch statement, but in jdk11 we need to use a simpler
            // approach of a dispatch through a map and an Array.
            if (TABLE_SWITCH_HANDLE != null) {
              // Generate a switch. Switches always take an `int` as the first argument (the switch
              // expression).  We don't actually use it in the cases, so we drop it.
              // The function will look like
              // Object switch(InternalContext ctx) {
              //   int index = getSwitchIndex(handles, defaultDependency, ctx);
              //   switch (index) {
              //     case 0:
              //       return handle0(ctx)
              //     ...
              //     case 1:
              //       return handles1(ctx);
              //     ...
              //     default:
              //       return callsite.fallback(ctx);
              //   }
              // }
              // NOTE: This pattern is modeled after how the JVM generates switch statements for
              // enums and simple class patterns.
              var switchHandle =
                  (MethodHandle)
                      TABLE_SWITCH_HANDLE.invokeExact(
                          /* fallback= */ MethodHandles.dropArguments(fallback, 0, int.class),
                          /* targets= */ stream(handles)
                              .map(h -> MethodHandles.dropArguments(h, 0, int.class))
                              .toArray(MethodHandle[]::new));
              var getIndex =
                  MethodHandles.insertArguments(
                      SWITCH_KEY_HANDLE, 0, handleIndices, bindingDependency);
              // This will return a handle of type (InternalContext) -> MethodHandle
              var executeSwitch = MethodHandles.foldArguments(switchHandle, getIndex);
              setTarget(executeSwitch);
            } else {
              // If we cannot use a switch statement, we need to use a dispatch through the array.
              // This is the jdk11 path. Generates:
              // `invokeHandle(handles, handleIndices, bindingDependency, fallback, ctx)`
              //
              // The main alternative is to generate a set of guardWithTest handles that 'binary
              // search' for the matching handle.  This would be more efficient than the
              // exactInvoker, but would be more complicated to generate. The table switch path
              // above should trigger most of the time so optimizing this path probably isn't that
              // important.
              var getHandle =
                  MethodHandles.insertArguments(
                      INVOKE_HANDLE_HANDLE, 0, handleIndices, handles, bindingDependency, fallback);
              setTarget(getHandle);
            }
          }
          // Ensure all callers see the update.
          MutableCallSite.syncAll(new MutableCallSite[] {this});
        } else {
          handle = handles[handleIndex];
        }
      }
      // For the current invocation just invoke the delegate handle directly.
      return handle.invokeExact(context);
    }

    /**
     * A helper to get the dependency from the context or the default dependency if that isn't
     * available.
     */
    private static Dependency<?> getDependency(
        InternalContext context, Dependency<?> bindingDependency) {
      var dep = context.getDependency();
      if (dep == null) {
        // We need to handle null in case the provider is being called directly but outside of its
        // original call-stack, so the InternalContext may not have a dependency set.
        dep = bindingDependency;
      }
      return dep;
    }

    /** Used for the guard in the case that we have constructed a single delegate handle. */
    @Keep
    static boolean dependencyEquals(
        InternalContext context, Dependency<?> bindingDependency, Dependency<?> dependency) {
      return dependency.equals(getDependency(context, bindingDependency));
    }

    /**
     * Used for the guard in the case that we have many handles and no `tableSwitch` support.
     *
     * <p>The caller should use a `MethodHandle.exactInvoker` to invoke the handle.
     */
    @Keep
    static Object invokeHandle(
        ImmutableMap<Dependency<?>, Integer> handleIndices,
        MethodHandle[] handles,
        Dependency<?> bindingDependency,
        MethodHandle fallback,
        InternalContext context)
        throws Throwable {
      int switchKey = switchKey(handleIndices, bindingDependency, context);
      return switchKey == -1
          ? fallback.invokeExact(context)
          : handles[switchKey].invokeExact(context);
    }

    /**
     * Used for the switch statement in the case that we have many handles and `tableSwitch`
     * support.
     */
    @Keep
    static int switchKey(
        ImmutableMap<Dependency<?>, Integer> handleIndices,
        Dependency<?> bindingDependency,
        InternalContext context) {
      var index = handleIndices.get(getDependency(context, bindingDependency));
      return index == null ? -1 : index;
    }
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
          CallSite callSite = (CallSite) ((Supplier) handleCreator).get();
          varHandle.setVolatile((Object) callSite);
          return callSite;
        }
      }
    }
    return (CallSite) handleCreator;
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
    var elementHandlesList = ImmutableList.copyOf(elementHandles);
    for (var handle : elementHandlesList) {
      checkArgument(
          handle.type().equals(FACTORY_TYPE),
          "Element handle must be a factory type %s got %s",
          handle.type(),
          FACTORY_TYPE);
    }
    if (elementHandlesList.isEmpty()) {
      // ImmutableSet.of()
      return constantFactoryGetHandle(ImmutableSet.of());
    }
    if (elementHandlesList.size() == 1) {
      // ImmutableSet.of(<element>(ctx))
      return MethodHandles.filterReturnValue(elementHandlesList.get(0), IMMUTABLE_SET_OF_HANDLE)
          .asType(FACTORY_TYPE);
    }
    // ImmutableSet.builderWithExpectedSize(<size>)
    var builder =
        MethodHandles.insertArguments(
            IMMUTABLE_SET_BUILDER_OF_SIZE_HANDLE, 0, elementHandlesList.size());

    builder = MethodHandles.foldArguments(doAddToImmutableSet(elementHandlesList), builder);
    return MethodHandles.filterReturnValue(builder, IMMUTABLE_SET_BUILDER_BUILD_HANDLE)
        .asType(FACTORY_TYPE);
  }

  /**
   * A helper to generate calls to add to the ImmutableSet builder.
   *
   * <p>Returns a handle of type (ImmutableSet.Builder, InternalContext) -> ImmutableSet.Builder
   */
  private static MethodHandle doAddToImmutableSet(ImmutableList<MethodHandle> elementHandlesList) {
    // We don't want to 'fold' too deep as it can lead to a stack overflow.  So we have a special
    // case for small lists.
    if (elementHandlesList.size() < 32) {
      var builder =
          MethodHandles.dropArguments(
              MethodHandles.identity(ImmutableSet.Builder.class), 1, InternalContext.class);
      for (var handle : elementHandlesList) {
        // builder = builder.add(<handle>(ctx))
        builder =
            MethodHandles.foldArguments(
                MethodHandles.filterArguments(IMMUTABLE_SET_BUILDER_ADD_HANDLE, 1, handle),
                dropReturn(builder));
      }
      return builder;
    } else {
      // Otherwise we split and recurse, this basically ensures that none of the lambda forms are
      // too big and the 'folds' are not too deep.
      int half = elementHandlesList.size() / 2;
      var left = elementHandlesList.subList(0, half);
      var right = elementHandlesList.subList(half, elementHandlesList.size());
      // We are basically creating 2 methods in a chain that add to the builder
      // We do this by calling `doAddToImmutableSet` recursively.
      return MethodHandles.foldArguments(
          doAddToImmutableSet(right), dropReturn(doAddToImmutableSet(left)));
    }
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
      return InternalMethodHandles.constantFactoryGetHandle(ImmutableMap.of());
    }
    // ImmutableMap.Builder.of(K, V) has a special case for a single entry.
    if (entries.size() == 1) {
      var entry = entries.get(0);
      return MethodHandles.filterReturnValue(
              entry.getValue(),
              MethodHandles.insertArguments(IMMUTABLE_MAP_OF_HANDLE, 0, entry.getKey()))
          .asType(FACTORY_TYPE);
    }
    // Otherwise, we use the builder API by chaining a bunch of put() calls.
    // It might be slightly more efficient to bind to one of the ImmutableMap.of(...) overloads
    // since that would eliminate the need for the builder (and all the `put` calls).
    // The other option is to call `ImmutableMap.ofEntries(entries)` which also might be slightly
    // more efficient.  But these are probably pretty minor optimizations.
    var builder =
        MethodHandles.insertArguments(
            IMMUTABLE_MAP_BUILDER_WITH_EXPECTED_SIZE_HANDLE, 0, entries.size());
    builder = MethodHandles.foldArguments(doPutEntries(entries), builder);
    return MethodHandles.filterReturnValue(builder, IMMUTABLE_MAP_BUILDER_BUILD_OR_THROW_HANDLE)
        .asType(FACTORY_TYPE);
  }

  /** Returns a handle of type (ImmutableMap.Builder, InternalContext) -> ImmutableMap.Builder */
  private static <T> MethodHandle doPutEntries(List<Map.Entry<T, MethodHandle>> entries) {
    int size = entries.size();
    checkArgument(size > 0, "entries must not be empty");
    if (size < 32) {
      MethodHandle builder = null;
      for (Map.Entry<T, MethodHandle> entry : entries) {
        // `put` has the signature `put(Builder, K, V)->Builder` (the first parameter is 'this').
        // Insert the 'constant' key to get this signature:
        // (Builder, V)->Builder
        var put =
            MethodHandles.insertArguments(IMMUTABLE_MAP_BUILDER_PUT_HANDLE, 1, entry.getKey());
        // Construct the value, by calling the factory method handle to supply the first argument
        // (the
        // value).  Because the entry is a MethodHandle with signature `(InternalContext)->V` we
        // need
        // to cast the return type to `Object` to match the signature of `put`.
        // (Builder, InternalContext)->Builder
        put = MethodHandles.filterArguments(put, 1, entry.getValue());
        // Fold works by invoking the 'builder' and then passing it to the first argument of the
        // `put` method, and then passing the arguments to `builder` to put as well. Like this:
        //  Builder fold(InternalContext ctx) {
        //   bar builder = <builder-handle>(ctx);
        //   return <put-handle>(builder, ctx);
        // }
        // (InternalContext)->Builder
        // Now, that has the same signture as builder, so assign back and loop.
        builder = builder == null ? put : MethodHandles.foldArguments(put, dropReturn(builder));
      }
      return builder;
    } else {
      // Otherwise we split and recurse, this basically ensures that none of the lambda forms are
      // too big and the 'folds' are not too deep.
      int half = size / 2;
      return MethodHandles.foldArguments(
          doPutEntries(entries.subList(half, size)),
          dropReturn(doPutEntries(entries.subList(0, half))));
    }
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
