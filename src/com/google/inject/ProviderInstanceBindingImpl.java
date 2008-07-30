/*
Copyright (C) 2007 Google Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.google.inject;

import com.google.inject.internal.ErrorsException;
import com.google.inject.internal.ToStringBuilder;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.oldversion.BindingVisitor;
import com.google.inject.spi.oldversion.ProviderInstanceBinding;
import java.util.Collection;

/**
 *
 */
class ProviderInstanceBindingImpl<T> extends BindingImpl<T>
    implements ProviderInstanceBinding<T> {

  final Provider<? extends T> providerInstance;

  ProviderInstanceBindingImpl(InjectorImpl injector, Key<T> key,
      Object source,
      InternalFactory<? extends T> internalFactory, Scope scope,
      Provider<? extends T> providerInstance,
      LoadStrategy loadStrategy) {
    super(injector, key, source, internalFactory, scope, loadStrategy);
    this.providerInstance = providerInstance;
  }

  public void accept(BindingVisitor<? super T> bindingVisitor) {
    bindingVisitor.visit(this);
  }

  public <V> V acceptTargetVisitor(TargetVisitor<? super T, V> visitor) {
    return visitor.visitProvider(providerInstance);
  }

  public Provider<? extends T> getProviderInstance() {
    return this.providerInstance;
  }

  public Collection<InjectionPoint<?>> getInjectionPoints() {
    try {
      return injector.getFieldAndMethodInjectionsFor(providerInstance.getClass());
    }
    catch (ErrorsException e) {
      // this would have been a creation exception
      // TODO: initialize the dependencies via a callback
      throw new AssertionError(e);
    }
  }

  @Override
  public String toString() {
    return new ToStringBuilder(ProviderInstanceBinding.class)
        .add("key", key)
        .add("provider", providerInstance)
        .add("scope", scope)
        .add("source", source)
        .toString();
  }
}
