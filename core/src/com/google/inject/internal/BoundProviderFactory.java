/**
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
import com.google.inject.internal.InjectorImpl.JitLimitation;
import com.google.inject.spi.Dependency;

/**
 * Delegates to a custom factory which is also bound in the injector.
 */
final class BoundProviderFactory<T> implements InternalFactory<T>, CreationListener {

  private final InjectorImpl injector;
  final Key<? extends javax.inject.Provider<? extends T>> providerKey;
  final Object source;
  private InternalFactory<? extends javax.inject.Provider<? extends T>> providerFactory;

  BoundProviderFactory(
      InjectorImpl injector,
      Key<? extends javax.inject.Provider<? extends T>> providerKey,
      Object source) {
    this.injector = injector;
    this.providerKey = providerKey;
    this.source = source;
  }

  public void notify(Errors errors) {
    try {
      providerFactory = injector.getInternalFactory(providerKey, errors.withSource(source), JitLimitation.NEW_OR_EXISTING_JIT);
    } catch (ErrorsException e) {
      errors.merge(e.getErrors());
    }
  }

  public T get(Errors errors, InternalContext context, Dependency<?> dependency, boolean linked)
      throws ErrorsException {
    errors = errors.withSource(providerKey);
    javax.inject.Provider<? extends T> provider = providerFactory.get(errors, context, dependency, true);
    try {
      return errors.checkForNull(provider.get(), source, dependency);
    } catch(RuntimeException userException) {
      throw errors.errorInProvider(userException).toException();
    }
  }

  @Override public String toString() {
    return providerKey.toString();
  }
}
