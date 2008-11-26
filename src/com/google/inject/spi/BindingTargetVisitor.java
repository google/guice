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
   *
   * @param instance the user-supplied value
   * @param injectionPoints the field and method injection points of the instance, injected at
   *      injector-creation time only.
   */
  V visitInstance(T instance, Set<InjectionPoint> injectionPoints);

  /**
   * Visit a provider instance binding. The provider's {@code get} method is invoked to resolve
   * injections. This target is found in both module and injector bindings.
   *
   * @param provider the user-supplied, unscoped provider
   * @param injectionPoints the field and method injection points of the provider, injected at
   *      injector-creation time only.
   */
  V visitProvider(Provider<? extends T> provider, Set<InjectionPoint> injectionPoints);

  /**
   * Visit a provider key binding. To resolve injections, the provider injection is first
   * resolved, then that provider's {@code get} method is invoked. This target is found in both
   * module and injector bindings.
   *
   * @param providerKey the key used to resolve the provider's binding. That binding can be
   *      retrieved from an injector using {@link com.google.inject.Injector#getBinding(Key)
   *      Injector.getBinding(providerKey)}
   */
  V visitProviderKey(Key<? extends Provider<? extends T>> providerKey);

  /**
   * Visit a linked key binding. The other key's binding is used to resolve injections. This
   * target is found in both module and injector bindings.
   *
   * @param key the linked key used to resolve injections. That binding can be retrieved from an
   *      injector using {@link com.google.inject.Injector#getBinding(Key) Injector.getBinding(key)}
   */
  V visitKey(Key<? extends T> key);

  /**
   * Visit an untargetted binding. This target is found only on module bindings. It indicates
   * that the injector should use its implicit binding strategies to resolve injections.
   */
  V visitUntargetted();

  /**
   * Visit a constructor binding. To resolve injections, an instance is instantiated by invoking
   * {@code constructor}. This target is found only on injector bindings.
   *
   * @param constructor the {@link com.google.inject.Inject annotated} or default constructor that
   *      is invoked for creating values
   * @param injectionPoints the constructor, field and method injection points to create and
   *      populate a new instance. The set contains exactly one constructor injection point.
   */
  V visitConstructor(Constructor<? extends T> constructor, Set<InjectionPoint> injectionPoints);

  /**
   * Visit a binding created from converting a bound instance to a new type. The source binding
   * has the same binding annotation but a different type. This target is found only on injector
   * bindings.
   *
   * @param value the converted value
   */
  V visitConvertedConstant(T value);

  /**
   * Visit a binding to a {@link com.google.inject.Provider} that delegates to the binding for the
   * provided type. This target is found only on injector bindings.
   *
   * @param provided the key whose binding is used to {@link com.google.inject.Provider#get provide
   *      instances}. That binding can be retrieved from an injector using {@link
   *      com.google.inject.Injector#getBinding(Key) Injector.getBinding(provided)}
   */
  V visitProviderBinding(Key<?> provided);

  /**
   * Visit a binding to a key exposed by a private environment.
   */
  V visitExposed(PrivateEnvironment privateEnvironment);
}
