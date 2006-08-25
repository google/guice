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

import static com.google.inject.util.ReferenceType.STRONG;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

/**
 * Extends {@link ReferenceMap} to support lazy loading values by overriding
 * {@link #create(Object)}.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public abstract class ReferenceCache<K, V> extends ReferenceMap<K, V> {

  private static final long serialVersionUID = 0;

  transient ConcurrentMap<Object, Future<V>> futures =
      new ConcurrentHashMap<Object, Future<V>>();

  transient ThreadLocal<Future<V>> localFuture = new ThreadLocal<Future<V>>();

  public ReferenceCache(ReferenceType keyReferenceType,
      ReferenceType valueReferenceType) {
    super(keyReferenceType, valueReferenceType);
  }

  /**
   * Equivalent to {@code new ReferenceCache(STRONG, STRONG)}.
   */
  public ReferenceCache() {
    super(STRONG, STRONG);
  }

  /**
   * Override to lazy load values. Use as an alternative to {@link
   * #put(Object,Object)}. Invoked by getter if value isn't already cached.
   * Must not return {@code null}. This method will not be called again until
   * the garbage collector reclaims the returned value.
   */
  protected abstract V create(K key);

  V internalCreate(K key) {
    try {
      FutureTask<V> futureTask = new FutureTask<V>(
          new CallableCreate(key));

      // use a reference so we get the same equality semantics.
      Object keyReference = referenceKey(key);
      Future<V> future = futures.putIfAbsent(keyReference, futureTask);
      if (future == null) {
        // winning thread.
        try {
          if (localFuture.get() != null) {
            throw new IllegalStateException(
                "Nested creations within the same cache are not allowed.");
          }
          localFuture.set(futureTask);
          futureTask.run();
          V value = futureTask.get();
          putStrategy().execute(this,
              keyReference, referenceValue(keyReference, value));
          return value;
        } finally {
          localFuture.remove();
          futures.remove(keyReference);
        }
      } else {
        // wait for winning thread.
        return future.get();
      }
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      } else if (cause instanceof Error) {
        throw (Error) cause;
      }
      throw new RuntimeException(cause);
    }
  }

  /**
   * {@inheritDoc}
   *
   * If this map does not contain an entry for the given key and {@link
   * #create(Object)} has been overridden, this method will create a new
   * value, put it in the map, and return it.
   *
   * @throws NullPointerException if {@link #create(Object)} returns null.
   * @throws java.util.concurrent.CancellationException if the creation is
   *  cancelled. See {@link #cancel()}.
   */
  @SuppressWarnings("unchecked")
  @Override public V get(final Object key) {
    V value = super.get(key);
    return (value == null)
      ? internalCreate((K) key)
      : value;
  }

  /**
   * Cancels the current {@link #create(Object)}. Throws {@link
   * java.util.concurrent.CancellationException} to all clients currently
   * blocked on {@link #get(Object)}.
   */
  protected void cancel() {
    Future<V> future = localFuture.get();
    if (future == null) {
      throw new IllegalStateException("Not in create().");
    }
    future.cancel(false);
  }

  class CallableCreate implements Callable<V> {

    K key;

    public CallableCreate(K key) {
      this.key = key;
    }

    public V call() {
      // try one more time (a previous future could have come and gone.)
      V value = internalGet(key);
      if (value != null) {
        return value;
      }

      // create value.
      value = create(key);
      if (value == null) {
        throw new NullPointerException(
            "create(K) returned null for: " + key);
      }
      return value;
    }
  }

  /**
   * Returns a {@code ReferenceCache} delegating to the specified {@code
   * function}. The specified function must not return {@code null}.
   */
  public static <K, V> ReferenceCache<K, V> of(
      ReferenceType keyReferenceType,
      ReferenceType valueReferenceType,
      final Function<? super K, ? extends V> function) {
    ensureNotNull(function);
    return new ReferenceCache<K, V>(keyReferenceType, valueReferenceType) {
      protected V create(K key) {
        return function.apply(key);
      }
      private static final long serialVersionUID = 0;
    };
  }

  private void readObject(ObjectInputStream in) throws IOException,
      ClassNotFoundException {
    in.defaultReadObject();
    this.futures = new ConcurrentHashMap<Object, Future<V>>();
    this.localFuture = new ThreadLocal<Future<V>>();
  }

}
