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

import com.google.inject.Key;
import com.google.inject.ProvidedBy;
import com.google.inject.internal.InjectorImpl.JitLimitation;
import com.google.inject.spi.Dependency;
import jakarta.inject.Provider;

/**
 * An {@link InternalFactory} for {@literal @}{@link ProvidedBy} bindings.
 *
 * @author sameb@google.com (Sam Berlin)
 */
class ProvidedByInternalFactory<T> extends ProviderInternalFactory<T> implements DelayedInitialize {

  private final Class<?> rawType;
  private final Class<? extends Provider<?>> providerType;
  private final Key<? extends Provider<T>> providerKey;
  private InternalFactory<? extends Provider<T>> providerFactory;
  private ProvisionListenerStackCallback<T> provisionCallback;

  ProvidedByInternalFactory(
      Class<?> rawType,
      Class<? extends Provider<?>> providerType,
      Key<? extends Provider<T>> providerKey,
      int circularFactoryId) {
    super(providerKey, circularFactoryId);
    this.rawType = rawType;
    this.providerType = providerType;
    this.providerKey = providerKey;
  }

  void setProvisionListenerCallback(ProvisionListenerStackCallback<T> listener) {
    provisionCallback = listener;
  }

  @Override
  public void initialize(InjectorImpl injector, Errors errors) throws ErrorsException {
    providerFactory =
        injector.getInternalFactory(providerKey, errors, JitLimitation.NEW_OR_EXISTING_JIT);
  }

  @Override
  public T get(InternalContext context, Dependency<?> dependency, boolean linked)
      throws InternalProvisionException {
    InternalFactory<? extends Provider<T>> localProviderFactory = providerFactory;
    if (localProviderFactory == null) {
      throw new IllegalStateException("not initialized");
    }
    try {
      Provider<? extends T> provider = localProviderFactory.get(context, dependency, true);
      return circularGet(provider, context, dependency, provisionCallback);
    } catch (InternalProvisionException ipe) {
      throw ipe.addSource(providerKey);
    }
  }

  @Override
  protected T provision(
      jakarta.inject.Provider<? extends T> provider,
      InternalContext context,
      Dependency<?> dependency)
      throws InternalProvisionException {
    Object o = super.provision(provider, context, dependency);
    if (o != null && !rawType.isInstance(o)) {
      throw InternalProvisionException.subtypeNotProvided(providerType, rawType).addSource(source);
    }
    @SuppressWarnings("unchecked") // protected by isInstance() check above
    T t = (T) o;
    return t;
  }
}
