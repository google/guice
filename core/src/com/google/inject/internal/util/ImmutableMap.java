/*
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

package com.google.inject.internal.util;

import java.io.Serializable;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * An immutable, hash-based {@link Map} with reliable user-specified iteration
 * order. Does not permit null keys or values.
 *
 * <p>Unlike {@link Collections#unmodifiableMap}, which is a <i>view</i> of a
 * separate map which can still change, an instance of {@code ImmutableMap}
 * contains its own data and will <i>never</i> change. {@code ImmutableMap} is
 * convenient for {@code public static final} maps ("constant maps") and also
 * lets you easily make a "defensive copy" of a map provided to your class by a
 * caller.
 *
 * <p><b>Note</b>: Although this class is not final, it cannot be subclassed as
 * it has no public or protected constructors. Thus, instances of this class are
 * guaranteed to be immutable.
 *
 * @see ImmutableList
 * @see ImmutableSet
 * @author Jesse Wilson
 * @author Kevin Bourrillion
 */
@SuppressWarnings("serial") // we're overriding default serialization
public abstract class ImmutableMap<K, V>
    implements ConcurrentMap<K, V>, Serializable {
  private static final ImmutableMap<?, ?> EMPTY_IMMUTABLE_MAP
      = new EmptyImmutableMap();

  // TODO: restore prebuilder API?  optimize, compare performance to HashMap

  /**
   * Returns the empty map. This map behaves and performs comparably to
   * {@link Collections#emptyMap}, and is preferable mainly for consistency
   * and maintainability of your code.
   */
  // Casting to any type is safe because the set will never hold any elements.
  @SuppressWarnings("unchecked")
  public static <K, V> ImmutableMap<K, V> of() {
    return (ImmutableMap<K, V>) EMPTY_IMMUTABLE_MAP;
  }

  /**
   * Returns an immutable map containing a single entry. This map behaves and
   * performs comparably to {@link Collections#singletonMap} but will not accept
   * a null key or value. It is preferable mainly for consistency and
   * maintainability of your code.
   */
  public static <K, V> ImmutableMap<K, V> of(K k1, V v1) {
    return new SingletonImmutableMap<K, V>(
        Preconditions.checkNotNull(k1), Preconditions.checkNotNull(v1));
  }

  /**
   * Returns an immutable map containing the given entries, in order.
   *
   * @throws IllegalArgumentException if duplicate keys are added
   */
  public static <K, V> ImmutableMap<K, V> of(K k1, V v1, K k2, V v2) {
    return new RegularImmutableMap<K, V>(entryOf(k1, v1), entryOf(k2, v2));
  }

  /**
   * Returns an immutable map containing the given entries, in order.
   *
   * @throws IllegalArgumentException if duplicate keys are added
   */
  public static <K, V> ImmutableMap<K, V> of(
      K k1, V v1, K k2, V v2, K k3, V v3) {
    return new RegularImmutableMap<K, V>(
        entryOf(k1, v1), entryOf(k2, v2), entryOf(k3, v3));
  }

  /**
   * Returns an immutable map containing the given entries, in order.
   *
   * @throws IllegalArgumentException if duplicate keys are added
   */
  public static <K, V> ImmutableMap<K, V> of(
      K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
    return new RegularImmutableMap<K, V>(
        entryOf(k1, v1), entryOf(k2, v2), entryOf(k3, v3), entryOf(k4, v4));
  }

  /**
   * Returns an immutable map containing the given entries, in order.
   *
   * @throws IllegalArgumentException if duplicate keys are added
   */
  public static <K, V> ImmutableMap<K, V> of(
      K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
    return new RegularImmutableMap<K, V>(entryOf(k1, v1),
        entryOf(k2, v2), entryOf(k3, v3), entryOf(k4, v4), entryOf(k5, v5));
  }

  // looking for of() with > 5 entries? Use the builder instead.

  /**
   * Returns a new builder. The generated builder is equivalent to the builder
   * created by the {@link Builder} constructor.
   */
  public static <K, V> Builder<K, V> builder() {
    return new Builder<K, V>();
  }

  /**
   * Verifies that {@code key} and {@code value} are non-null, and returns a new
   * entry with those values.
   */
  private static <K, V> Entry<K, V> entryOf(K key, V value) {
    return Maps.immutableEntry(Preconditions.checkNotNull(key), Preconditions.checkNotNull(value));
  }

  /**
   * A builder for creating immutable map instances, especially {@code public
   * static final} maps ("constant maps"). Example: <pre>   {@code
   *
   *   static final ImmutableMap<String, Integer> WORD_TO_INT =
   *       new ImmutableMap.Builder<String, Integer>()
   *           .put("one", 1)
   *           .put("two", 2)
   *           .put("three", 3)
   *           .build();}</pre>
   *
   * For <i>small</i> immutable maps, the {@code ImmutableMap.of()} methods are
   * even more convenient.
   *
   * <p>Builder instances can be reused - it is safe to call {@link #build}
   * multiple times to build multiple maps in series. Each map is a superset of
   * the maps created before it.
   */
  public static class Builder<K, V> {
    final List<Entry<K, V>> entries = Lists.newArrayList();

    /**
     * Creates a new builder. The returned builder is equivalent to the builder
     * generated by {@link ImmutableMap#builder}.
     */
    public Builder() {}

    /**
     * Associates {@code key} with {@code value} in the built map. Duplicate
     * keys are not allowed, and will cause {@link #build} to fail.
     */
    public Builder<K, V> put(K key, V value) {
      entries.add(entryOf(key, value));
      return this;
    }

    /**
     * Associates all of {@code map's} keys and values in the built map.
     * Duplicate keys are not allowed, and will cause {@link #build} to fail.
     *
     * @throws NullPointerException if any key or value in {@code map} is null
     */
    public Builder<K, V> putAll(Map<? extends K, ? extends V> map) {
      for (Entry<? extends K, ? extends V> entry : map.entrySet()) {
        put(entry.getKey(), entry.getValue());
      }
      return this;
    }

    // TODO: Should build() and the ImmutableBiMap version throw an
    // IllegalStateException instead?

    /**
     * Returns a newly-created immutable map.
     *
     * @throws IllegalArgumentException if duplicate keys were added
     */
    public ImmutableMap<K, V> build() {
      return fromEntryList(entries);
    }

    private static <K, V> ImmutableMap<K, V> fromEntryList(
        List<Entry<K, V>> entries) {
      int size = entries.size();
      switch (size) {
        case 0:
          return of();
        case 1:
          return new SingletonImmutableMap<K, V>(Iterables.getOnlyElement(entries));
        default:
          Entry<?, ?>[] entryArray
              = entries.toArray(new Entry<?, ?>[entries.size()]);
          return new RegularImmutableMap<K, V>(entryArray);
      }
    }
  }

  /**
   * Returns an immutable map containing the same entries as {@code map}. If
   * {@code map} somehow contains entries with duplicate keys (for example, if
   * it is a {@code SortedMap} whose comparator is not <i>consistent with
   * equals</i>), the results of this method are undefined.
   *
   * <p><b>Note:</b> Despite what the method name suggests, if {@code map} is an
   * {@code ImmutableMap}, no copy will actually be performed, and the given map
   * itself will be returned.
   *
   * @throws NullPointerException if any key or value in {@code map} is null
   */
  public static <K, V> ImmutableMap<K, V> copyOf(
      Map<? extends K, ? extends V> map) {
    if (map instanceof ImmutableMap) {
      @SuppressWarnings("unchecked") // safe since map is not writable
      ImmutableMap<K, V> kvMap = (ImmutableMap<K, V>) map;
      return kvMap;
    }

    int size = map.size();
    switch (size) {
      case 0:
        return of();
      case 1:
        Map.Entry<? extends K, ? extends V> loneEntry
            = Iterables.getOnlyElement(map.entrySet());
        /*
         * Must cast next line to (K) and (V) to avoid returning an
         * ImmutableMap<? extends K, ? extends V>, which is incompatible
         * with the return type ImmutableMap<K, V>.  (Eclipse will complain
         * mightily about this line if there's no cast.)
         */
        return of((K) loneEntry.getKey(), (V) loneEntry.getValue());
      default:
        Entry<?, ?>[] array = new Entry<?, ?>[size];
        int i = 0;
        for (Entry<? extends K, ? extends V> entry : map.entrySet()) {
          /*
           * See comment above re: <? extends K, ? extends V> to <K, V>.
           */
          array[i++] = entryOf((K) entry.getKey(), (V) entry.getValue());
        }
        return new RegularImmutableMap<K, V>(array);
    }
  }

  ImmutableMap() {}

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   */
  public final V put(K k, V v) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   */
  public final V remove(Object o) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   */
  public final V putIfAbsent(K key, V value) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   */
  public final boolean remove(Object key, Object value) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   */
  public final boolean replace(K key, V oldValue, V newValue) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   */
  public final V replace(K key, V value) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   */
  public final void putAll(Map<? extends K, ? extends V> map) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   */
  public final void clear() {
    throw new UnsupportedOperationException();
  }

  // Overriding to mark it Nullable
  public abstract boolean containsKey(@Nullable Object key);

  // Overriding to mark it Nullable
  public abstract boolean containsValue(@Nullable Object value);

  // Overriding to mark it Nullable
  public abstract V get(@Nullable Object key);

  /**
   * Returns an immutable set of the mappings in this map. The entries are in
   * the same order as the parameters used to build this map.
   */
  public abstract ImmutableSet<Entry<K, V>> entrySet();

  /**
   * Returns an immutable set of the keys in this map. These keys are in
   * the same order as the parameters used to build this map.
   */
  public abstract ImmutableSet<K> keySet();

  /**
   * Returns an immutable collection of the values in this map. The values are
   * in the same order as the parameters used to build this map.
   */
  public abstract ImmutableCollection<V> values();

  @Override public boolean equals(@Nullable Object object) {
    if (object == this) {
      return true;
    }
    if (object instanceof Map) {
      Map<?, ?> that = (Map<?, ?>) object;
      return this.entrySet().equals(that.entrySet());
    }
    return false;
  }

  @Override public int hashCode() {
    // not caching hash code since it could change if map values are mutable
    // in a way that modifies their hash codes
    return entrySet().hashCode();
  }

  @Override public String toString() {
    StringBuilder result = new StringBuilder(size() * 16).append('{');
    Iterator<Entry<K, V>> entries = entrySet().iterator();
    result.append(entries.next());
    while (entries.hasNext()) {
      result.append(", ").append(entries.next());
    }
    return result.append('}').toString();
  }

  private static final class EmptyImmutableMap
      extends ImmutableMap<Object, Object> {

    @Override public Object get(Object key) {
      return null;
    }

    public int size() {
      return 0;
    }

    public boolean isEmpty() {
      return true;
    }

    @Override public boolean containsKey(Object key) {
      return false;
    }

    @Override public boolean containsValue(Object value) {
      return false;
    }

    @Override public ImmutableSet<Entry<Object, Object>> entrySet() {
      return ImmutableSet.of();
    }

    @Override public ImmutableSet<Object> keySet() {
      return ImmutableSet.of();
    }

    @Override public ImmutableCollection<Object> values() {
      return ImmutableCollection.EMPTY_IMMUTABLE_COLLECTION;
    }

    @Override public boolean equals(@Nullable Object object) {
      if (object instanceof Map) {
        Map<?, ?> that = (Map<?, ?>) object;
        return that.isEmpty();
      }
      return false;
    }

    @Override public int hashCode() {
      return 0;
    }

    @Override public String toString() {
      return "{}";
    }
  }

  private static final class SingletonImmutableMap<K, V>
      extends ImmutableMap<K, V> {
    private transient final K singleKey;
    private transient final V singleValue;
    private transient Entry<K, V> entry;

    private SingletonImmutableMap(K singleKey, V singleValue) {
      this.singleKey = singleKey;
      this.singleValue = singleValue;
    }

    private SingletonImmutableMap(Entry<K, V> entry) {
      this.entry = entry;
      this.singleKey = entry.getKey();
      this.singleValue = entry.getValue();
    }

    private Entry<K, V> entry() {
      Entry<K, V> e = entry;
      return (e == null)
          ? (entry = Maps.immutableEntry(singleKey, singleValue)) : e;
    }

    @Override public V get(Object key) {
      return singleKey.equals(key) ? singleValue : null;
    }

    public int size() {
      return 1;
    }

    public boolean isEmpty() {
      return false;
    }

    @Override public boolean containsKey(Object key) {
      return singleKey.equals(key);
    }

    @Override public boolean containsValue(Object value) {
      return singleValue.equals(value);
    }

    private transient ImmutableSet<Entry<K, V>> entrySet;

    @Override public ImmutableSet<Entry<K, V>> entrySet() {
      ImmutableSet<Entry<K, V>> es = entrySet;
      return (es == null) ? (entrySet = ImmutableSet.of(entry())) : es;
    }

    private transient ImmutableSet<K> keySet;

    @Override public ImmutableSet<K> keySet() {
      ImmutableSet<K> ks = keySet;
      return (ks == null) ? (keySet = ImmutableSet.of(singleKey)) : ks;
    }

    private transient ImmutableCollection<V> values;

    @Override public ImmutableCollection<V> values() {
      ImmutableCollection<V> v = values;
      return (v == null) ? (values = new Values<V>(singleValue)) : v;
    }

    private static class Values<V> extends ImmutableCollection<V> {
      final V singleValue;

      Values(V singleValue) {
        this.singleValue = singleValue;
      }

      @Override public boolean contains(Object object) {
        return singleValue.equals(object);
      }

      @Override public boolean isEmpty() {
        return false;
      }

      public int size() {
        return 1;
      }

      @Override public UnmodifiableIterator<V> iterator() {
        return Iterators.singletonIterator(singleValue);
      }
    }

    @Override public boolean equals(@Nullable Object object) {
      if (object == this) {
        return true;
      }
      if (object instanceof Map) {
        Map<?, ?> that = (Map<?, ?>) object;
        if (that.size() != 1) {
          return false;
        }
        Map.Entry<?, ?> entry = that.entrySet().iterator().next();
        return singleKey.equals(entry.getKey())
            && singleValue.equals(entry.getValue());
      }
      return false;
    }

    @Override public int hashCode() {
      return singleKey.hashCode() ^ singleValue.hashCode();
    }

    @Override public String toString() {
      return new StringBuilder()
          .append('{')
          .append(singleKey.toString())
          .append('=')
          .append(singleValue.toString())
          .append('}')
          .toString();
    }
  }

  private static final class RegularImmutableMap<K, V>
      extends ImmutableMap<K, V> {
    private transient final Entry<K, V>[] entries; // entries in insertion order
    private transient final Object[] table; // alternating keys and values
    // 'and' with an int then shift to get a table index
    private transient final int mask;
    private transient final int keySetHashCode;

    private RegularImmutableMap(Entry<?, ?>... entries) {
      // each of our 6 callers carefully put only Entry<K, V>s into the array!
      @SuppressWarnings("unchecked")
      Entry<K, V>[] tmp = (Entry<K, V>[]) entries;
      this.entries = tmp;

      int tableSize = Hashing.chooseTableSize(entries.length);
      table = new Object[tableSize * 2];
      mask = tableSize - 1;

      int keySetHashCodeMutable = 0;
      for (Entry<K, V> entry : this.entries) {
        K key = entry.getKey();
        int keyHashCode = key.hashCode();
        for (int i = Hashing.smear(keyHashCode); true; i++) {
          int index = (i & mask) * 2;
          Object existing = table[index];
          if (existing == null) {
            V value = entry.getValue();
            table[index] = key;
            table[index + 1] = value;
            keySetHashCodeMutable += keyHashCode;
            break;
          } else if (existing.equals(key)) {
            throw new IllegalArgumentException("duplicate key: " + key);
          }
        }
      }
      keySetHashCode = keySetHashCodeMutable;
    }

    @Override public V get(Object key) {
      if (key == null) {
        return null;
      }
      for (int i = Hashing.smear(key.hashCode()); true; i++) {
        int index = (i & mask) * 2;
        Object candidate = table[index];
        if (candidate == null) {
          return null;
        }
        if (candidate.equals(key)) {
          // we're careful to store only V's at odd indices
          @SuppressWarnings("unchecked")
          V value = (V) table[index + 1];
          return value;
        }
      }
    }

    public int size() {
      return entries.length;
    }

    public boolean isEmpty() {
      return false;
    }

    @Override public boolean containsKey(Object key) {
      return get(key) != null;
    }

    @Override public boolean containsValue(Object value) {
      if (value == null) {
        return false;
      }
      for (Entry<K, V> entry : entries) {
        if (entry.getValue().equals(value)) {
          return true;
        }
      }
      return false;
    }

    // TODO: Serialization of the map views should serialize the map, and
    // deserialization should call entrySet(), keySet(), or values() on the
    // deserialized map. The views are serializable since the Immutable* classes
    // are.

    private transient ImmutableSet<Entry<K, V>> entrySet;

    @Override public ImmutableSet<Entry<K, V>> entrySet() {
      ImmutableSet<Entry<K, V>> es = entrySet;
      return (es == null) ? (entrySet = new EntrySet<K, V>(this)) : es;
    }

    private static class EntrySet<K, V> extends ImmutableSet.ArrayImmutableSet<Entry<K, V>> {
      final RegularImmutableMap<K, V> map;

      EntrySet(RegularImmutableMap<K, V> map) {
        super(map.entries);
        this.map = map;
      }

      @Override public boolean contains(Object target) {
        if (target instanceof Entry) {
          Entry<?, ?> entry = (Entry<?, ?>) target;
          V mappedValue = map.get(entry.getKey());
          return mappedValue != null && mappedValue.equals(entry.getValue());
        }
        return false;
      }
    }

    private transient ImmutableSet<K> keySet;

    @Override public ImmutableSet<K> keySet() {
      ImmutableSet<K> ks = keySet;
      return (ks == null) ? (keySet = new KeySet<K, V>(this)) : ks;
    }

    private static class KeySet<K, V>
        extends ImmutableSet.TransformedImmutableSet<Entry<K, V>, K> {
      final RegularImmutableMap<K, V> map;

      KeySet(RegularImmutableMap<K, V> map) {
        super(map.entries, map.keySetHashCode);
        this.map = map;
      }

      @Override K transform(Entry<K, V> element) {
        return element.getKey();
      }

      @Override public boolean contains(Object target) {
        return map.containsKey(target);
      }
    }

    private transient ImmutableCollection<V> values;

    @Override public ImmutableCollection<V> values() {
      ImmutableCollection<V> v = values;
      return (v == null) ? (values = new Values<V>(this)) : v;
    }

    private static class Values<V> extends ImmutableCollection<V>  {
      final RegularImmutableMap<?, V> map;

      Values(RegularImmutableMap<?, V> map) {
        this.map = map;
      }

      public int size() {
        return map.entries.length;
      }

      @Override public boolean isEmpty() {
        return false;
      }

      @Override public UnmodifiableIterator<V> iterator() {
        Iterator<V> iterator = new AbstractIterator<V>() {
          int index = 0;
          @Override protected V computeNext() {
            return (index < map.entries.length)
                ? map.entries[index++].getValue()
                : endOfData();
          }
        };
        // Though the AbstractIterator is unmodifiable, it isn't an
        // UnmodifiableIterator.
        return Iterators.unmodifiableIterator(iterator);
      }

      @Override public boolean contains(Object target) {
        return map.containsValue(target);
      }
    }

    @Override public String toString() {
      StringBuilder result = new StringBuilder(size() * 16)
          .append('{')
          .append(entries[0]);
      for (int e = 1; e < entries.length; e++) {
        result.append(", ").append(entries[e].toString());
      }
      return result.append('}').toString();
    }
  }

  /*
   * Serialized type for all ImmutableMap instances. It captures the logical
   * contents and they are reconstructed using public factory methods. This
   * ensures that the implementation types remain as implementation details.
   */
  private static class SerializedForm implements Serializable {
    final Object[] keys;
    final Object[] values;
    SerializedForm(ImmutableMap<?, ?> map) {
      keys = new Object[map.size()];
      values = new Object[map.size()];
      int i = 0;
      for (Entry<?, ?> entry : map.entrySet()) {
        keys[i] = entry.getKey();
        values[i] = entry.getValue();
        i++;
      }
    }
    Object readResolve() {
      Builder<Object, Object> builder = new Builder<Object, Object>();
      for (int i = 0; i < keys.length; i++) {
        builder.put(keys[i], values[i]);
      }
      return builder.build();
    }
    private static final long serialVersionUID = 0;
  }

  Object writeReplace() {
    return new SerializedForm(this);
  }
}
