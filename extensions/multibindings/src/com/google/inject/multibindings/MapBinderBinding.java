/**
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

import java.util.List;
import java.util.Map;

import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;

/**
 * A binding for a MapBinder.
 * <p>
 * Although MapBinders may be injected through a variety of generic types (Map&lt;K, V>, Map
 * &lt;K, Provider&lt;V>>, Map&lt;K, Set&lt;V>>, Map<K, Set&lt;
 * Provider&lt;V>>, and even Set&lt;Map.Entry&lt;K, Provider&lt;V>>), a
 * MapBinderBinding exists only on the Binding associated with the Map&lt;K, V> key. Other
 * bindings can be validated to be derived from this MapBinderBinding using
 * {@link #containsElement(Element)}.
 * 
 * @param <T> The fully qualified type of the map, including Map. For example:
 *          <code>MapBinderBinding&lt;Map&lt;String, Snack>></code>
 * 
 * @since 3.0
 * @author sameb@google.com (Sam Berlin)
 */
public interface MapBinderBinding<T> {

  /** Returns the {@link Key} for the map. */
  Key<T> getMapKey();

  /**
   * Returns the TypeLiteral describing the keys of the map.
   * <p>
   * The TypeLiteral will always match the type Map's generic type. For example, if getMapKey
   * returns a key of <code>Map&lt;String, Snack></code>, then this will always return a
   * <code>TypeLiteral&lt;String></code>.
   */
  TypeLiteral<?> getKeyTypeLiteral();

  /**
   * Returns the TypeLiteral describing the values of the map.
   * <p>
   * The TypeLiteral will always match the type Map's generic type. For example, if getMapKey
   * returns a key of <code>Map&lt;String, Snack></code>, then this will always return a
   * <code>TypeLiteral&lt;Snack></code>.
   */
  TypeLiteral<?> getValueTypeLiteral();

  /**
   * Returns all entries in the Map. The returned list of Map.Entries contains the key and a binding
   * to the value. Duplicate keys or values will exist as separate Map.Entries in the returned list.
   * This is only supported on bindings returned from an injector. This will throw
   * {@link UnsupportedOperationException} if it is called on an element retrieved from
   * {@link Elements#getElements}.
   * <p>
   * The elements will always match the type Map's generic type. For example, if getMapKey returns a
   * key of <code>Map&lt;String, Snack></code>, then this will always return a list of type
   * <code>List&lt;Map.Entry&lt;String, Binding&lt;Snack>>></code>.
   */
  List<Map.Entry<?, Binding<?>>> getEntries();

  /**
   * Returns true if the MapBinder permits duplicates. This is only supported on bindings returned
   * from an injector. This will throw {@link UnsupportedOperationException} if it is called on a
   * MapBinderBinding retrieved from {@link Elements#getElements}.
   */
  boolean permitsDuplicates();

  /**
   * Returns true if this MapBinder contains the given Element in order to build the map or uses the
   * given Element in order to support building and injecting the map. This will work for
   * MapBinderBindings retrieved from an injector and {@link Elements#getElements}. Usually this is
   * only necessary if you are working with elements retrieved from modules (without an Injector),
   * otherwise {@link #getEntries} and {@link #permitsDuplicates} are better options.
   * <p>
   * If you need to introspect the details of the map, such as the keys, values or if it permits
   * duplicates, it is necessary to pass the elements through an Injector and use
   * {@link #getEntries()} and {@link #permitsDuplicates()}.
   */
  boolean containsElement(Element element);
}
