/**
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
import com.google.inject.spi.ProviderInstanceBinding;

/**
 * Adapts {@link ProviderInstanceBinding} providers, ensuring circular proxies
 * fail (or proxy) properly.
 * 
 * @author sameb@google.com (Sam Berlin)
*/
final class InternalFactoryToInitializableAdapter<T> extends ProviderInternalFactory<T> {

  private final ProvisionListenerStackCallback<T> provisionCallback;
  private final Initializable<? extends javax.inject.Provider<? extends T>> initializable;

  public InternalFactoryToInitializableAdapter(
      Initializable<? extends javax.inject.Provider<? extends T>> initializable,
      Object source, boolean allowProxy,
      ProvisionListenerStackCallback<T> provisionCallback) {
    super(source, allowProxy);
    this.provisionCallback = checkNotNull(provisionCallback, "provisionCallback");
    this.initializable = checkNotNull(initializable, "provider");
  }

  public T get(Errors errors, InternalContext context, Dependency<?> dependency, boolean linked)
      throws ErrorsException {
    return circularGet(initializable.get(errors), errors, context, dependency, linked,
        provisionCallback);
  }
  
  @Override
  protected T provision(javax.inject.Provider<? extends T> provider, Errors errors,
      Dependency<?> dependency, ConstructionContext<T> constructionContext) throws ErrorsException {
    try {
      return super.provision(provider, errors, dependency, constructionContext);
    } catch(RuntimeException userException) {
      throw errors.withSource(source).errorInProvider(userException).toException();
    }
  }

  @Override public String toString() {
    return initializable.toString();
  }
}
