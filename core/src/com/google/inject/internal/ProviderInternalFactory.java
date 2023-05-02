/*
 * Copyright (C) 2011 Google Inc.
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

import com.google.inject.spi.Dependency;
import javax.inject.Provider;

/**
 * Base class for InternalFactories that are used by Providers, to handle circular dependencies.
 *
 * @author sameb@google.com (Sam Berlin)
 */
abstract class ProviderInternalFactory<T>
    extends AbstractProviderInternalFactory<T, Provider<? extends T>> {
  ProviderInternalFactory(Object source) {
    super(source);
  }

  ProviderInternalFactory(
      Object source,
      ProvisionListenerStackCallback<T> provisionCallback) {
    super(source, provisionCallback);
  }

  @Override
  protected T provide(final Provider<? extends T> provider, final Dependency<?> dependency) {
    return provider.get();
  }
}
