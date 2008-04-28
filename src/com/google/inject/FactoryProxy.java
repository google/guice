/**
 * Copyright (C) 2008 Google Inc.
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

import com.google.inject.internal.ResolveFailedException;
import com.google.inject.internal.ToStringBuilder;

/**
 * A placeholder which enables us to swap in the real factory once the
 * container is created.
 */
class FactoryProxy<T> implements InternalFactory<T>,
    BindCommandProcessor.CreationListener {

  private final Key<T> key;
  private final Key<? extends T> targetKey;
  private final Object source;

  InternalFactory<? extends T> targetFactory;

  FactoryProxy(Key<T> key, Key<? extends T> targetKey, Object source) {
    this.key = key;
    this.targetKey = targetKey;
    this.source = source;
  }

  public void notify(final InjectorImpl injector) {
    injector.withDefaultSource(source, new Runnable() {
      public void run() {
        try {
          targetFactory = injector.getInternalFactory(targetKey);
        } catch (ResolveFailedException e) {
          injector.errorHandler.handle(source, e.getMessage());
        }
      }
    });
  }

  public T get(InternalContext context, InjectionPoint<?> injectionPoint) {
    return targetFactory.get(context, injectionPoint);
  }

  public String toString() {
    return new ToStringBuilder(FactoryProxy.class)
        .add("key", key)
        .add("provider", targetFactory)
        .toString();
  }
}
