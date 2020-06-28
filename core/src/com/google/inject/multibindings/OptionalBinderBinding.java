/*
 * Copyright (C) 2014 Google Inc.
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

package com.google.inject.multibindings;

import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import java.util.Set;

/**
 * A binding for a OptionalBinder.
 *
 * <p>Although OptionalBinders may be injected through a variety of types {@code V}, {@code
 * Optional<V>}, {@code Optional<Provider<V>>}, etc..), an OptionalBinderBinding exists only on the
 * Binding associated with the {@code Optional<V>} key. Injectable types can be discovered using
 * {@link #getKey} (which will return the {@code Optional<V>} key), or{@link #getAlternateKeys}
 * (which will return the other keys that can inject this data). Other bindings can be validated to
 * be derived from this OptionalBinderBinding using {@link #containsElement}.
 *
 * @param <T> The fully qualified type of the optional binding, including Optional. For example:
 *     {@code Optional<String>}.
 * @since 4.0
 * @author sameb@google.com (Sam Berlin)
 */
public interface OptionalBinderBinding<T> {

  /** Returns the {@link Key} for this binding. */
  Key<T> getKey();

  /**
   * Returns the keys of other bindings that represent this OptionalBinder. This will return an
   * entry for {@code Optional<com.google.inject.Provider<V>>} and {@code
   * Optional<javax.inject.Provider<V>>}.
   *
   * @since 4.2.3
   */
  Set<Key<?>> getAlternateKeys();

  /**
   * Returns the default binding (set by {@link OptionalBinder#setDefault}) if one exists or null if
   * no default binding is set. This will throw {@link UnsupportedOperationException} if it is
   * called on an element retrieved from {@link Elements#getElements}.
   *
   * <p>The Binding's type will always match the type Optional's generic type. For example, if
   * getKey returns a key of <code>Optional&lt;String></code>, then this will always return a <code>
   * Binding&lt;String></code>.
   */
  Binding<?> getDefaultBinding();

  /**
   * Returns the actual binding (set by {@link OptionalBinder#setBinding}) or null if not set. This
   * will throw {@link UnsupportedOperationException} if it is called on an element retrieved from
   * {@link Elements#getElements}.
   *
   * <p>The Binding's type will always match the type Optional's generic type. For example, if
   * getKey returns a key of <code>Optional&lt;String></code>, then this will always return a <code>
   * Binding&lt;String></code>.
   */
  Binding<?> getActualBinding();

  /**
   * Returns true if this OptionalBinder contains the given Element in order to build the optional
   * binding or uses the given Element in order to support building and injecting its data. This
   * will work for OptionalBinderBinding retrieved from an injector and {@link
   * Elements#getElements}. Usually this is only necessary if you are working with elements
   * retrieved from modules (without an Injector), otherwise {@link #getDefaultBinding} and {@link
   * #getActualBinding} are better options.
   */
  boolean containsElement(Element element);
}
