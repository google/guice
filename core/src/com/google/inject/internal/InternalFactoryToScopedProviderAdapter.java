/*
 * Copyright (C) 2006 Google Inc.
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.inject.internal.InternalMethodHandles.findVirtualOrDie;
import static java.lang.invoke.MethodType.methodType;

import com.google.errorprone.annotations.Keep;
import com.google.errorprone.annotations.concurrent.LazyInit;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.spi.Dependency;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MutableCallSite;

/**
 * A factory that wraps a provider that has been scoped.
 *
 * @author crazybob@google.com (Bob Lee)
 */
class InternalFactoryToScopedProviderAdapter<T> extends InternalFactory<T> {
  /** Creates a factory around a scoped provider. */
  static <T> InternalFactoryToScopedProviderAdapter<T> create(
      Scope scopeInstance, Provider<? extends T> provider, Object source) {
    if (scopeInstance instanceof SingletonScope) {
      return new ForSingletonScope<>(provider, source);
    }
    return new InternalFactoryToScopedProviderAdapter<>(provider, source);
  }

  private final Provider<? extends T> provider;
  final Object source;

  private InternalFactoryToScopedProviderAdapter(Provider<? extends T> provider, Object source) {
    this.provider = checkNotNull(provider, "provider");
    this.source = checkNotNull(source, "source");
  }

  @Override
  public T get(InternalContext context, Dependency<?> dependency, boolean linked)
      throws InternalProvisionException {
    // Set the dependency here so it is available to scope implementations (such as SingletonScope)
    // The reason we need this is so that Scope implementations (and scope delegate providers) can
    // create proxies of super-interfaces to support cyclic dependencies.  It would be nice to
    // drop the setDependency method (and field), but that could only happen if cyclic proxies
    // were also dropped.
    context.setDependency(dependency);
    try {
      T t = provider.get();
      if (t == null && !dependency.isNullable()) {
        InternalProvisionException.onNullInjectedIntoNonNullableDependency(source, dependency);
      }
      return t;
    } catch (RuntimeException userException) {
      throw InternalProvisionException.errorInProvider(userException).addSource(source);
    }
  }

  @Override
  MethodHandleResult makeHandle(LinkageContext context, boolean linked) {
    // Call Provider.get() on the constant provider
    // ()->Object
    var invokeProvider =
        InternalMethodHandles.getProvider(
            MethodHandles.constant(jakarta.inject.Provider.class, provider));

    // Satisfy the signature for the factory protocol.
    // (InternalContext, Dependency) -> Object
    invokeProvider =
        MethodHandles.dropArguments(invokeProvider, 0, InternalContext.class, Dependency.class);
    // null check the result using the dependency.
    // ()->Object
    invokeProvider = InternalMethodHandles.nullCheckResult(invokeProvider, source);
    // Catch any RuntimeException as an InternalProvisionException.
    // ()->Object
    invokeProvider =
        InternalMethodHandles.catchRuntimeExceptionInProviderAndRethrowWithSource(
            invokeProvider, source);

    // We need to call 'setDependency' so it is available to scope implementations and scope
    // delegate providers. See comment in `get` method for more details.
    invokeProvider =
        MethodHandles.foldArguments(invokeProvider, INTERNAL_CONTEXT_SET_DEPENDENCY_HANDLE);
    return makeCachable(invokeProvider);
  }

  private static final MethodHandle INTERNAL_CONTEXT_SET_DEPENDENCY_HANDLE =
      InternalMethodHandles.findVirtualOrDie(
          InternalContext.class, "setDependency", methodType(void.class, Dependency.class));

  @Override
  public String toString() {
    return provider.toString();
  }

  /**
   * A factory that wraps a provider that has been scoped by the singleton scope.
   *
   * <p>This special case allows us to optimize the behavior of Provider instances for singleton
   * bindings. See https://github.com/google/guice/issues/1802 for more details.
   */
  static final class ForSingletonScope<T> extends InternalFactoryToScopedProviderAdapter<T> {
    private static final Object UNINITIALIZED_VALUE = new Object();

    // Cache the values here so we can optimize the behavior of Provider instances.
    // We do not use a lock but rather a volatile field to safely publish values.  This means we
    // might compute the value multiple times, but we rely on internal synchronization inside the
    // singleton scope to ensure that we only compute the value once.
    // See https://github.com/google/guice/issues/1802 for more details.
    @LazyInit private volatile Object value = UNINITIALIZED_VALUE;

    ForSingletonScope(Provider<? extends T> provider, Object source) {
      super(provider, source);
    }

