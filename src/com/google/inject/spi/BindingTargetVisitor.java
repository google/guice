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

/**
 * Visits each of the strategies used to find an instance to satisfy an injection.
 *
 * @param <V> any type to be returned by the visit method. Use {@link Void} with
 *     {@code return null} if no return type is needed.
 * @since 2.0
 */
public interface BindingTargetVisitor<T, V> {

  /**
   * Visit a instance binding. The same instance is returned for every injection. This target is
   * found in both module and injector bindings.
   */
  V visitInstance(InstanceBinding<T> binding);

  /**
   * Visit a provider instance binding. The provider's {@code get} method is invoked to resolve
   * injections. This target is found in both module and injector bindings.
   */
  V visitProviderInstance(ProviderInstanceBinding<T> binding);

  /**
   * Visit a provider key binding. To resolve injections, the provider key is first resolved, then
   * that provider's {@code get} method is invoked. This target is found in both module and injector
   * bindings.
   */
  V visitProviderKey(ProviderKeyBinding<T> binding);

  /**
   * Visit a linked key binding. The other key's binding is used to resolve injections. This
   * target is found in both module and injector bindings.
   */
  V visitLinkedKey(LinkedKeyBinding<T> binding);

  /**
   * Visit a binding to a key exposed from an enclosed private environment. This target is found in
   * both module and injector bindings.
   */
  V visitExposed(ExposedBinding<T> binding);

  /**
   * Visit an untargetted binding. This target is found only on module bindings. It indicates
   * that the injector should use its implicit binding strategies to resolve injections.
   */
  V visitUntargetted(UntargettedBinding<T> binding);

  /**
   * Visit a constructor binding. To resolve injections, an instance is instantiated by invoking
   * {@code constructor}. This target is found only on injector bindings.
   */
  V visitConstructor(ConstructorBinding<T> binding);

  /**
   * Visit a binding created from converting a bound instance to a new type. The source binding
   * has the same binding annotation but a different type. This target is found only on injector
   * bindings.
   */
  V visitConvertedConstant(ConvertedConstantBinding<T> binding);

  /**
   * Visit a binding to a {@link com.google.inject.Provider} that delegates to the binding for the
   * provided type. This target is found only on injector bindings.
   */
  V visitProviderBinding(ProviderBinding<?> binding);
}
