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

import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.spi.Dependency;

/**
 * A provider that wraps an internal factory in order to pass to Scope instances.
 *
 * <p>This differs from the {@code Provider} instances constructed by the {@link InjectorImpl} in 2
 * ways:
 *
 * <ul>
 *   <li>It always pretends that the binding is linked, to support scoping implicit bindings.
 *   <li>It throws a slightly different ProvisionException if an error occurs. It doesn't add the
 *       current Dependency to the provision source stack.
 * </ul>
 *
 * <p>Additionally it is sensitive to the {@code disableCircularProxies} option and will use a
 * different implementation if it is enabled which allows us to avoid the cost of the {@link
 * InternalContext#getDependency()} protocol.
 *
 * @author crazybob@google.com (Bob Lee)
 */
class ProviderToInternalFactoryAdapter<T> implements Provider<T> {

  private final InjectorImpl injector;
  private final InternalFactory<? extends T> internalFactory;

  static <T> ProviderToInternalFactoryAdapter<T> create(
      InjectorImpl injector, InternalFactory<? extends T> internalFactory, Key<T> key) {
    // When Circular proxies are disabled we can use a constant Dependency based on the key of the
    // binding. However, if circular proxies are enabled we infer the dependency from the
    // InternalContext which makes it easier for us to create proxies for super interfaces.
    if (injector.options.disableCircularProxies) {
      return new ProviderToInternalFactoryAdapter.WithoutCircularProxies<>(
          injector, internalFactory, Dependency.get(key));
    }
    return new ProviderToInternalFactoryAdapter<>(injector, internalFactory);
  }

  private ProviderToInternalFactoryAdapter(
      InjectorImpl injector, InternalFactory<? extends T> internalFactory) {
    this.injector = injector;
    this.internalFactory = internalFactory;
  }

  @Override
  public T get() {
    InternalContext context = injector.enterContext();
    try {
      // Always pretend that we are a linked binding, to support
      // scoping implicit bindings.  If we are not actually a linked
      // binding, we'll fail properly elsewhere in the chain.
      return internalFactory.get(context, getDependency(context), /* linked= */ true);
    } catch (InternalProvisionException e) {
      throw e.toProvisionException();
    } finally {
      context.close();
    }
  }

  Dependency<?> getDependency(InternalContext context) {
    return context.getDependency();
  }

  /** Exposed for SingletonScope. */
  InjectorImpl getInjector() {
    return injector;
  }

  @Override
  public String toString() {
    return internalFactory.toString();
  }

  /**
   * A version of {@link ProviderToInternalFactoryAdapter} that doesn't depend on {@link
   * InternalContext#getDependency()} to get the current dependency and instead uses a constant
   * {@link Dependency} based on the key of the binding.
   */
  private static final class WithoutCircularProxies<T> extends ProviderToInternalFactoryAdapter<T> {
    // The dependency for the binding being scoped.
    private final Dependency<?> dependency;

    WithoutCircularProxies(
        InjectorImpl injector,
        InternalFactory<? extends T> internalFactory,
        Dependency<?> dependency) {
      super(injector, internalFactory);
      this.dependency = dependency;
    }

    @Override
    Dependency<?> getDependency(InternalContext context) {
      return dependency;
    }
  }
}
