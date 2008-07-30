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

import com.google.inject.Binding.TargetVisitor;
import com.google.inject.Key;
import com.google.inject.Provider;
import java.lang.reflect.Constructor;

public class DefaultBindTargetVisitor<T, V> implements TargetVisitor<T, V> {

  protected V visitTarget() {
    return null;
  }

  public V visitInstance(T instance) {
    return visitTarget();
  }

  public V visitProvider(Provider<? extends T> provider) {
    return visitTarget();
  }

  public V visitProviderKey(Key<? extends Provider<? extends T>> providerKey) {
    return visitTarget();
  }

  public V visitKey(Key<? extends T> key) {
    return visitTarget();
  }

  public V visitUntargetted() {
    return visitTarget();
  }

  public V visitConstructor(Constructor<? extends T> constructor) {
    return visitTarget();
  }

  public V visitConstant(T value) {
    return visitTarget();
  }

  public V visitConvertedConstant(T value) {
    return visitTarget();
  }

  public V visitProviderBinding(Key<?> provided) {
    return visitTarget();
  }
}
