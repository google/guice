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

package com.google.inject;

import com.google.inject.BindCommandProcessor.CreationListener;
import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;
import com.google.inject.spi.InjectionPoint;

/**
 * Delegates to a custom factory which is also bound in the injector.
 */
class BoundProviderFactory<T> implements InternalFactory<T>, CreationListener {

  final Key<? extends Provider<? extends T>> providerKey;
  final Object source;
  private InternalFactory<? extends Provider<? extends T>> providerFactory;

  BoundProviderFactory(
      Key<? extends Provider<? extends T>> providerKey,
      Object source) {
    this.providerKey = providerKey;
    this.source = source;
  }

  public void notify(final InjectorImpl injector, final Errors errors) {
    errors.pushSource(source);
    try {
      providerFactory = injector.getInternalFactory(providerKey, errors);
    } catch (ErrorsException e) {
      errors.merge(e.getErrors());
    } finally {
      errors.popSource(source);
    }
  }

  public T get(Errors errors, InternalContext context, InjectionPoint<?> injectionPoint)
      throws ErrorsException {
    Provider<? extends T> provider = providerFactory.get(errors, context, injectionPoint);
    try {
      return injectionPoint.checkForNull(errors, provider.get(), source);
    } catch(RuntimeException userException) {
      Errors userErrors = ProvisionException.getErrors(userException);
      throw errors.errorInProvider(userException, userErrors).toException();
    }
  }

  @Override public String toString() {
    return providerKey.toString();
  }
}
