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
 * @author crazybob@google.com (Bob Lee)
 */
public class ProviderToInternalFactoryAdapter<T> implements Provider<T> {

  private final InjectorImpl injector;
  final InternalFactory<? extends T> internalFactory;

  static <T> ProviderToInternalFactoryAdapter<T> create(
      InjectorImpl injector, InternalFactory<? extends T> internalFactory) {
    if (InternalFlags.getUseMethodHandlesOption()) {
      return InternalMethodHandles.makeScopedProvider(internalFactory, injector);
    }

    return new ProviderToInternalFactoryAdapter<>(injector, internalFactory);
  }

  protected ProviderToInternalFactoryAdapter(
      InjectorImpl injector, InternalFactory<? extends T> internalFactory) {
    this.injector = injector;
    this.internalFactory = internalFactory;
  }

  @Override
  public T get() {
    InternalContext context = injector.enterContext();
    try {
      return doGet(context, context.getDependency());
    } catch (InternalProvisionException e) {
      throw e.toProvisionException();
    } finally {
      context.close();
    }
  }

  // Exposed so it can be overridden by the generated provider when method handles are enabled.
  // See InternalMethodHandles.makeScopedProvider.
  protected T doGet(InternalContext context, Dependency<?> dependency)
      throws InternalProvisionException {
    // Always pretend that we are a linked binding, to support
    // scoping implicit bindings.  If we are not actually a linked
    // binding, we'll fail properly elsewhere in the chain.
    return internalFactory.get(context, dependency, true);
  }

  /** Exposed for SingletonScope. */
  InjectorImpl getInjector() {
    return injector;
  }

  @Override
  public String toString() {
    return internalFactory.toString();
  }
}
