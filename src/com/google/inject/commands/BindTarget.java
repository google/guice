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

package com.google.inject.commands;

import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.binder.ConstantBindingBuilder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.binder.ScopedBindingBuilder;


/**
 * A binding target, which provides instances from a specific key. 
 *
 * @deprecated replaced with {@link com.google.inject.Binding.TargetVisitor}
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
@Deprecated
public interface BindTarget<T> {

  /**
   * Execute this target against the linked binding builder.
   */
  ScopedBindingBuilder execute(LinkedBindingBuilder<T> linkedBindingBuilder);

  /**
   * Execute this target against the constant binding builder.
   */
  void execute(ConstantBindingBuilder builder);

  /**
   * Returns the bound instance, if it exists, or {@code null} if no bound value exists.
   */
  T get();

  Provider<? extends T> getProvider();

  Key<? extends Provider<? extends T>> getProviderKey();

  Key<? extends T> getKey();

  <V> V acceptVisitor(Visitor<T, V> visitor);

  interface Visitor<T, V> {
    V visitToInstance(T instance);
    V visitToProvider(Provider<? extends T> provider);
    V visitToProviderKey(Key<? extends Provider<? extends T>> providerKey);
    V visitToKey(Key<? extends T> key);
    V visitUntargetted();
  }
}
