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

import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.Provider;
import java.lang.reflect.Constructor;
import java.util.Set;

/**
 * No-op visitor for subclassing. All interface methods simply delegate to {@link
 * #visitOther(Binding)}, returning its result.
 *
 * @param <V> any type to be returned by the visit method. Use {@link Void} with
 *     {@code return null} if no return type is needed.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 * @since 2.0
 */
public abstract class DefaultBindingTargetVisitor<T, V> implements BindingTargetVisitor<T, V> {

  @Deprecated protected V visitOther() {
    return null;
  }

  @Deprecated public V visitInstance(T instance, Set<InjectionPoint> injectionPoints) {
    return visitOther();
  }

  @Deprecated public V visitProvider(
      Provider<? extends T> provider, Set<InjectionPoint> injectionPoints) {
    return visitOther();
  }

  @Deprecated public V visitProviderKey(Key<? extends Provider<? extends T>> providerKey) {
    return visitOther();
  }

  @Deprecated public V visitKey(Key<? extends T> key) {
    return visitOther();
  }

  @Deprecated public V visitUntargetted() {
    return visitOther();
  }

  @Deprecated public V visitConstructor(Constructor<? extends T> constructor,
      Set<InjectionPoint> injectionPoints) {
    return visitOther();
  }

  @Deprecated public V visitConvertedConstant(T value) {
    return visitOther();
  }

  @Deprecated public V visitProviderBinding(Key<?> provided) {
    return visitOther();
  }

  @Deprecated public V visitExposed(PrivateElements privateElements) {
    return visitOther();
  }

  protected V visitOther(Binding<? extends T> binding) {
    return null;
  }

  public V visitInstance(InstanceBinding<? extends T> binding) {
    V result = visitInstance(binding.getInstance(), binding.getInjectionPoints());
    return result != null ? result : visitOther(binding);
  }

  public V visitProviderInstance(ProviderInstanceBinding<? extends T> binding) {
    V result = visitProvider(binding.getProviderInstance(), binding.getInjectionPoints());
    return result != null ? result : visitOther(binding);
  }

  public V visitProviderKey(ProviderKeyBinding<? extends T> binding) {
    V result = visitProviderKey(binding.getProviderKey());
    return result != null ? result : visitOther(binding);
  }

  public V visitLinkedKey(LinkedKeyBinding<? extends T> binding) {
    V result = visitKey(binding.getLinkedKey());
    return result != null ? result : visitOther(binding);
  }

  public V visitExposed(ExposedBinding<? extends T> binding) {
    V result = visitExposed(binding.getPrivateElements());
    return result != null ? result : visitOther(binding);
  }

  public V visitUntargetted(UntargettedBinding<? extends T> binding) {
    V result = visitUntargetted();
    return result != null ? result : visitOther(binding);
  }

  public V visitConstructor(ConstructorBinding<? extends T> binding) {
    V result = visitConstructor(binding.getConstructor(), binding.getInjectionPoints());
    return result != null ? result : visitOther(binding);
  }

  public V visitConvertedConstant(ConvertedConstantBinding<? extends T> binding) {
    V result = visitConvertedConstant(binding.getValue());
    return result != null ? result : visitOther(binding);
  }

   // javac says it's an error to cast ProviderBinding<? extends T> to Binding<? extends T>
  @SuppressWarnings("unchecked")
  public V visitProviderBinding(ProviderBinding<? extends T> binding) {
     V result = visitProviderBinding(binding.getProvidedKey());
     return result != null ? result : visitOther((Binding) binding);
  }
}
