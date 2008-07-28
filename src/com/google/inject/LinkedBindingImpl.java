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
import com.google.inject.spi.oldversion.BindingVisitor;
import com.google.inject.spi.oldversion.LinkedBinding;
import com.google.inject.spi.oldversion.OldVersionBinding;

/**
 *
 *
 */
class LinkedBindingImpl<T> extends BindingImpl<T>
    implements LinkedBinding<T> {

  final Key<? extends T> targetKey;

  LinkedBindingImpl(InjectorImpl injector, Key<T> key, Object source,
      InternalFactory<? extends T> internalFactory, Scope scope,
      Key<? extends T> targetKey,
      LoadStrategy loadStrategy) {
    super(injector, key, source, internalFactory, scope, loadStrategy);
    this.targetKey = targetKey;
  }

  public void accept(BindingVisitor<? super T> bindingVisitor) {
    bindingVisitor.visit(this);
  }

  public <V> V acceptTargetVisitor(TargetVisitor<? super T, V> visitor) {
    return visitor.visitToKey(targetKey);
  }

  public OldVersionBinding<? extends T> getTargetBinding() {
    return injector.getBinding(targetKey);
  }

  @Override public String toString() {
    return new ToStringBuilder(LinkedBinding.class)
        .add("key", key)
        .add("target", targetKey)
        .add("scope", scope)
        .add("source", source)
        .toString();
  }
}
