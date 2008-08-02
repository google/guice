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

import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;
import com.google.inject.internal.ToStringBuilder;
import com.google.inject.spi.InjectionPoint;

/**
 * A placeholder which enables us to swap in the real factory once the
 * container is created.
 */
class FactoryProxy<T> implements InternalFactory<T>, BindingProcessor.CreationListener {

  private final Key<T> key;
  private final Key<? extends T> targetKey;
  private final Object source;

  private InternalFactory<? extends T> targetFactory;

  FactoryProxy(Key<T> key, Key<? extends T> targetKey, Object source) {
    this.key = key;
    this.targetKey = targetKey;
    this.source = source;
  }

  public void notify(final InjectorImpl injector, final Errors errors) {
    try {
      targetFactory = injector.getInternalFactory(targetKey, errors.withSource(source));
    } catch (ErrorsException e) {
      errors.merge(e.getErrors());
    }
  }

  public T get(Errors errors, InternalContext context, InjectionPoint<?> injectionPoint)
      throws ErrorsException {
    return targetFactory.get(errors, context, injectionPoint);
  }

  @Override public String toString() {
    return new ToStringBuilder(FactoryProxy.class)
        .add("key", key)
        .add("provider", targetFactory)
        .toString();
  }
}
