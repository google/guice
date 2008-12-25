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

  protected V visitOther(Binding<T> binding) {
    return null;
  }

  public V visitInstance(InstanceBinding<T> instanceBinding) {
    return visitOther(instanceBinding);
  }

  public V visitProviderInstance(ProviderInstanceBinding<T> providerInstanceBinding) {
    return visitOther(providerInstanceBinding);
  }

  public V visitProviderKey(ProviderKeyBinding<T> providerKeyBinding) {
    return visitOther(providerKeyBinding);
  }

  public V visitLinkedKey(LinkedKeyBinding<T> linkedKeyBinding) {
    return visitOther(linkedKeyBinding);
  }

  public V visitExposed(ExposedBinding<T> exposedBinding) {
    return visitOther(exposedBinding);
  }

  public V visitUntargetted(UntargettedBinding<T> untargettedBinding) {
    return visitOther(untargettedBinding);
  }

  public V visitConstructor(ConstructorBinding<T> constructorBinding) {
    return visitOther(constructorBinding);
  }

  public V visitConvertedConstant(ConvertedConstantBinding<T> convertedConstantBinding) {
    return visitOther(convertedConstantBinding);
  }

  @SuppressWarnings("unchecked") // if we visit a ProviderBinding, we know T == Provider<?>
  public V visitProviderBinding(ProviderBinding<?> providerBinding) {
    return visitOther((Binding<T>) providerBinding);
  }
}
