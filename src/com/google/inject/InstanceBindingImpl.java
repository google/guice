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

import com.google.inject.internal.ErrorsException;
import com.google.inject.internal.ToStringBuilder;
import com.google.inject.spi.BindingVisitor;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.InstanceBinding;
import com.google.inject.util.Providers;
import java.util.Collection;

class InstanceBindingImpl<T> extends BindingImpl<T>
    implements InstanceBinding<T> {

  final T instance;
  final Provider<T> provider;

  InstanceBindingImpl(InjectorImpl injector, Key<T> key, Object source,
      InternalFactory<? extends T> internalFactory, T instance) {
    super(injector, key, source, internalFactory, Scopes.NO_SCOPE, LoadStrategy.EAGER);
    this.instance = instance;
    this.provider = Providers.of(instance);
  }

  @Override public Provider<T> getProvider() {
    return this.provider;
  }

  public void accept(BindingVisitor<? super T> bindingVisitor) {
    bindingVisitor.visit(this);
  }

  public T getInstance() {
    return this.instance;
  }

  public Collection<InjectionPoint<?>> getInjectionPoints() {
    try {
      return injector.getFieldAndMethodInjectionsFor(instance.getClass());
    }
    catch (ErrorsException e) {
      // this would have been a creation exception
      // TODO: initialize the dependencies via a callback
      throw new AssertionError(e);
    }
  }

  @Override public String toString() {
    return new ToStringBuilder(InstanceBinding.class)
        .add("key", key)
        .add("instance", instance)
        .add("source", source)
        .toString();
  }
}
