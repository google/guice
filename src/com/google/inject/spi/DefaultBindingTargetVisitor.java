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
import java.util.Set;

/**
 * No-op visitor for subclassing. All interface methods simply delegate to
 * {@link #visitOther()}, returning its result.
 *
 * @param <V> any type to be returned by the visit method. Use {@link Void} with
 *     {@code return null} if no return type is needed.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 * @since 2.0
 */
public abstract class DefaultBindingTargetVisitor<T, V> implements BindingTargetVisitor<T, V> {

  protected V visitOther() {
    return null;
  }

  public V visitInstance(T instance, Set<InjectionPoint> injectionPoints) {
    return visitOther();
  }

  public V visitProvider(Provider<? extends T> provider, Set<InjectionPoint> injectionPoints) {
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

  public V visitConstructor(Constructor<? extends T> constructor,
      Set<InjectionPoint> injectionPoints) {
    return visitOther();
  }

  public V visitConvertedConstant(T value) {
    return visitOther();
  }

  public V visitProviderBinding(Key<?> provided) {
    return visitOther();
  }

  public V visitExposed(PrivateEnvironment privateEnvironment) {
    return visitOther();
  }
}
