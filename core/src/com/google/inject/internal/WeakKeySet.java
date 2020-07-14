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

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.google.inject.Key;
import com.google.inject.internal.util.SourceProvider;
import java.util.Map;
import java.util.Set;

/**
 * Minimal set that doesn't hold strong references to the contained keys.
 *
 * @author dweis@google.com (Daniel Weis)
 */
final class WeakKeySet {

  private Map<Key<?>, Multiset<Object>> backingMap;

  /**
   * This is already locked externally on add and getSources but we need it to handle clean up in
   * the evictionCache's RemovalListener.
   */
  private final Object lock;

  /**
   * Tracks child injector lifetimes and evicts blacklisted keys/sources after the child injector is
   * garbage collected.
   */
  private final Cache<InjectorBindingData, Set<KeyAndSource>> evictionCache =
      CacheBuilder.newBuilder().weakKeys().removalListener(this::cleanupOnRemoval).build();

  private void cleanupOnRemoval(
      RemovalNotification<InjectorBindingData, Set<KeyAndSource>> notification) {
    Preconditions.checkState(RemovalCause.COLLECTED.equals(notification.getCause()));

    // There may be multiple child injectors blacklisting a certain key so only remove the source
    // that's relevant.
    synchronized (lock) {
      for (KeyAndSource keyAndSource : notification.getValue()) {
        Multiset<Object> set = backingMap.get(keyAndSource.key);
        if (set != null) {
          set.remove(keyAndSource.source);
          if (set.isEmpty()) {
            backingMap.remove(keyAndSource.key);
          }
        }
      }
    }
  }

  WeakKeySet(Object lock) {
    this.lock = lock;
  }

  public void add(Key<?> key, InjectorBindingData state, Object source) {
    if (backingMap == null) {
      backingMap = Maps.newHashMap();
    }
    // if it's an instanceof Class, it was a JIT binding, which we don't
    // want to retain.
    if (source instanceof Class || source == SourceProvider.UNKNOWN_SOURCE) {
      source = null;
    }
    Object convertedSource = Errors.convert(source);
    backingMap.computeIfAbsent(key, k -> LinkedHashMultiset.create()).add(convertedSource);

    // Avoid all the extra work if we can.
    if (state.parent().isPresent()) {
      Set<KeyAndSource> keyAndSources = evictionCache.getIfPresent(state);
      if (keyAndSources == null) {
        evictionCache.put(state, keyAndSources = Sets.newHashSet());
      }
      keyAndSources.add(new KeyAndSource(key, convertedSource));
    }
  }

  public boolean contains(Key<?> key) {
    evictionCache.cleanUp();
    return backingMap != null && backingMap.containsKey(key);
  }

  public Set<Object> getSources(Key<?> key) {
    evictionCache.cleanUp();
    Multiset<Object> sources = (backingMap == null) ? null : backingMap.get(key);
    return (sources == null) ? null : sources.elementSet();
  }

  private static final class KeyAndSource {
    final Key<?> key;
    final Object source;

    KeyAndSource(Key<?> key, Object source) {
      this.key = key;
      this.source = source;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(key, source);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }

      if (!(obj instanceof KeyAndSource)) {
        return false;
      }

      KeyAndSource other = (KeyAndSource) obj;
      return Objects.equal(key, other.key) && Objects.equal(source, other.source);
    }
  }
}
