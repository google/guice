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

package com.google.inject.visitable;

import com.google.inject.binder.ScopedBindingBuilder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.binder.ConstantBindingBuilder;
import com.google.inject.Provider;
import com.google.inject.Key;


/**
 * A binding target, which provides instances from a specific key. 
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public interface Target<T> {

  /**
   * Execute this target against the linked binding builder.
   */
  ScopedBindingBuilder execute(LinkedBindingBuilder<T> linkedBindingBuilder);

  /**
   * Execute this target against the constant binding builder.
   */
  void execute(ConstantBindingBuilder builder);

  /**
   * Returns the bound instance, if it exists, or {@code defaultValue}
   * if no bound value exists.
   */
  T get(T defaultValue);

  Provider<? extends T> getProvider(Provider<? extends T> defaultValue);

  Key<? extends Provider<? extends T>> getProviderKey(Key<Provider<? extends T>> defaultValue);

  Key<? extends T> getKey(Key<? extends T> defaultValue);
}
