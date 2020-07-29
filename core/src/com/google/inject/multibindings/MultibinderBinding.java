/*
 * Copyright (C) 2010 Google Inc.
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
import com.google.inject.TypeLiteral;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import java.util.List;
import java.util.Set;

/**
 * A binding for a Multibinder.
 *
 * <p>Although Multibinders may be injected through a variety of generic types ({@code Set<V>},
 * {@code Collection<Provider<V>>}, and {@code Set<? extends V>} ), a MultibinderBinding exists only
 * on the Binding associated with the {@code Set<V>} key. Injectable types can be discovered using
 * {@link #getSetKey} (which will return the {@code Set<V>} key), or {@link #getAlternateSetKeys}
 * (which will return the other keys that can inject this data). Other bindings can be validated to
 * be derived from this MultibinderBinding using {@link #containsElement(Element)}.
 *
 * @param <T> The fully qualified type of the set, including Set. For example: {@code
 *     MultibinderBinding<Set<Boolean>>}
 * @since 3.0
 * @author sameb@google.com (Sam Berlin)
 */
public interface MultibinderBinding<T> {

  /** Returns the key for the set. */
  Key<T> getSetKey();

  /**
   * Returns the keys of other bindings that represent this set. This will return an entry for
   * {@code Collection<com.google.inject.Provider<V>>}, {@code
   * Collection<javax.inject.Provider<V>>}, and {@code Set<? extends V>}.
   *
   * @since 4.2.3
   */
  Set<Key<?>> getAlternateSetKeys();

  /**
   * Returns the TypeLiteral that describes the type of elements in the set.
   *
   * <p>The elements will always match the type Set's generic type. For example, if getSetKey
   * returns a key of <code>Set&lt;String></code>, then this will always return a <code>
   * TypeLiteral&lt;String></code>.
   */
  TypeLiteral<?> getElementTypeLiteral();

  /**
   * Returns all bindings that make up the set. This is only supported on bindings returned from an
   * injector. This will throw {@link UnsupportedOperationException} if it is called on an element
   * retrieved from {@link Elements#getElements}.
   *
   * <p>The elements will always match the type Set's generic type. For example, if getSetKey
   * returns a key of <code>Set&lt;String></code>, then this will always return a list of type
   * <code>List&lt;Binding&lt;String>></code>.
   */
  List<Binding<?>> getElements();

  /**
   * Returns true if the multibinder permits duplicates. This is only supported on bindings returned
   * from an injector. This will throw {@link UnsupportedOperationException} if it is called on a
   * MultibinderBinding retrieved from {@link Elements#getElements}.
   */
  boolean permitsDuplicates();

  /**
   * Returns true if this Multibinder uses the given Element. This will be true for bindings that
   * derive the elements of the set and other bindings that Multibinder uses internally. This will
   * work for MultibinderBindings retrieved from an injector and {@link Elements#getElements}.
   * Usually this is only necessary if you are working with elements retrieved from modules (without
   * an Injector), otherwise {@link #getElements} and {@link #permitsDuplicates} are better options.
   *
   * <p>If you need to introspect the details of the set, such as the values or if it permits
   * duplicates, it is necessary to pass the elements through an Injector and use {@link
   * #getElements()} and {@link #permitsDuplicates()}.
   */
  boolean containsElement(Element element);
}
