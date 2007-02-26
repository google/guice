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
 * Injects dependencies into constructors, methods and fields annotated with
 * {@code @}{@link Inject}. Iterates explicit {@link Binding}s.
 *
 * <p>Automatically converts constant values as needed from {@code String} to
 * any primitive type in addition to {@code enum} and {@code Class<?>}.
 * Automatically boxes and unboxes primitives. For example, in the absence of
 * a binding to {@code int}, the container will look for a binding to {@code
 * Integer}.
 *
 * <p>Contains several default bindings:
 *
 * <ul>
 * <li>This {@link Container}
 * <li>A {@code Provider<T>} for each binding of type {@code T}
 * <li>The {@link java.util.logging.Logger} for the class being injected
 * <li>The {@link Stage} specified when this container was created
 * </ul>
 *
 * @author crazybob@google.com (Bob Lee)
 * @see Guice
 */
public interface Container {

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
}
