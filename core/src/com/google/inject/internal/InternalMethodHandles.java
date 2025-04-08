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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Keep;
import com.google.inject.Provider;
import com.google.inject.internal.ProvisionListenerStackCallback.ProvisionCallback;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.InjectionPoint;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

/** Utility methods for working with method handles and our internal guice protocols. */
public final class InternalMethodHandles {
  private static final MethodHandles.Lookup lookup = MethodHandles.lookup();

  /**
   * The type of the method handle for {@link InternalFactory#getHandle}.
   *
   * <p>It accepts an {@link InternalContext} and a {@link Dependency} and returns an {@link
   * Object}.
   *
   * <p>In theory we could specialize return types for handles to be more specific, and this might
   * even allow us to do something cool like support {@code @Provides int provideInt() { return 1;
   * }} without boxing. However, this adds a fair bit of complexity as every single factory helper
   * in this file would need to support generic returns (e.g. the try-catch and finally adapters),
   * this is achievable but would be a fair bit of complexity for a small performance win. Instead
   * we model everything as returning Object and rely on various injection sinks to enforce
   * correctness, which is the same strategy that we use for the `InternalFactory.get` protocol.
   */
  static final MethodType FACTORY_TYPE =
      methodType(Object.class, InternalContext.class, Dependency.class);

  /** The type of a factory that has encapsulated the Dependency. */
  static final MethodType ELEMENT_FACTORY_TYPE = methodType(Object.class, InternalContext.class);

  static void checkHasFactoryType(MethodHandle handle) {
    checkArgument(
        handle.type().equals(FACTORY_TYPE), "Expected %s to have type %s", handle, FACTORY_TYPE);
  }

  static void checkHasElementFactoryType(MethodHandle handle) {
    checkArgument(
        handle.type().equals(ELEMENT_FACTORY_TYPE),
        "Element handle must be an element factory type %s got %s",
        ELEMENT_FACTORY_TYPE,
        handle.type());
  }

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
  private static final MethodHandle METHOD_INVOKE_HANDLE =
      findVirtualOrDie(
          Method.class, "invoke", methodType(Object.class, Object.class, Object[].class));

  /**
   * Returns a handle that calls {@link Method#invoke(Object, Object...)} the given method with the
   * signature {@code (Object, Object[])->Object}.
   *
   * <p>InvocationTargetExceptions are handled and rethrown as Throwable.
   */
  static MethodHandle invokeHandle(Method m) {
    var rethrow =
        MethodHandles.filterReturnValue(
            INVOCATION_TARGET_EXCEPTION_GET_CAUSE,
            MethodHandles.throwException(Object.class, Throwable.class));
    return MethodHandles.catchException(
        METHOD_INVOKE_HANDLE.bindTo(m), InvocationTargetException.class, rethrow);
  }

  private static final MethodHandle INVOCATION_TARGET_EXCEPTION_GET_CAUSE =
      findVirtualOrDie(InvocationTargetException.class, "getCause", methodType(Throwable.class));

  /**
   * A handle for {@link Method#invoke}, useful when we can neither use a fastclass or direct method
   * handle.
   */
  private static final MethodHandle CONSTRUCTOR_NEWINSTANCE_HANDLE =
      findVirtualOrDie(Constructor.class, "newInstance", methodType(Object.class, Object[].class));