    @Override
    public T get(InternalContext context, Dependency<?> dependency, boolean linked)
        throws InternalProvisionException {
      Object value = this.value;
      if (value != UNINITIALIZED_VALUE) {
        // safe because we only store values of T or UNINITIALIZED_VALUE
        @SuppressWarnings("unchecked")
        T typedValue = (T) value;
        if (typedValue == null && !dependency.isNullable()) {
          InternalProvisionException.onNullInjectedIntoNonNullableDependency(source, dependency);
        }
        return typedValue;
      }
      T t = super.get(context, dependency, linked);
      if (!context.areCircularProxiesEnabled() || !BytecodeGen.isCircularProxy(t)) {
        // Avoid caching circular proxies.
        this.value = t;
      }
      return t;
    }

    @Override
    MethodHandleResult makeHandle(LinkageContext context, boolean linked) {
      // If it is somehow already initialized, we can return a constant handle.
      Object value = this.value;
      if (value != UNINITIALIZED_VALUE) {
        return makeCachable(getHandleForConstant(source, value));
      }
      // Otherwise we bind to a callsite that will patch itself once it is initialized.
      var result = super.makeHandle(context, linked);
      checkState(result.cachability == MethodHandleResult.Cachability.ALWAYS);
      return makeCachable(new SingletonCallSite(result.methodHandle, source).dynamicInvoker());
    }

    private static MethodHandle getHandleForConstant(Object source, Object value) {
      var handle = InternalMethodHandles.constantFactoryGetHandle(value);
      // Since this is a constant, we only need to null check if it is actually null.
      if (value == null) {
        handle = InternalMethodHandles.nullCheckResult(handle, source);
      }
      return handle;
    }

    /**
     * A special callsite that allows us to take advantage of the singleton scope semantics.
     *
     * <p>After initialization we can patch the callsite with a constant, which should unlock some
     * inlining opportunities.
     */
    static final class SingletonCallSite extends MutableCallSite {
      static final MethodHandle BOOTSTRAP_CALL_MH =
          findVirtualOrDie(
              SingletonCallSite.class,
              "boostrapCallSite",
              methodType(Object.class, Object.class, InternalContext.class, Dependency.class));

      private final Object source;

      SingletonCallSite(MethodHandle actualGetHandle, Object source) {
        super(actualGetHandle.type());
        this.source = source;
        // Invoke the 'actual' handle and then pass the result to the `boostrapCallSite` method.
        // This will allow us to eventually 'fold' the result into the callsite.
        // (InternalContext, InternalContext) -> Object
        var invokeBootstrap =
            MethodHandles.foldArguments(BOOTSTRAP_CALL_MH.bindTo(this), actualGetHandle);
        setTarget(invokeBootstrap);
      }

      @Keep
      Object boostrapCallSite(Object result, InternalContext context, Dependency<?> dependency) {
        // Don't cache circular, proxies.
        if (!context.areCircularProxiesEnabled() || !BytecodeGen.isCircularProxy(result)) {
          setTarget(getHandleForConstant(source, result));
          // This ensures that other threads will see the new target.  This isn't strictly necessary
          // since the underlying provider is both ThreadSafe and idempotent, but it should improve
          // performance by giving the JIT and easy optimization opportunity.
          MutableCallSite.syncAll(new MutableCallSite[] {this});
        }
        // otherwise we shouldn't cache the result.
        return result;
      }
    }

    private T getAndCache(InjectorImpl injector, Dependency<?> dependency)
        throws InternalProvisionException {
      try (InternalContext context = injector.enterContext()) {
        return get(context, dependency, /* linked= */ false);
      }
    }

    @Override
    public Provider<T> makeProvider(InjectorImpl injector, Dependency<?> dependency) {
      var value = this.value;
      if (value != UNINITIALIZED_VALUE) {
        @SuppressWarnings("unchecked")
        T typedValue = (T) value;
        return InternalFactory.makeProviderForNullable(typedValue, this, dependency, source);
      }
      if (dependency.isNullable()) {
        return new Provider<T>() {
          @Override
          public T get() {
            Object value = ForSingletonScope.this.value;
            if (value != UNINITIALIZED_VALUE) {
              // safe because we only store values of T or UNINITIALIZED_VALUE
              @SuppressWarnings("unchecked")
              T typedValue = (T) value;
              return typedValue;
            }
            try {
              return getAndCache(injector, dependency);
            } catch (InternalProvisionException e) {
              throw e.addSource(dependency).toProvisionException();
            }
          }
        };
      }
      return new Provider<T>() {
        @Override
        public T get() {
          try {
            Object value = ForSingletonScope.this.value;
            if (value != UNINITIALIZED_VALUE) {
              if (value == null) {
                InternalProvisionException.onNullInjectedIntoNonNullableDependency(
                    source, dependency);
              }
              // safe because we only store values of T or UNINITIALIZED_VALUE
              @SuppressWarnings("unchecked")
              T typedValue = (T) value;
              return typedValue;
            }
            return getAndCache(injector, dependency);
          } catch (InternalProvisionException e) {
            throw e.addSource(dependency).toProvisionException();
          }
        }
      };
    }
  }
}
