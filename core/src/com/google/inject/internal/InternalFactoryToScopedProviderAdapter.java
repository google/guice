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

import com.google.errorprone.annotations.concurrent.LazyInit;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.spi.Dependency;

/**
 * A factory that wraps a provider that has been scoped.
 *
 * @author crazybob@google.com (Bob Lee)
 */
class InternalFactoryToScopedProviderAdapter<T> implements InternalFactory<T> {
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
    // Set the dependency here so it is available to scope implementations and scope delegate
    // providers. This allows us to create proxies of super-interfaces to support cyclic
    // dependencies.  This method is a no-op when cyclic proxies are disabled.
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

    private T getAndCache(InjectorImpl injector, Dependency<?> dependency)
        throws InternalProvisionException {
      try (InternalContext context = injector.enterContext()) {
        T t = get(context, dependency, /* linked= */ false);
        if (!context.areCircularProxiesEnabled() || !BytecodeGen.isCircularProxy(t)) {
          // Avoid caching circular proxies.
          this.value = t;
        }
        return t;
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
