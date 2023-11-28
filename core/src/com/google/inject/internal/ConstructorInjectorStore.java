/*
 * Copyright (C) 2009 Google Inc.
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

import com.google.inject.spi.InjectionPoint;

/**
 * Constructor injectors by type.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
final class ConstructorInjectorStore {
  private final InjectorImpl injector;

  private final FailableCache<InjectionPoint, ConstructorInjector<?>> cache =
      new FailableCache<InjectionPoint, ConstructorInjector<?>>() {
        @Override
        protected ConstructorInjector<?> create(InjectionPoint constructorInjector, Errors errors)
            throws ErrorsException {
          return injector.createConstructor(this, constructorInjector, errors);
        }
      };

  ConstructorInjectorStore(InjectorImpl injector) {
    this.injector = injector;
  }

  /** Returns true if the store is in the process of loading this injection point. */
  boolean isLoading(InjectionPoint ip) {
    return cache.isLoading(ip);
  }

  /** Returns a new complete constructor injector with injection listeners registered. */
  public ConstructorInjector<?> get(InjectionPoint constructorInjector, Errors errors)
      throws ErrorsException {
    return cache.get(constructorInjector, errors);
  }

  /**
   * Purges an injection point from the cache. Use this only if the cache is not actually valid and
   * needs to be purged. (See issue 319 and
   * ImplicitBindingTest#testCircularJitBindingsLeaveNoResidue and
   * #testInstancesRequestingProvidersForThemselvesWithChildInjectors for examples of when this is
   * necessary.)
   *
   * <p>Returns true if the injector for that point was stored in the cache, false otherwise.
   */
  boolean remove(InjectionPoint ip) {
    return cache.remove(ip);
  }
}
