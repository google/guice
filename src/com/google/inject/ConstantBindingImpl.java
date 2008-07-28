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

import com.google.inject.internal.ToStringBuilder;
import com.google.inject.spi.oldversion.BindingVisitor;
import com.google.inject.spi.oldversion.ConstantBinding;
import com.google.inject.util.Providers;

/**
 * A constant binding.
 */
class ConstantBindingImpl<T> extends BindingImpl<T>
    implements ConstantBinding<T> {

  final T value;
  final Provider<T> provider;

  ConstantBindingImpl(InjectorImpl injector, Key<T> key, Object source,
      InternalFactory<T> internalFactory, T value) {
    super(injector, key, source, internalFactory, Scopes.NO_SCOPE, LoadStrategy.LAZY);
    this.value = value;
    this.provider = Providers.of(value);
  }

  @Override public Provider<T> getProvider() {
    return this.provider;
  }

  public <V> V acceptTargetVisitor(TargetVisitor<? super T, V> visitor) {
    return visitor.visitConstant(value);
  }

  public void accept(BindingVisitor<? super T> bindingVisitor) {
    bindingVisitor.visit(this);
  }

  public T getValue() {
    return this.value;
  }

  @Override public String toString() {
    return new ToStringBuilder(ConstantBinding.class)
        .add("key", key)
        .add("value", value)
        .add("source", source)
        .toString();
  }
}
