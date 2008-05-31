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

package com.google.inject.internal;

import static com.google.inject.internal.ReferenceType.STRONG;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.ref.Reference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Concurrent hash map that wraps keys and/or values in soft or weak references.
 * Does not support null keys or values. Uses identity equality for weak and
 * soft keys.
 *
 * <p>The concurrent semantics of {@link ConcurrentHashMap} combined with the
 * fact that the garbage collector can asynchronously reclaim and clean up
 * keys and values at any time can lead to some racy semantics. For example,
 * {@link #size()} returns an upper bound on the size; that is, the actual size
 * may be smaller in cases where the key or value has been reclaimed but the map
 * entry has not been cleaned up yet.
 *
 * @author crazybob@google.com (Bob Lee)
 * @author fry@google.com (Charles Fry)
 */
@SuppressWarnings("unchecked")
public class ReferenceMap<K, V> extends AbstractMap<K, V>
    implements ConcurrentMap<K, V>, Serializable {

  transient ConcurrentMap<Object, Object> delegate;

  private final ReferenceType keyReferenceType;
  private final ReferenceType valueReferenceType;

  /**
   * Concurrent hash map that wraps keys and/or values based on specified
   * reference types.
   *
   * @param keyReferenceType key reference type
   * @param valueReferenceType value reference type
   */
  public ReferenceMap(
      ReferenceType keyReferenceType, ReferenceType valueReferenceType) {
    ensureNotNull(keyReferenceType, valueReferenceType);

    if (keyReferenceType == ReferenceType.PHANTOM
        || valueReferenceType == ReferenceType.PHANTOM) {
      throw new IllegalArgumentException("Phantom references not supported.");
    }

    this.delegate = new ConcurrentHashMap<Object, Object>();
    this.keyReferenceType = keyReferenceType;
    this.valueReferenceType = valueReferenceType;
  }

  V internalGet(K key) {
    Object valueReference = delegate.get(makeKeyReferenceAware(key));
    return dereferenceValue(valueReference);
  }

  public V get(final Object key) {
    Objects.nonNull(key, "key");
    return internalGet((K) key);
  }

  private V execute(Strategy strategy, K key, V value) {
    ensureNotNull(key, value);
    Object keyReference = referenceKey(key);
    return (V) strategy.execute(
        this, keyReference, referenceValue(keyReference, value)
    );
  }

  public V put(K key, V value) {
    return execute(putStrategy(), key, value);
  }

  public V remove(Object key) {
    Objects.nonNull(key, "key");
    Object referenceAwareKey = makeKeyReferenceAware(key);
    Object valueReference = delegate.remove(referenceAwareKey);
    return dereferenceValue(valueReference);
  }

  public int size() {
    return delegate.size();
  }

  public boolean isEmpty() {
    return delegate.isEmpty();
  }

  public boolean containsKey(Object key) {
    Objects.nonNull(key, "key");
    Object referenceAwareKey = makeKeyReferenceAware(key);
    return delegate.containsKey(referenceAwareKey);
  }

  public boolean containsValue(Object value) {
    Objects.nonNull(value, "value");
    for (Object valueReference : delegate.values()) {
      if (value.equals(dereferenceValue(valueReference))) {
        return true;
      }
    }
    return false;
  }

  public void putAll(Map<? extends K, ? extends V> t) {
    for (Map.Entry<? extends K, ? extends V> entry : t.entrySet()) {
      put(entry.getKey(), entry.getValue());
    }
  }

  public void clear() {
    delegate.clear();
  }

  public V putIfAbsent(K key, V value) {
    return execute(putIfAbsentStrategy(), key, value);
  }

  public boolean remove(Object key, Object value) {
    ensureNotNull(key, value);
    Object referenceAwareKey = makeKeyReferenceAware(key);
    Object referenceAwareValue = makeValueReferenceAware(value);
    return delegate.remove(referenceAwareKey, referenceAwareValue);
  }

  public boolean replace(K key, V oldValue, V newValue) {
    ensureNotNull(key, oldValue, newValue);
    Object keyReference = referenceKey(key);

    Object referenceAwareOldValue = makeValueReferenceAware(oldValue);
    return delegate.replace(keyReference, referenceAwareOldValue,
        referenceValue(keyReference, newValue)
    );
  }

  public V replace(K key, V value) {
    return execute(replaceStrategy(), key, value);
  }

  private transient volatile Set<Map.Entry<K, V>> entrySet = null;

  public Set<Map.Entry<K, V>> entrySet() {
    if (entrySet == null) {
      entrySet = new EntrySet();
    }
    return entrySet;
  }

  /** Dereferences an entry. Returns null if the key or value has been gc'ed. */
  private Entry dereferenceEntry(Map.Entry<Object, Object> entry) {
    K key = dereferenceKey(entry.getKey());
    V value = dereferenceValue(entry.getValue());
    return (key == null || value == null)
        ? null
        : new Entry(key, value);
  }

  /** Creates a reference for a key. */
  Object referenceKey(K key) {
    switch (keyReferenceType) {
      case STRONG:
        return key;
      case SOFT:
        return new SoftKeyReference(key);
      case WEAK:
        return new WeakKeyReference(key);
      default:
        throw new AssertionError();
    }
  }

  /** Converts a reference to a key. */
  private K dereferenceKey(Object o) {
    return (K) dereference(keyReferenceType, o);
  }

  /** Converts a reference to a value. */
  V dereferenceValue(Object o) {
    if (o == null) {
      return null;
    }
    Object value = dereference(valueReferenceType, o);
    if (o instanceof InternalReference) {
      InternalReference ref = (InternalReference) o;
      if (value == null) {
        // old value was garbage collected
        ref.finalizeReferent();
      }
    }
    return (V) value;
  }

  /** Returns the refererent for reference given its reference type. */
  private Object dereference(ReferenceType referenceType, Object reference) {
    return referenceType == STRONG ? reference : ((Reference) reference).get();
  }

  /** Creates a reference for a value. */
  Object referenceValue(Object keyReference, Object value) {
    switch (valueReferenceType) {
      case STRONG:
        return value;
      case SOFT:
        return new SoftValueReference(keyReference, value);
      case WEAK:
        return new WeakValueReference(keyReference, value);
      default:
        throw new AssertionError();
    }
  }

  /**
   * Wraps key so it can be compared to a referenced key for equality.
   */
  private Object makeKeyReferenceAware(Object o) {
    return keyReferenceType == STRONG ? o : new KeyReferenceAwareWrapper(o);
  }

  /** Wraps value so it can be compared to a referenced value for equality. */
  private Object makeValueReferenceAware(Object o) {
    return valueReferenceType == STRONG ? o : new ReferenceAwareWrapper(o);
  }

  /**
   * Marker interface to differentiate external and internal references. Also
   * duplicates FinalizableReference and Reference.get for internal use.
   */
  interface InternalReference {
    void finalizeReferent();

    Object get();
  }

  private static int keyHashCode(Object key) {
    return System.identityHashCode(key);
  }

  /*
   * Tests weak and soft references for identity equality. Compares references
   * to other references and wrappers. If o is a reference, this returns true if
   * r == o or if r and o reference the same non-null object. If o is a wrapper,
   * this returns true if r's referent is identical to the wrapped object.
   */
  private static boolean referenceEquals(Reference r, Object o) {
    // compare reference to reference.
    if (o instanceof InternalReference) {
      // are they the same reference? used in cleanup.
      if (o == r) {
        return true;
      }

      // do they reference identical values? used in conditional puts.
      Object referent = ((Reference) o).get();
      return referent != null && referent == r.get();
    }

    // is the wrapped object identical to the referent? used in lookups.
    return ((ReferenceAwareWrapper) o).unwrap() == r.get();
  }

  /**
   * Returns {@code true} if the specified value reference has been garbage
   * collected. The value behind the reference is also passed in, rather than
   * queried inside this method, to ensure that the return statement of this
   * method will still hold true after it has returned (that is, a value
   * reference exists outside of this method which will prevent that value from
   * being garbage collected).
   *
   * @param valueReference the value reference to be tested
   * @param value the object referenced by {@code valueReference}
   * @return {@code true} if {@code valueReference} is non-null and {@code
   *     value} is null
   */
  private static boolean isExpired(Object valueReference, Object value) {
    return (valueReference != null) && (value == null);
  }

  /**
   * Big hack. Used to compare keys and values to referenced keys and values
   * without creating more references.
   */
  static class ReferenceAwareWrapper {
    final Object wrapped;

    ReferenceAwareWrapper(Object wrapped) {
      this.wrapped = wrapped;
    }

    Object unwrap() {
      return wrapped;
    }

    @Override public int hashCode() {
      return wrapped.hashCode();
    }

    @Override public boolean equals(Object obj) {
      // defer to reference's equals() logic.
      return obj.equals(this);
    }
  }

  /** Used for keys. Overrides hash code to use identity hash code. */
  static class KeyReferenceAwareWrapper extends ReferenceAwareWrapper {
    public KeyReferenceAwareWrapper(Object wrapped) {
      super(wrapped);
    }

    public int hashCode() {
      return System.identityHashCode(wrapped);
    }
  }

  class SoftKeyReference extends FinalizableSoftReference<Object>
      implements InternalReference {
    final int hashCode;

    public SoftKeyReference(Object key) {
      super(key);
      this.hashCode = keyHashCode(key);
    }

    public void finalizeReferent() {
      delegate.remove(this);
    }

    @Override public int hashCode() {
      return this.hashCode;
    }

    @Override public boolean equals(Object o) {
      return referenceEquals(this, o);
    }
  }

  class WeakKeyReference extends FinalizableWeakReference<Object>
      implements InternalReference {
    final int hashCode;

    public WeakKeyReference(Object key) {
      super(key);
      this.hashCode = keyHashCode(key);
    }

    public void finalizeReferent() {
      delegate.remove(this);
    }

    @Override public int hashCode() {
      return this.hashCode;
    }

    @Override public boolean equals(Object o) {
      return referenceEquals(this, o);
    }
  }

  class SoftValueReference extends FinalizableSoftReference<Object>
      implements InternalReference {
    final Object keyReference;

    public SoftValueReference(Object keyReference, Object value) {
      super(value);
      this.keyReference = keyReference;
    }

    public void finalizeReferent() {
      delegate.remove(keyReference, this);
    }

    @Override public boolean equals(Object obj) {
      return referenceEquals(this, obj);
    }
  }

  class WeakValueReference extends FinalizableWeakReference<Object>
      implements InternalReference {
    final Object keyReference;

    public WeakValueReference(Object keyReference, Object value) {
      super(value);
      this.keyReference = keyReference;
    }

    public void finalizeReferent() {
      delegate.remove(keyReference, this);
    }

    @Override public boolean equals(Object obj) {
      return referenceEquals(this, obj);
    }
  }

  protected interface Strategy {
    public Object execute(
        ReferenceMap map, Object keyReference, Object valueReference);
  }

  // TODO(crazybob): a getter called put() is probably a bad idea
  protected Strategy putStrategy() {
    return PutStrategy.PUT;
  }

  protected Strategy putIfAbsentStrategy() {
    return PutStrategy.PUT_IF_ABSENT;
  }

  protected Strategy replaceStrategy() {
    return PutStrategy.REPLACE;
  }

  private enum PutStrategy implements Strategy {
    PUT {
      public Object execute(
          ReferenceMap map, Object keyReference, Object valueReference) {
        return map.dereferenceValue(
            map.delegate.put(keyReference, valueReference));
      }
    },

    REPLACE {
      public Object execute(
          ReferenceMap map, Object keyReference, Object valueReference) {
        // ensure that the existing value is not collected
        do {
          Object existingValueReference;
          Object existingValue;
          do {
            existingValueReference = map.delegate.get(keyReference);
            existingValue = map.dereferenceValue(existingValueReference);
          } while (isExpired(existingValueReference, existingValue));

          if (existingValueReference == null) {
            // nothing to replace
            return false;
          }

          if (map.delegate.replace(
              keyReference, existingValueReference, valueReference)) {
            // existingValue didn't expire since we still have a reference to it
            return existingValue;
          }
        } while (true);
      }
    },

    PUT_IF_ABSENT {
      public Object execute(
          ReferenceMap map, Object keyReference, Object valueReference) {
        Object existingValueReference;
        Object existingValue;
        do {
          existingValueReference
              = map.delegate.putIfAbsent(keyReference, valueReference);
          existingValue = map.dereferenceValue(existingValueReference);
        } while (isExpired(existingValueReference, existingValue));
        return existingValue;
      }
    },
  }

  private static PutStrategy defaultPutStrategy;

  protected PutStrategy getPutStrategy() {
    return defaultPutStrategy;
  }

  class Entry implements Map.Entry<K, V> {
    final K key;
    V value;

    public Entry(K key, V value) {
      this.key = key;
      this.value = value;
    }

    public K getKey() {
      return this.key;
    }

    public V getValue() {
      return this.value;
    }

    public V setValue(V newValue) {
      value = newValue;
      return put(key, newValue);
    }

    public int hashCode() {
      return key.hashCode() * 31 + value.hashCode();
    }

    public boolean equals(Object o) {
      if (!(o instanceof ReferenceMap.Entry)) {
        return false;
      }

      Entry entry = (Entry) o;
      return key.equals(entry.key) && value.equals(entry.value);
    }

    public String toString() {
      return key + "=" + value;
    }
  }

  private class ReferenceIterator implements Iterator<Map.Entry<K, V>> {
    private Iterator<Map.Entry<Object, Object>> i =
        delegate.entrySet().iterator();
    private Map.Entry<K, V> nextEntry;
    private Map.Entry<K, V> lastReturned;

    public ReferenceIterator() {
      advance();
    }

    private void advance() {
      while (i.hasNext()) {
        Map.Entry<K, V> entry = dereferenceEntry(i.next());
        if (entry != null) {
          nextEntry = entry;
          return;
        }
      }

      // nothing left
      nextEntry = null;
    }

    public boolean hasNext() {
      return nextEntry != null;
    }

    public Map.Entry<K, V> next() {
      if (nextEntry == null) {
        throw new NoSuchElementException();
      }
      lastReturned = nextEntry;
      advance();
      return lastReturned;
    }

    public void remove() {
      ReferenceMap.this.remove(lastReturned.getKey());
    }
  }

  private class EntrySet extends AbstractSet<Map.Entry<K, V>> {
    public Iterator<Map.Entry<K, V>> iterator() {
      return new ReferenceIterator();
    }

    public int size() {
      return delegate.size();
    }

    public boolean contains(Object o) {
      if (!(o instanceof Map.Entry)) {
        return false;
      }
      Map.Entry<K, V> e = (Map.Entry<K, V>) o;
      V v = ReferenceMap.this.get(e.getKey());
      return v != null && v.equals(e.getValue());
    }

    public boolean remove(Object o) {
      if (!(o instanceof Map.Entry)) {
        return false;
      }
      Map.Entry<K, V> e = (Map.Entry<K, V>) o;
      return ReferenceMap.this.remove(e.getKey(), e.getValue());
    }

    public void clear() {
      delegate.clear();
    }
  }

  static void ensureNotNull(Object... array) {
    for (int i = 0; i < array.length; i++) {
      if (array[i] == null) {
        throw new NullPointerException("Argument #" + i + " is null.");
      }
    }
  }

  private void writeObject(ObjectOutputStream out) throws IOException {
    out.defaultWriteObject();
    out.writeInt(size());
    for (Map.Entry<Object, Object> entry : delegate.entrySet()) {
      Object key = dereferenceKey(entry.getKey());
      Object value = dereferenceValue(entry.getValue());

      // don't persist gc'ed entries.
      if (key != null && value != null) {
        out.writeObject(key);
        out.writeObject(value);
      }
    }
    out.writeObject(null);
  }

  private void readObject(ObjectInputStream in)
      throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    int size = in.readInt();
    this.delegate = new ConcurrentHashMap<Object, Object>(size);
    while (true) {
      K key = (K) in.readObject();
      if (key == null) {
        break;
      }
      V value = (V) in.readObject();
      put(key, value);
    }
  }

  private static final long serialVersionUID = 0;
}
