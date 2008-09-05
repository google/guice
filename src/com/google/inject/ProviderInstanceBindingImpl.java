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

import com.google.common.collect.ImmutableSet;
import com.google.inject.internal.ToStringBuilder;
import com.google.inject.spi.BindingTargetVisitor;
import com.google.inject.spi.InjectionPoint;
import java.util.Set;

/**
 *
 */
class ProviderInstanceBindingImpl<T> extends BindingImpl<T> {

  final Provider<? extends T> providerInstance;
  final ImmutableSet<InjectionPoint> injectionPoints;

  ProviderInstanceBindingImpl(InjectorImpl injector, Key<T> key,
      Object source,
      InternalFactory<? extends T> internalFactory, Scope scope,
      Provider<? extends T> providerInstance,
      LoadStrategy loadStrategy,
      Set<InjectionPoint> injectionPoints) {
    super(injector, key, source, internalFactory, scope, loadStrategy);
    this.providerInstance = providerInstance;
    this.injectionPoints = ImmutableSet.copyOf(injectionPoints);
  }

  public <V> V acceptTargetVisitor(BindingTargetVisitor<? super T, V> visitor) {
    return visitor.visitProvider(providerInstance, injectionPoints);
  }

  @Override
  public String toString() {
    return new ToStringBuilder(Binding.class)
        .add("key", key)
        .add("provider", providerInstance)
        .add("scope", scope)
        .add("source", source)
        .toString();
  }
}
