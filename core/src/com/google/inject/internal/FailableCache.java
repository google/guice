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

package com.google.inject.internal;

import com.google.common.collect.ImmutableMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * Lazily creates (and caches) values for keys. If creating the value fails (with errors), an
 * exception is thrown on retrieval.
 *
 * This defends against re-entrant loads, and removals of elements while they are being loaded.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public abstract class FailableCache<K, V> {

  private final Map<K, FutureTask<V>> delegate = new LinkedHashMap<>();

  private final ThreadLocal<Set<K>> computingThreadLocal = ThreadLocal.withInitial(HashSet::new);

  protected abstract V create(K key, Errors errors) throws ErrorsException;

  public V get(K key, Errors errors) throws ErrorsException {
    Set<K> computing = computingThreadLocal.get();
    if (!computing.add(key)) {
      errors.addMessage("Recursive load of %s", key);
      throw errors.toException();
    }

    V result = null;
    try {
      FutureTask<V> futureTask;
      synchronized (delegate) {
        futureTask = delegate.computeIfAbsent(key, this::newFutureTask);
      }

      futureTask.run();
      result = futureTask.get();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof ErrorsException) {
        errors.merge(((ErrorsException) cause).getErrors());
      } else {
        throw new RuntimeException(e);
      }
    } finally {
      computing.remove(key);
    }

    if (errors.hasErrors()) {
      throw errors.toException();
    }

    return result;
  }

  private FutureTask<V> newFutureTask(K key) {
    return new FutureTask<>(() -> {
      Errors errors = new Errors();
      V result = null;
      try {
        result = create(key, errors);
      } catch (ErrorsException e) {
        errors.merge(e.getErrors());
      }
      if (errors.hasErrors()) throw errors.toException();
      return result;
    });
  }

  boolean remove(K key) {
    Set<K> computing = computingThreadLocal.get();
    if (computing.contains(key)) {
      return false; // Don't remove a value that's still being computed.
    }
    synchronized (delegate) {
      return delegate.remove(key) != null;
    }
  }

  Map<K, V> asMap() {
    synchronized (delegate) {
      ImmutableMap.Builder<K, V> result = ImmutableMap.builder();
      for (Map.Entry<K, FutureTask<V>> entry : delegate.entrySet()) {
        FutureTask<V> futureTask = entry.getValue();
        try {
          if (futureTask.isDone()) {
            result.put(entry.getKey(), futureTask.get());
          }
        } catch (Exception ignored) {
        }
      }
      return result.build();
    }
  }
}
