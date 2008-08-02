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

package com.google.inject.spi;

import com.google.inject.Key;
import com.google.inject.Provider;
import java.lang.reflect.Constructor;

/**
 * No-op visitor for subclassing.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class DefaultBindTargetVisitor<T, V> implements BindTargetVisitor<T, V> {

  protected V visitOther() {
    return null;
  }

  public V visitInstance(T instance) {
    return visitOther();
  }

  public V visitProvider(Provider<? extends T> provider) {
    return visitOther();
  }

  public V visitProviderKey(Key<? extends Provider<? extends T>> providerKey) {
    return visitOther();
  }

  public V visitKey(Key<? extends T> key) {
    return visitOther();
  }

  public V visitUntargetted() {
    return visitOther();
  }

  public V visitConstructor(Constructor<? extends T> constructor) {
    return visitOther();
  }

  public V visitConvertedConstant(T value) {
    return visitOther();
  }

  public V visitProviderBinding(Key<?> provided) {
    return visitOther();
  }
}
