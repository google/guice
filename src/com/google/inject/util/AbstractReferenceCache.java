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

package com.google.inject.util;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Supports cache implementations.
 *
 * @author crazybob@google.com (Bob Lee)
 */
abstract class AbstractReferenceCache<K, V> extends ReferenceMap<K, V> {

  private static final long serialVersionUID = 0;

  transient ConcurrentMap<Object, FutureValue<V>> futures =
      new ConcurrentHashMap<Object, FutureValue<V>>();

  AbstractReferenceCache(ReferenceType keyReferenceType,
      ReferenceType valueReferenceType) {
    super(keyReferenceType, valueReferenceType);
  }

  V internalCreate(K key) {
    FutureValue<V> futureValue = new FutureValue<V>();

    // use a reference so we get the correct equality semantics.
    Object keyReference = referenceKey(key);
    FutureValue<V> previous =
        futures.putIfAbsent(keyReference, futureValue);
    if (previous == null) {
      // winning thread.
      try {
        // check one more time (a previous future could have come and gone.)
        V value = internalGet(key);
        if (value != null) {
          futureValue.setValue(value);
          return value;
        }

        try {
          value = create(futureValue, key);
          if (value == null) {
            throw new NullPointerException(
                "create() returned null for: " + key);
          }
          futureValue.setValue(value);
        } catch (Throwable t) {
          futureValue.setThrowable(t);
          rethrow(t);
        }

        putStrategy().execute(
            this, keyReference, referenceValue(keyReference, value));

        return value;
      } finally {
        futures.remove(keyReference);
      }
    } else {
      if (previous.winningThread() == Thread.currentThread()) {
        throw new RuntimeException("Circular reference: " + key);
      }

      // wait for winning thread.
      return previous.get();
    }
  }

  private static void rethrow(Throwable t) {
    if (t instanceof RuntimeException) {
      throw (RuntimeException) t;
    }
    if (t instanceof Error) {
      throw (Error) t;
    }
    throw new RuntimeException(t);
  }

  /**
   * Creates a value for the given key.
   */
  abstract V create(FutureValue<V> futureValue, K key);

  /**
   * {@inheritDoc}
   *
   * If this map does not contain an entry for the given key, this method will
   * create a new value, put it in the map, and return it. The value
   * is canonical (i.e. only one value will be created for each key).
   *
   * @throws NullPointerException if the value is {@code null}
   * @throws java.util.concurrent.CancellationException if the value creation
   *  is cancelled.
   */
  @SuppressWarnings("unchecked")
  @Override public V get(final Object key) {
    V value = super.get(key);
    return (value == null)
        ? internalCreate((K) key)
        : value;
  }

  private void readObject(ObjectInputStream in) throws IOException,
      ClassNotFoundException {
    in.defaultReadObject();
    this.futures = new ConcurrentHashMap<Object, FutureValue<V>>();
  }

  /**
   * Synchronizes threads waiting for the same value.
   */
  static class FutureValue<V> {

    /** True if the result has been set. */
    private boolean set = false;

    /** The result is a value. */
    private V value;

    /** The result is a throwable. */
    private Throwable t;

    private final Thread winningThread = Thread.currentThread();

    Thread winningThread() {
      return winningThread;
    }

    V get() {
      if (!set) {
        boolean interrupted = waitUntilSet();

        if (interrupted) {
          Thread.currentThread().interrupt();
        }
      }

      if (t != null) {
        rethrow(t);
      }
      return value;
    }

    /**
     * Waits until a value is set.
     *
     * @return {@code true} if the thread was interrupted while waiting
     */
    private synchronized boolean waitUntilSet() {
      boolean interrupted = false;
      while (!set) {
        try {
          wait();
        } catch (InterruptedException e) {
          interrupted = true;
        }
      }
      return interrupted;
    }

    synchronized void setValue(V v) {
      set();
      value = v;
    }

    synchronized void setThrowable(Throwable t) {
      set();
      this.t = t;
    }

    private void set() {
      if (set) {
        throw new IllegalStateException("Value is already set.");
      }
      set = true;
      notifyAll();
    }
  }
}
