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

import com.google.inject.internal.ToStringBuilder;
import com.google.inject.spi.BindingVisitor;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.ProviderInstanceBinding;

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
      Provider<? extends T> providerInstance) {
    super(injector, key, source, internalFactory, scope);
    this.providerInstance = providerInstance;
  }

  public void accept(BindingVisitor<? super T> bindingVisitor) {
    bindingVisitor.visit(this);
  }

  public Provider<? extends T> getProviderInstance() {
    return this.providerInstance;
  }

  public Collection<Dependency<?>> getDependencies() {
    return injector.getFieldAndMethodDependenciesFor(
        providerInstance.getClass());
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
