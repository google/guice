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

import com.google.inject.Key;
import com.google.inject.internal.InjectorImpl.JitLimitation;
import com.google.inject.spi.Dependency;

/** Delegates to a custom factory which is also bound in the injector. */
final class BoundProviderFactory<T> extends ProviderInternalFactory<T> implements CreationListener {

  private final ProvisionListenerStackCallback<T> provisionCallback;
  private final InjectorImpl injector;
  final Key<? extends jakarta.inject.Provider<? extends T>> providerKey;
  private InternalFactory<? extends jakarta.inject.Provider<? extends T>> providerFactory;

  BoundProviderFactory(
      Class<? super T> rawType,
      InjectorImpl injector,
      Key<? extends jakarta.inject.Provider<? extends T>> providerKey,
      Object source,
      ProvisionListenerStackCallback<T> provisionCallback) {
    super(rawType, source, injector.circularFactoryIdFactory.next());
    this.provisionCallback = provisionCallback;
    this.injector = injector;
    this.providerKey = providerKey;
  }

  @Override
  public void notify(Errors errors) {
    try {
      providerFactory =
          injector.getInternalFactory(
              providerKey, errors.withSource(source), JitLimitation.NEW_OR_EXISTING_JIT);
    } catch (ErrorsException e) {
      errors.merge(e.getErrors());
    }
  }

  @Override
  public T get(InternalContext context, Dependency<?> dependency, boolean linked)
      throws InternalProvisionException {
    try {
      // TODO: lukes - are we passing the right dependency here?
      jakarta.inject.Provider<? extends T> provider = providerFactory.get(context, dependency, true);
      return circularGet(provider, context, dependency, provisionCallback);
    } catch (InternalProvisionException ipe) {
      throw ipe.addSource(providerKey);
    }
  }

  @Override
  MethodHandleResult makeHandle(LinkageContext context, boolean linked) {
    return makeCachable(
        InternalMethodHandles.catchInternalProvisionExceptionAndRethrowWithSource(
            circularGetHandle(
                providerFactory.getHandle(context, /* linked= */ true), provisionCallback),
            providerKey));
  }

  @Override
  public String toString() {
    return providerKey.toString();
  }
}
