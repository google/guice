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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.inject.spi.Dependency;
import javax.annotation.Nullable;
import jakarta.inject.Provider;

/**
 * Base class for InternalFactories that are used by Providers, to handle circular dependencies.
 *
 * @author sameb@google.com (Sam Berlin)
 */
abstract class ProviderInternalFactory<T> implements InternalFactory<T> {

  protected final Object source;
  private final int circularFactoryId;

  ProviderInternalFactory(Object source, int circularFactoryId) {
    this.source = checkNotNull(source, "source");
    this.circularFactoryId = circularFactoryId;
  }

  protected T circularGet(
      final Provider<? extends T> provider,
      InternalContext context,
      final Dependency<?> dependency,
      @Nullable ProvisionListenerStackCallback<T> provisionCallback)
      throws InternalProvisionException {
    @SuppressWarnings("unchecked")
    T result = (T) context.tryStartConstruction(circularFactoryId, dependency);
    if (result != null) {
      // We have a circular reference between bindings. Return a proxy.
      return result;
    }
    // Optimization: Don't go through the callback stack if no one's listening.
    if (provisionCallback == null) {
      return provision(provider, context, dependency);
    } else {
      return provisionCallback.provision(
          context, dependency, (ctx, dep) -> provision(provider, ctx, dep));
    }
  }

  /**
   * Provisions a new instance. Subclasses should override this to catch exceptions and rethrow as
   * ErrorsExceptions.
   */
  protected T provision(
      Provider<? extends T> provider, InternalContext context, Dependency<?> dependency)
      throws InternalProvisionException {
    T t = null;
    try {
      t = provider.get();
    } catch (RuntimeException e) {
      throw InternalProvisionException.errorInProvider(e).addSource(source);
    } finally {
      context.finishConstruction(circularFactoryId, t);
    }
    if (t == null && !dependency.isNullable()) {
      InternalProvisionException.onNullInjectedIntoNonNullableDependency(source, dependency);
    }
    return t;
  }
}
