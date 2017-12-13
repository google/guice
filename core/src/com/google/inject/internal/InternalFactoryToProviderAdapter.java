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

import com.google.inject.Provider;
import com.google.inject.spi.Dependency;

/** @author crazybob@google.com (Bob Lee) */
final class InternalFactoryToProviderAdapter<T> implements InternalFactory<T> {

  private final Provider<? extends T> provider;
  private final Object source;

  public InternalFactoryToProviderAdapter(Provider<? extends T> provider, Object source) {
    this.provider = checkNotNull(provider, "provider");
    this.source = checkNotNull(source, "source");
  }

  @Override
  public T get(InternalContext context, Dependency<?> dependency, boolean linked)
      throws InternalProvisionException {
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
}
