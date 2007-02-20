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

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;

/**
 * Injects dependencies into constructors, methods and fields annotated with
 * {@code @}{@link Inject}. Provides access to {@link Binding}s.
 *
 * <p>Automatically converts constants as needed from {@code String} to any
 * primitive type as well as {@code enum} and {@code Class<?>}. Automatically
 * boxes and unboxes primitives. For example, in the absence of a binding to
 * {@code int}, the container will look for a binding to {@code Integer}.
 *
 * @author crazybob@google.com (Bob Lee)
 * @see BinderImpl
 */
public interface Container {

  /**
   * Injects dependencies into the fields and methods of an existing object.
   */
  void injectMembers(Object o);

  /**
   * Gets all bindings.
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
   * Gets an instance from the locator bound to the given type.
   */
  <T> T getInstance(TypeLiteral<T> type);

  /**
   * Gets an instance from the locator bound to the given type.
   */
  <T> T getInstance(Class<T> type);

  /**
   * Gets an instance from the locator bound to the given key.
   */
  <T> T getInstance(Key<T> key);

  /**
   * Gets an instance from the locator bound to the given type and annotation.
   */
  <T> T getInstance(TypeLiteral<T> type, Annotation annotation);

  /**
   * Gets an instance from the locator bound to the given type and annotation.
   */
  <T> T getInstance(Class<T> type, Annotation annotation);

  /**
   * Gets an instance from the locator bound to the given type and annotation.
   */
  <T> T getInstance(TypeLiteral<T> type,
      Class<? extends Annotation> annotationType);

  /**
   * Gets an instance from the locator bound to the given type and annotation.
   */
  <T> T getInstance(Class<T> type,
      Class<? extends Annotation> annotationType);

  /**
   * Gets the locator bound to the given key.
   */
  <T> Locator<T> getLocator(Key<T> key);

  /**
   * Gets the locator bound to the given type.
   */
  <T> Locator<T> getLocator(Class<T> type);

  /**
   * Gets the locator bound to the given type.
   */
  <T> Locator<T> getLocator(TypeLiteral<T> type);

  /**
   * Gets the locator bound to the given type and annotation.
   */
  <T> Locator<T> getLocator(Class<T> type, Annotation annotation);

  /**
   * Gets the locator bound to the given type and annotation.
   */
  <T> Locator<T> getLocator(TypeLiteral<T> type, Annotation annotation);

  /**
   * Gets the locator bound to the given type and annotation.
   */
  <T> Locator<T> getLocator(Class<T> type,
      Class<? extends Annotation> annotationType);

  /**
   * Gets the locator bound to the given type and annotation.
   */
  <T> Locator<T> getLocator(TypeLiteral<T> type,
      Class<? extends Annotation> annotationType);
}
