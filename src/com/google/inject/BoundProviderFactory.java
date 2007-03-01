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

import com.google.inject.BinderImpl.CreationListener;

/**
 * Delegates to a custom factory which is also bound in the injector.
 */
class BoundProviderFactory<T>
    implements InternalFactory<T>, CreationListener {

  final Key<? extends Provider<? extends T>> providerKey;
  final Object source;
  private InternalFactory<? extends Provider<? extends T>> providerFactory;

  BoundProviderFactory(
      Key<? extends Provider<? extends T>> providerKey,
      Object source) {
    this.providerKey = providerKey;
    this.source = source;
  }

  BoundProviderFactory(
      Key<? extends Provider<? extends T>> providerKey,
      InternalFactory<? extends Provider<? extends T>> providerFactory,
      Object source) {
    this.providerKey = providerKey;
    this.providerFactory = providerFactory;
    this.source = source;
  }

  public void notify(final InjectorImpl injector) {
    injector.withDefaultSource(source, new Runnable() {
      public void run() {
        providerFactory = injector.getInternalFactory(null, providerKey);
      }
    });
  }

  public String toString() {
    return providerKey.toString();
  }

  public T get(InternalContext context) {
    return providerFactory.get(context).get();
  }
}