  /**
   * Returns a handle that invokes the given method with the signature (Object[])->Object.
   *
   * <p>InvocationTargetExceptions are handled and rethrown as Throwable.
   */
  static MethodHandle newInstanceHandle(Constructor<?> c) {
    var rethrow =
        MethodHandles.filterReturnValue(
            INVOCATION_TARGET_EXCEPTION_GET_CAUSE,
            MethodHandles.throwException(Object.class, Throwable.class));
    return MethodHandles.catchException(
        CONSTRUCTOR_NEWINSTANCE_HANDLE.bindTo(c), InvocationTargetException.class, rethrow);
  }

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
      paramSize += getParamSize(param);
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
   * Returns how many 'slots' the param will consume. `long` and `douhble` take 2 and everything
   * else takes 1.
   */
  private static int getParamSize(Class<?> param) {
    return param == long.class || param == double.class ? 2 : 1;
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
      paramSize += getParamSize(param);
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
   * Returns a method handle with the signature {@code (InternalContext, Dependency) -> Object} that
   * returns the given instance.
   */
  static MethodHandle constantFactoryGetHandle(Object instance) {
    return MethodHandles.dropArguments(
        MethodHandles.constant(Object.class, instance), 0, InternalContext.class, Dependency.class);
  }

  /**
   * Returns a method handle with the signature {@code (InternalContext) -> Object} that returns the
   * given instance.
   */
  static MethodHandle constantElementFactoryGetHandle(Object instance) {
    return MethodHandles.dropArguments(
        MethodHandles.constant(Object.class, instance), 0, InternalContext.class);
  }

  /**
   * Returns a method handle with the {@link #FACTORY_TYPE} signature that returns the result of the
   * given initializable.
   */
  static MethodHandle initializableFactoryGetHandle(Initializable<?> initializable) {
    return MethodHandles.dropArguments(
        new InitializableCallSite(initializable).dynamicInvoker(), 1, Dependency.class);
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
      super(ELEMENT_FACTORY_TYPE);
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
      setTarget(constantElementFactoryGetHandle(value));
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
  static MethodHandle nullCheckResult(MethodHandle delegate, Object source) {
    var type = delegate.type();
    checkArgument(
        type.parameterCount() >= 2
            && type.parameterType(0).equals(InternalContext.class)
            && type.parameterType(1).equals(Dependency.class),
        "Expected %s to have an initial InternalContext and Dependency parameter",
        delegate);
    // (Object, InternalContext, Dependency) -> Object
    var nullCheckResult = MethodHandles.insertArguments(NULL_CHECK_RESULT_MH, 1, source);
    if (type.parameterCount() > 2) {
      // (InternalContext, Dependency) -> Object
      nullCheckResult =
          MethodHandles.dropArguments(
              nullCheckResult, 3, type.parameterList().subList(2, type.parameterCount()));
    }
    return MethodHandles.foldArguments(nullCheckResult, delegate);
  }

  private static final MethodHandle NULL_CHECK_RESULT_MH =
      findStaticOrDie(
          InternalMethodHandles.class,
          "doNullCheckResult",
          methodType(
              Object.class, Object.class, Object.class, InternalContext.class, Dependency.class));

  @Keep
  private static Object doNullCheckResult(
      Object result, Object source, InternalContext ignored, Dependency<?> dependency)
      throws InternalProvisionException {
    if (result == null && !dependency.isNullable()) {
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
      MethodHandle delegate, @Nullable ProvisionListenerStackCallback<?> listener) {
    var type = delegate.type();
    checkArgument(type.parameterType(0).equals(InternalContext.class));
    checkArgument(type.parameterType(1).equals(Dependency.class));
    if (listener == null) {
      return delegate;
    }
    // (InternalContext, Dependency, ProvisionCallback)->Object
    var provision = PROVISION_CALLBACK_PROVISION_HANDLE.bindTo(listener);
    // Support a few kinds of provision callbacks, as needed.
    // The problem we have is that we need to create a MethodHandle that constructs a
    // ProvisionCallback around the delegate.  If the delegate only accepts an InternalContext then
    // we can construct a constant ProvisionCallback that just calls the delegate.  If the delegate
    // has other 'free' parameters we will need to construct a MethodHandle that accepts those free
    // parameters and constructs a ProvisionCallback that calls the delegate with those parameters
    // plus the InternalContext and the ProvisionCallback.
    if (type.equals(FACTORY_TYPE)) {
      ProvisionCallback<Object> callback =
          (ctx, dep) -> {
            try {
              // This invokeExact is not as fast as it could be since the `delegate` is not
              // constant foldable. The only solution to that is custom bytecode generation, but
              // that probably isn't worth the effort since provision callbacks are rare.
              return (Object) delegate.invokeExact(ctx, dep);
            } catch (Throwable t) {
              // This is lame but is needed to work around the different exception semantics of
              // method handles.  In reality this is either a InternalProvisionException or a
              // RuntimeException. Both of which are throwable by this method.
              // The only alternative is to generate bytecode to call the method handle which
              // avoids this problem at great expense.
              throw sneakyThrow(t);
            }
          };
      return MethodHandles.insertArguments(provision, 2, callback);
    } else if (type.parameterCount() == 3) {
      var callback =
          MethodHandles.insertArguments(
              MAKE_PROVISION_CALLBACK_1_HANDLE,
              0,
              delegate.asType(
                  delegate
                      .type()
                      // Upcast the parameter type to Object so we don't need to care about it.
                      .changeParameterType(2, Object.class)));
      // Cast it back to the original type of the delegate.
      return MethodHandles.filterArguments(provision, 2, callback).asType(delegate.type());
    } else {
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

  // A simple factory for the case where the delegate only has a single free parameter that we need
  // to capture.
  @Keep
  private static final ProvisionCallback<Object> makeProvisionCallback(
      MethodHandle delegate, Object capture) {
    return (ctx, dep) -> {
      try {
        return (Object) delegate.invokeExact(ctx, dep, capture);
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
   * } catch (RuntimeException re) {
   *   throw InternalProvisionException.errorInProvider(re).addSource(source);
   * }
   * }</pre>
   */
  static MethodHandle catchRuntimeExceptionInProviderAndRethrowWithSource(
      MethodHandle delegate, Object source) {
    var rethrow =
        MethodHandles.insertArguments(
            CATCH_RUNTIME_EXCEPTION_IN_PROVIDER_AND_RETHROW_WITH_SOURCE_HANDLE, 1, source);

    return MethodHandles.catchException(
            castReturnToObject(delegate), RuntimeException.class, rethrow)
        .asType(delegate.type());
  }

  private static final MethodHandle
      CATCH_RUNTIME_EXCEPTION_IN_PROVIDER_AND_RETHROW_WITH_SOURCE_HANDLE =
          findStaticOrDie(
              InternalMethodHandles.class,
              "doCatchRuntimeExceptionInProviderAndRethrowWithSource",
              methodType(Object.class, RuntimeException.class, Object.class));

  @Keep
  private static Object doCatchRuntimeExceptionInProviderAndRethrowWithSource(
      RuntimeException re, Object source) throws InternalProvisionException {
    throw InternalProvisionException.errorInProvider(re).addSource(source);
  }

  /**
   * Surrounds the delegate with a catch block that rethrows the exception with the given source.
   *
   * <pre>{@code
   * try {
   *   return delegate(...);
   * } catch (InternalProvisionException ipe) {
   *   throw ipe.addSource(source);
   * } catch (Throwable t) {
   *   throw InternalProvisionException.errorInProvider(t).addSource(source);
   * }
   * }</pre>
   */
  static MethodHandle catchThrowableInProviderAndRethrowWithSource(
      MethodHandle delegate, Object source) {
    var rethrow =
        MethodHandles.insertArguments(
            CATCH_THROWABLE_IN_PROVIDER_AND_RETHROW_WITH_SOURCE_HANDLE, 1, source);

    return MethodHandles.catchException(castReturnToObject(delegate), Throwable.class, rethrow)
        .asType(delegate.type());
  }

  private static final MethodHandle CATCH_THROWABLE_IN_PROVIDER_AND_RETHROW_WITH_SOURCE_HANDLE =
      findStaticOrDie(
          InternalMethodHandles.class,
          "doCatchThrowableInProviderAndRethrowWithSource",
          methodType(Object.class, Throwable.class, Object.class));

  @Keep
  private static Object doCatchThrowableInProviderAndRethrowWithSource(Throwable re, Object source)
      throws InternalProvisionException {
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
   *   throw InternalProvisionException.errorInjectingMethod(re).addSource(source);
   * }
   * }</pre>
   */
  static MethodHandle catchErrorInMethodAndRethrowWithSource(
      MethodHandle delegate, InjectionPoint source) {
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
          methodType(void.class, Throwable.class, InjectionPoint.class));

  @Keep
  private static void doCatchErrorInMethodAndRethrowWithSource(Throwable re, InjectionPoint source)
      throws InternalProvisionException {
    throw InternalProvisionException.errorInjectingMethod(re).addSource(source);
  }

  /**
   * Surrounds the delegate with a catch block that rethrows the exception with the given source.
   *
   * <pre>{@code
   * try {
   *   return delegate(...);
   * } catch (Throwable re) {
   *   throw InternalProvisionException.errorInjectingConstructor(re).addSource(source);
   * }
   * }</pre>
   *
   * <p>This can accept any MethodHandle but the return type must be Object.
   */
  static MethodHandle catchErrorInConstructorAndRethrowWithSource(
      MethodHandle delegate, InjectionPoint source) {
    var rethrow =
        MethodHandles.insertArguments(
            CATCH_ERROR_IN_CONSTRUCTOR_AND_RETHROW_WITH_SOURCE_HANDLE, 1, source);
    rethrow = castReturnTo(rethrow, delegate.type().returnType());

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
  static MethodHandle tryStartConstruction(MethodHandle delegate, int circularFactoryId) {
    // NOTE: we cannot optimize this further by assuming that the return value is always null when
    // circular proxies are disabled, because if parent and child injectors differ in that setting
    // then injectors with circularProxiesDisabled may still run in a context where they are enabled
    // and visa versa.

    // (InternalContext, Dependency)->Object
    var tryStartConstruction =
        MethodHandles.insertArguments(TRY_START_CONSTRUCTION_HANDLE, 1, circularFactoryId);
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

  /**
   * Generates a provider instance that delegates to the given factory.
   *
   * <p>This leverages the {@link InternalFactory#getHandle} method, but it only invokes it lazily.
   */
  static <T> Provider<T> makeProvider(
      InternalFactory<T> factory, InjectorImpl injector, Dependency<?> dependency) {
    return new MethodHandleProvider<>(injector, factory, dependency);
  }

  /**
   * Returns a {@link ProviderToInternalFactoryAdapter} subtype to support scoping.
   *
   * <p>The unique thing about scoping is that we need to support many different {@link Dependency}
   * instances.
   */
  static <T> ProviderToInternalFactoryAdapter<T> makeScopedProvider(
      InternalFactory<? extends T> factory, InjectorImpl injector) {
    return new MethodHandleProviderToInternalFactoryAdapter<T>(injector, factory);
  }

  /** A provider instance that delegates to a lazily constructed method handle. */
  static final class MethodHandleProviderToInternalFactoryAdapter<T>
      extends ProviderToInternalFactoryAdapter<T> {
    // Protected via double checked locking
    private volatile MethodHandle handle;

    MethodHandleProviderToInternalFactoryAdapter(
        InjectorImpl injector, InternalFactory<? extends T> factory) {
      super(injector, factory);
    }

    @Override
    protected T doGet(InternalContext context, Dependency<?> dependency) {
      try {
        @SuppressWarnings("unchecked") // safety is provided by the Factory implementation.
        var typed = (T) (Object) getHandle().invokeExact(context, dependency);
        return typed;
      } catch (Throwable t) {
        throw sneakyThrow(t); // This includes InternalProvisionException.
      }
    }

    private MethodHandle getHandle() {
      var local = this.handle;
      if (local == null) {
        // synchronize on the factory instead of `this` since users might be using the provider
        // itself as a lock.
        synchronized (internalFactory) {
          local = this.handle;
          if (local == null) {
            local = internalFactory.getHandle(new LinkageContext(), /* linked= */ true);
            this.handle = local;
          }
        }
      }
      return handle;
    }
  }

  /**
   * A provider that delegates to a lazily constructed MethodHandle.
   *
   * <p>This class encapsulates the logic for entering and exiting the injector context, and
   * handling {@link InternalProvisionException}s.
   */
  static final class MethodHandleProvider<T> implements Provider<T> {
    private final InjectorImpl injector;
    private final InternalFactory<? extends T> factory;
    private final Dependency<?> dependency;
    // Uses the double-checked-locking pattern.
    private volatile MethodHandle handle;

    MethodHandleProvider(
        InjectorImpl injector, InternalFactory<T> factory, Dependency<?> dependency) {
      this.injector = injector;
      this.factory = factory;
      this.dependency = dependency;
    }

    @Override
    public T get() {
      InternalContext currentContext = injector.enterContext();
      try {
        @SuppressWarnings("unchecked") // safety is provided by the Factory implementation.
        var typed = (T) (Object) getHandle().invokeExact(currentContext);
        return typed;
      } catch (InternalProvisionException e) {
        throw e.addSource(dependency).toProvisionException();
      } catch (Throwable t) {
        throw sneakyThrow(t);
      } finally {
        currentContext.close();
      }
    }

    @Override
    public String toString() {
      return factory.toString();
    }

    private MethodHandle getHandle() {
      var local = this.handle;
      if (local == null) {
        // synchronize on the factory instead of `this` since users might be using the provider
        // itself as a lock.
        synchronized (factory) {
          local = this.handle;
          if (local == null) {
            // Since the Dependency is a constant for all calls to this handle, insert it so the jit
            // can see it.
            local =
                MethodHandles.insertArguments(
                    factory.getHandle(new LinkageContext(), /* linked= */ false), 1, dependency);
            this.handle = local;
          }
        }
      }
      return handle;
    }
  }

  // The `throws` clause tricks Java type inference into deciding that E must be some subtype of
  // RuntimeException but because the cast is unchecked it doesn't check.  So the compiler cannot
  // tell that this might be a checked exception.
  @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals", "CheckedExceptionNotThrown"})
  static <E extends Throwable> E sneakyThrow(Throwable e) throws E {
    throw (E) e;
  }

  /**
   * Builds and returns a `(InternalContext)->Object[]` handle from all the element handles.
   *
   * <p>This scales to any number of parameters.
   */
  static MethodHandle buildObjectArrayFactory(MethodHandle[] elementHandles) {
    return buildObjectArrayFactory(ImmutableList.copyOf(elementHandles));
  }

  /**
   * Builds and returns a `(InternalContext)->Object[]` handle from all the element handles.
   *
   * <p>This scales to any number of parameters.
   */
  static MethodHandle buildObjectArrayFactory(Iterable<MethodHandle> elementHandles) {
    var elementHandlesList = ImmutableList.copyOf(elementHandles);
    for (var handle : elementHandlesList) {
      checkHasElementFactoryType(handle);
    }
    // Empty arrays are immutable and we don't care about identity.
    if (elementHandlesList.isEmpty()) {
      return EMPTY_OBJECT_ARRAY_HANDLE;
    }
    // The approach to build an array is complex in the MethodHandle API.
    // See
    // https://stackoverflow.com/questions/79551257/efficiently-build-a-large-array-with-methodhandles/79558740#79558740
    // For a discussion of the different approaches, we follow that advice and just use a
    // straightforward recursive approach.
    // (Object[]) -> Object[]
    // (Object[], InternalContext) -> void
    var populateArray = populateArray(0, elementHandlesList);
    // (Object[], InternalContext) -> Object[]
    populateArray =
        MethodHandles.foldArguments(
            MethodHandles.dropArguments(
                MethodHandles.identity(Object[].class), 1, InternalContext.class),
            populateArray);
    // ()->Object[]
    var constructArray =
        MethodHandles.insertArguments(OBJECT_ARRAY_CONSTRUCTOR, 0, elementHandlesList.size());
    // (InternalContext) -> Object[]
    constructArray = MethodHandles.dropArguments(constructArray, 0, InternalContext.class);
    return MethodHandles.foldArguments(populateArray, constructArray);
  }

  private static final MethodHandle EMPTY_OBJECT_ARRAY_HANDLE =
      MethodHandles.dropArguments(
          MethodHandles.constant(Object[].class, new Object[0]), 0, InternalContext.class);
  private static final MethodHandle OBJECT_ARRAY_CONSTRUCTOR =
      MethodHandles.arrayConstructor(Object[].class);
  private static final MethodHandle OBJECT_ARRAY_ELEMENT_SETTER =
      MethodHandles.arrayElementSetter(Object[].class);

  /**
   * Recursive helper to populate an array. Builds a balanced binary tree of handles that assign to
   * array elements.
   *
   * <p>Returns a handle of type (Object[], InternalContext) -> void
   */
  private static MethodHandle populateArray(int offset, List<MethodHandle> elementFactories) {
    var size = elementFactories.size();
    if (size == 0) {
      throw new IllegalArgumentException("Cannot populate an empty array");
    }
    if (size == 1) {
      // (Object[], InternalContext) -> void
      return MethodHandles.filterArguments(
          MethodHandles.insertArguments(OBJECT_ARRAY_ELEMENT_SETTER, 1, offset),
          1,
          elementFactories.get(0));
    }
    var half = size / 2;
    var left = elementFactories.subList(0, half);
    var right = elementFactories.subList(half, size);
    var leftHandle = populateArray(offset, left);
    var rightHandle = populateArray(offset + half, right);
    return MethodHandles.foldArguments(rightHandle, leftHandle);
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
   *
   * <p>Returns a handle with the signature `(InternalContext) -> Object`.
   */
  static MethodHandle buildImmutableSetFactory(Iterable<MethodHandle> elementHandles) {
    var elementHandlesList = ImmutableList.copyOf(elementHandles);
    for (var handle : elementHandlesList) {
      checkHasElementFactoryType(handle);
    }
    MethodHandle setHandle;
    int size = elementHandlesList.size();
    if (size == 0) {
      // ImmutableSet.of()
      return constantElementFactoryGetHandle(ImmutableSet.of());
    }
    // (Object, ...Object)->ImmutableSet
    var immutableSetOf = immutableSetOf(size);
    if (immutableSetOf != null) {
      // (InternalContext,... InternalContext) -> ImmutableSet
      immutableSetOf =
          MethodHandles.filterArguments(
              immutableSetOf, 0, elementHandlesList.toArray(new MethodHandle[0]));
      // (InternalContext) -> ImmutableSet
      immutableSetOf =
          MethodHandles.permuteArguments(
              immutableSetOf, methodType(ImmutableSet.class, InternalContext.class), new int[size]);
      setHandle = immutableSetOf;
    } else {
      // ImmutableSet.builderWithExpectedSize(<size>)
      var builder = MethodHandles.insertArguments(IMMUTABLE_SET_BUILDER_OF_SIZE_HANDLE, 0, size);

      builder = MethodHandles.foldArguments(doAddToImmutableSet(elementHandlesList), builder);
      setHandle = MethodHandles.filterReturnValue(builder, IMMUTABLE_SET_BUILDER_BUILD_HANDLE);
    }

    return castReturnToObject(setHandle);
  }

  /**
   * A helper to generate calls to add to the ImmutableSet builder.
   *
   * <p>Returns a handle of type (ImmutableSet.Builder, InternalContext) -> ImmutableSet.Builder
   */
  private static MethodHandle doAddToImmutableSet(ImmutableList<MethodHandle> elementHandlesList) {
    // We don't want to 'fold' too deep as it can lead to a stack overflow.  So we have a special
    // case for small lists.
    int size = elementHandlesList.size();
    if (size == 0) {
      throw new IllegalArgumentException("Cannot add to an empty set");
    }
    if (size == 1) {
      return MethodHandles.filterArguments(
          IMMUTABLE_SET_BUILDER_ADD_HANDLE, 1, elementHandlesList.get(0));
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

  private static final ConcurrentHashMap<Integer, MethodHandle> immutableSetOfHandles =
      new ConcurrentHashMap<>();

  @Nullable
  private static final MethodHandle immutableSetOf(int arity) {
    if (arity > MAX_BINDABLE_ARITY) {
      return null;
    }
    return immutableSetOfHandles.computeIfAbsent(
        arity,
        n -> {
          if (n < 6) {
            var type = methodType(ImmutableSet.class);
            type = type.appendParameterTypes(Collections.nCopies(n, Object.class));
            return findStaticOrDie(ImmutableSet.class, "of", type);
          }
          if (n == 6) {
            return MethodHandles.insertArguments(
                IMMUTABLE_SET_OF_VARARGS_HANDLE, 6, (Object) new Object[0]);
          }
          return IMMUTABLE_SET_OF_VARARGS_HANDLE.asCollector(6, Object[].class, n - 6);
        });
  }

  private static final MethodHandle IMMUTABLE_SET_OF_VARARGS_HANDLE =
      findStaticOrDie(
          ImmutableSet.class,
          "of",
          methodType(
              ImmutableSet.class,
              Object.class,
              Object.class,
              Object.class,
              Object.class,
              Object.class,
              Object.class,
              Object[].class));
  private static final MethodHandle IMMUTABLE_SET_BUILDER_OF_SIZE_HANDLE =
      findStaticOrDie(
          ImmutableSet.class,
          "builderWithExpectedSize",
          methodType(ImmutableSet.Builder.class, int.class));
  private static final MethodHandle IMMUTABLE_SET_BUILDER_ADD_HANDLE =
      findVirtualOrDie(
          ImmutableSet.Builder.class, "add", methodType(ImmutableSet.Builder.class, Object.class));
  private static final MethodHandle IMMUTABLE_SET_BUILDER_BUILD_HANDLE =
      findVirtualOrDie(ImmutableSet.Builder.class, "build", methodType(ImmutableSet.class));

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
   *
   * <p>Returns a handle with the signature `(InternalContext) -> Object`.
   */
  static <T> MethodHandle buildImmutableMapFactory(List<Map.Entry<T, MethodHandle>> entries) {
    for (var entry : entries) {
      checkHasElementFactoryType(entry.getValue());
    }
    if (entries.isEmpty()) {
      return constantElementFactoryGetHandle(ImmutableMap.of());
    }

    // See if we can bind to ImmutableMap.of(...)
    var immutableMapOf = immutableMapOf(entries.size());
    if (immutableMapOf != null) {
      // First bind all the keys
      immutableMapOf =
          MethodHandles.insertArguments(
              immutableMapOf, 0, entries.stream().map(e -> e.getKey()).toArray());

      // Then all the values
      immutableMapOf =
          MethodHandles.filterArguments(
              immutableMapOf,
              0,
              entries.stream().map(e -> e.getValue()).toArray(MethodHandle[]::new));
      // then merge the InternalContext params
      immutableMapOf =
          MethodHandles.permuteArguments(
              immutableMapOf,
              methodType(ImmutableMap.class, InternalContext.class),
              new int[entries.size()]);
      return castReturnToObject(immutableMapOf);
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
        .asType(ELEMENT_FACTORY_TYPE);
  }

  /** Returns a handle of type (ImmutableMap.Builder, InternalContext) -> ImmutableMap.Builder */
  private static <T> MethodHandle doPutEntries(List<Map.Entry<T, MethodHandle>> entries) {
    int size = entries.size();
    checkArgument(size > 0, "entries must not be empty");
    if (size == 1) {
      var entry = entries.get(0);
      // `put` has the signature `put(Builder, K, V)->Builder` (the first parameter is 'this').
      // Insert the 'constant' key to get this signature:
      // (Builder, V)->Builder
      var put = MethodHandles.insertArguments(IMMUTABLE_MAP_BUILDER_PUT_HANDLE, 1, entry.getKey());
      // Construct the value, by calling the factory method handle to supply the first argument
      // (the value).  Because the entry is a MethodHandle with signature `(InternalContext)->V` we
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
      return put;
    } else {
      // Otherwise we split and recurse, this basically ensures that none of the lambda forms are
      // too big and the 'folds' are not too deep.
      int half = size / 2;
      return MethodHandles.foldArguments(
          doPutEntries(entries.subList(half, size)),
          dropReturn(doPutEntries(entries.subList(0, half))));
    }
  }

  private static final ConcurrentHashMap<Integer, MethodHandle> immutableMapOfHandles =
      new ConcurrentHashMap<>();

  /**
   * Returns a handle for calling ImmutableMap.of, but it reorders the parameters so that all the
   * keys come first and all the values come last instead of alternating.
   */
  @Nullable
  private static final MethodHandle immutableMapOf(int numEntries) {
    if (numEntries > 10) {
      // In theory we could support more by targeting the varargs method that takes Map.Entry
      // objects
      // but at that point it is better to have the builder API construct the entries for us.
      return null;
    }
    return immutableMapOfHandles.computeIfAbsent(
        numEntries,
        n -> {
          var type = methodType(ImmutableMap.class);
          type = type.appendParameterTypes(Collections.nCopies(n * 2, Object.class));
          var immutableMapOf = findStaticOrDie(ImmutableMap.class, "of", type);
          // We want all the keys to come first and the values to come last
          // So all the even numbers go to the front and the od numbers go to the end.
          int[] reorder = new int[n * 2];
          for (int i = 0; i < n; i++) {
            // all the even indices are keys, send them to the initial params
            reorder[2 * i] = i;
            // odds are values send to the back
            reorder[2 * i + 1] = i + n;
          }
          return MethodHandles.permuteArguments(immutableMapOf, type, reorder);
        });
  }

  private static final MethodHandle IMMUTABLE_MAP_BUILDER_WITH_EXPECTED_SIZE_HANDLE =
      findStaticOrDie(
          ImmutableMap.class,
          "builderWithExpectedSize",
          methodType(ImmutableMap.Builder.class, int.class));
  private static final MethodHandle IMMUTABLE_MAP_BUILDER_PUT_HANDLE =
      findVirtualOrDie(
          ImmutableMap.Builder.class,
          "put",
          methodType(ImmutableMap.Builder.class, Object.class, Object.class));
  private static final MethodHandle IMMUTABLE_MAP_BUILDER_BUILD_OR_THROW_HANDLE =
      findVirtualOrDie(ImmutableMap.Builder.class, "buildOrThrow", methodType(ImmutableMap.class));

  private InternalMethodHandles() {}
}
