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

import static com.google.common.base.Preconditions.checkState;

import com.google.inject.Key;
import com.google.inject.ProvidedBy;
import com.google.inject.Provider;
import com.google.inject.internal.InjectorImpl.JitLimitation;
import com.google.inject.spi.Dependency;

/**
 * An {@link InternalFactory} for {@literal @}{@link ProvidedBy} bindings.
 * 
 * @author sameb@google.com (Sam Berlin)
 */
class ProvidedByInternalFactory<T> extends ProviderInternalFactory<T>
    implements DelayedInitialize {
  
  private final Class<?> rawType;
  private final Class<? extends Provider<?>> providerType;
  private final Key<? extends Provider<T>> providerKey;
  private BindingImpl<? extends Provider<T>> providerBinding;
  private ProvisionListenerStackCallback<T> provisionCallback;
  
  ProvidedByInternalFactory(
      Class<?> rawType,
      Class<? extends Provider<?>> providerType,
      Key<? extends Provider<T>> providerKey) {
    super(providerKey);
    this.rawType = rawType;
    this.providerType = providerType; 
    this.providerKey = providerKey;
  }
  
  void setProvisionListenerCallback(ProvisionListenerStackCallback<T> listener) {
    provisionCallback = listener;
  }
  
  public void initialize(InjectorImpl injector, Errors errors) throws ErrorsException {
    providerBinding =
        injector.getBindingOrThrow(providerKey, errors, JitLimitation.NEW_OR_EXISTING_JIT);
  }

  public T get(Errors errors, InternalContext context, Dependency dependency, boolean linked)
      throws ErrorsException {
    checkState(providerBinding != null, "not initialized");
    
    context.pushState(providerKey, providerBinding.getSource());
    try {
      errors = errors.withSource(providerKey);
      Provider<? extends T> provider = providerBinding.getInternalFactory().get(
          errors, context, dependency, true);
      return circularGet(provider, errors, context, dependency, provisionCallback);
    } finally {
      context.popState();
    }
  }
  
  @Override
  protected T provision(javax.inject.Provider<? extends T> provider, Errors errors,
      Dependency<?> dependency, ConstructionContext<T> constructionContext)
      throws ErrorsException {
    try {
      Object o = super.provision(provider, errors, dependency, constructionContext);
      if (o != null && !rawType.isInstance(o)) {
        throw errors.subtypeNotProvided(providerType, rawType).toException();
      }
      @SuppressWarnings("unchecked") // protected by isInstance() check above
      T t = (T) o;
      return t;
    } catch (RuntimeException e) {
      throw errors.errorInProvider(e).toException();
    }
  }
}
