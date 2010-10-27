/*
 * Copyright (C) 2007 Google Inc.
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

package com.google.inject.internal.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * Static utility methods pertaining to {@link Map} instances. Also see this
 * class's counterparts {@link Lists} and {@link Sets}.
 *
 * @author Kevin Bourrillion
 * @author Mike Bostock
 * @author Isaac Shum
 */
public final class Maps {
  private Maps() {}

  /**
   * Creates a {@code HashMap} instance.
   *
   * <p><b>Note:</b> if you don't actually need the resulting map to be mutable,
   * use {@link Collections#emptyMap} instead.
   *
   * @return a new, empty {@code HashMap}
   */
  public static <K, V> HashMap<K, V> newHashMap() {
    return new HashMap<K, V>();
  }

  /**
   * Creates an insertion-ordered {@code LinkedHashMap} instance.
   *
   * @return a new, empty {@code LinkedHashMap}
   */
  public static <K, V> LinkedHashMap<K, V> newLinkedHashMap() {
    return new LinkedHashMap<K, V>();
  }

  /**
   * Creates a {@code TreeMap} instance using the natural ordering of its
   * elements.
   *
   * @return a new, empty {@code TreeMap}
   */
  @SuppressWarnings("unchecked")  // allow ungenerified Comparable types
  public static <K extends Comparable, V> TreeMap<K, V> newTreeMap() {
    return new TreeMap<K, V>();
  }

  /**
   * Creates an {@code IdentityHashMap} instance.
   *
   * @return a new, empty {@code IdentityHashMap}
   */
  public static <K, V> IdentityHashMap<K, V> newIdentityHashMap() {
    return new IdentityHashMap<K, V>();
  }

  /**
   * Returns an immutable map entry with the specified key and value. The {@link
   * Entry#setValue} operation throws an {@link UnsupportedOperationException}.
   *
   * <p>The returned entry is serializable.
   *
   * @param key the key to be associated with the returned entry
   * @param value the value to be associated with the returned entry
   */
  public static <K, V> Entry<K, V> immutableEntry(
      @Nullable final K key, @Nullable final V value) {
    return new ImmutableEntry<K, V>(key, value);
  }
}
