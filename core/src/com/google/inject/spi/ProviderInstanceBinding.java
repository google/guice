/*
 * Copyright (C) 2008 Google Inc.
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

package com.google.inject.spi;

import com.google.inject.Binding;
import com.google.inject.Provider;
import java.util.Set;

/**
 * A binding to a provider instance. The provider's {@code get} method is invoked to resolve
 * injections.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 * @since 2.0
 */
public interface ProviderInstanceBinding<T> extends Binding<T>, HasDependencies {

  /**
   * If the user supplied a JSR330 binding, then this will wrap that one. To always return the
   * user-supplied provider, use {@link #getUserSuppliedProvider}.
   *
   * @deprecated Use {@link #getUserSuppliedProvider} instead.
   */
  @Deprecated
  Provider<? extends T> getProviderInstance();

  /**
   * Returns the user-supplied, unscoped provider.
   *
   * @since 4.0
   */
  javax.inject.Provider<? extends T> getUserSuppliedProvider();

  /**
   * Returns the field and method injection points of the provider, injected at injector-creation
   * time only.
   *
   * @return a possibly empty set
   */
  Set<InjectionPoint> getInjectionPoints();
}
