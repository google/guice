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

import com.google.inject.introspect.Resolver;
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
   * Ignores the presence or absence of an injectable constructor.
   *
   * <p>Whenever Guice creates an instance, it performs this injection
   * automatically (after first performing constructor injection), so if you're
   * able to let Guice create all your objects for you, you'll never need to
   * use this method.
   */
  void injectMembers(Object o);

  /**
   * Gets all explicit bindings.  This method is part of the Injector
   * Introspection API and is primarily intended for use by tools.
   */
  Map<Key<?>, Binding<?>> getBindings();

  /**
   * Gets a binding for the given key, or null if no binding for this key is
   * found.  Note that if this key references an implementation class that can
   * be implicitly bound, this method may return null, but may later return the
   * implicit binding after it has been loaded. This method is part of the
   * Injector Introspection API and is primarily intended for use by tools.
   */
  <T> Binding<T> getBinding(Key<T> key);

  /**
   * Finds all bindings to the given type. This method is part of the Injector
   * Introspection API and is primarily intended for use by tools.
   */
  <T> List<Binding<T>> findBindingsByType(TypeLiteral<T> type);

  /**
   * Returns the provider used to obtain instances for the given injection key.
   * When feasible, it's generally preferable to avoid using this method, in
   * favor of having Guice inject your dependencies ahead of time.
   */
  <T> Provider<T> getProvider(Key<T> key);

  /**
   * Returns the provider used to obtain instances for the given injection key.
   * When feasible, it's generally preferable to avoid using this method, in
   * favor of having Guice inject your dependencies ahead of time.
   */
  <T> Provider<T> getProvider(Class<T> type);

  /**
   * Returns the appropriate instance for the given injection key; equivalent to
   * {@code getProvider(key).get()}. When feasible, it's generally preferable to
   * avoid using this method, in favor of having Guice inject your dependencies
   * ahead of time.
   */
  <T> T getInstance(Key<T> key);

  /**
   * Returns the appropriate instance for the given type; equivalent to
   * {@code getProvider(type).get()}. When feasible, it's generally preferable
   * to avoid using this method, in favor of having Guice inject your
   * dependencies ahead of time.
   */
  <T> T getInstance(Class<T> type);

  /**
   * Returns the resolver used by this injector to resolve injection requests.
   * This method is part of the Injector Introspection API and is primarily
   * intended for use by tools.
   */
  Resolver getResolver();
}
