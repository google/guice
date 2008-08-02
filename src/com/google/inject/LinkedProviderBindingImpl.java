/*
 * Copyright (C) 2007 Google Inc.
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

import com.google.inject.internal.ToStringBuilder;
import com.google.inject.spi.BindTargetVisitor;
import com.google.inject.spi.oldversion.BindingVisitor;
import com.google.inject.spi.oldversion.LinkedProviderBinding;
import com.google.inject.spi.oldversion.OldVersionBinding;

/**
 *
 *
 */
class LinkedProviderBindingImpl<T> extends BindingImpl<T>
    implements LinkedProviderBinding<T> {

  final Key<? extends Provider<? extends T>> providerKey;

  LinkedProviderBindingImpl(InjectorImpl injector, Key<T> key, Object source,
      InternalFactory<? extends T> internalFactory, Scope scope,
      Key<? extends Provider<? extends T>> providerKey,
      LoadStrategy loadStrategy) {
    super(injector, key, source, internalFactory, scope, loadStrategy);
    this.providerKey = providerKey;
  }

  public void accept(BindingVisitor<? super T> bindingVisitor) {
    bindingVisitor.visit(this);
  }

  public OldVersionBinding<? extends Provider<? extends T>> getTargetProvider() {
    return injector.getBinding(providerKey);
  }

  public <V> V acceptTargetVisitor(BindTargetVisitor<? super T, V> visitor) {
    return visitor.visitProviderKey(providerKey);
  }

  @Override public String toString() {
    return new ToStringBuilder(LinkedProviderBinding.class)
        .add("key", key)
        .add("provider", providerKey)
        .add("scope", scope)
        .add("source", source)
        .toString();
  }
}
