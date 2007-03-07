/**
 * Copyright (C) 2006 Google Inc.
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

import java.util.List;
import java.util.Map;

/**
 * Fulfills requests for the object instances that make up your application,
 * always ensuring that these instances are properly injected before they are
 * returned. The {@code Injector} is the heart of the Guice framework,
 * although you don't typically interact with it directly very often. This
 * "behind-the-scenes" operation is what distinguishes the dependency
 * injection pattern from its cousin, service locator.
 *
 * <p>The {@code Injector} API has a few additional features: it allows
 * pre-constructed instances to have their fields and methods injected and
 * offers programmatic introspection to support tool development.
 *
 * <p>Contains several default bindings:
 *
 * <ul>
 * <li>This {@link Injector} instance itself
 * <li>A {@code Provider<T>} for each binding of type {@code T}
 * <li>The {@link java.util.logging.Logger} for the class being injected
 * <li>The {@link Stage} in which the Injector was created
 * </ul>
 *
 * Injectors are created using the facade class {@link Guice}.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public interface Injector {

  /**
   * Injects dependencies into the fields and methods of an existing object.
   * Does not inject the constructor.
   */
  void injectMembers(Object o);

  /**
   * Gets all explicit bindings.
   */
  Map<Key<?>, Binding<?>> getBindings();

  /**
   * Gets a binding for the given key.
   */
  <T> Binding<T> getBinding(Key<T> key);

  /**
   * Finds all bindings to the given type.
   */
  <T> List<Binding<T>> findBindingsByType(TypeLiteral<T> type);

  /**
   * Gets the provider bound to the given key.
   */
  <T> Provider<T> getProvider(Key<T> key);

  /**
   * Gets the provider bound to the given type.
   */
  <T> Provider<T> getProvider(Class<T> type);

  /**
   * Gets an instance bound to the given key; equivalent to
   * {@code getProvider(key).get()}.
   */
  <T> T getInstance(Key<T> key);

  /**
   * Gets an instance bound to the given type; equivalent to
   * {@code getProvider(type).get()}.
   */
  <T> T getInstance(Class<T> type);
}
