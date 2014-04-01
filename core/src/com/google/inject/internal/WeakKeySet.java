/**
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
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.google.inject.Key;
import com.google.inject.internal.util.SourceProvider;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Set;

/**
 * Minimal set that doesn't hold strong references to the contained keys.
 *
 * @author dweis@google.com (Daniel Weis)
 */
final class WeakKeySet {

  /**
   * The key for the Map is {@link Key} for most bindings, String for multibindings.
   * <p>
   * Reason being that multibinding Key's annotations hold a reference to their injector, implying
   * we'd leak memory.
   */
  private Map<Object, Multiset<Object>> backingSet;
  
  /**
   * This is already locked externally on add and getSources but we need it to handle clean up in
   * the evictionCache's RemovalListener.
   */
  private final Object lock;

  /**
   * Tracks child injector lifetimes and evicts blacklisted keys/sources after the child injector is
   * garbage collected.
   */
  private final Cache<State, Set<KeyAndSource>> evictionCache = CacheBuilder.newBuilder()
      .weakKeys()
      .removalListener(
          new RemovalListener<State, Set<KeyAndSource>>() {
            @Override
            public void onRemoval(RemovalNotification<State, Set<KeyAndSource>> notification) {
              Preconditions.checkState(RemovalCause.COLLECTED.equals(notification.getCause()));

              cleanUpForCollectedState(notification.getValue());
            }
          })
      .build();

  /**
   * There may be multiple child injectors blacklisting a certain key so only remove the source
   * that's relevant.
   */
  private void cleanUpForCollectedState(Set<KeyAndSource> keysAndSources) {
    synchronized (lock) {
      
      for (KeyAndSource keyAndSource : keysAndSources) {
        Multiset<Object> set = backingSet.get(keyAndSource.mapKey);
        if (set != null) {
          set.remove(keyAndSource.source);
          if (set.isEmpty()) {
            backingSet.remove(keyAndSource.mapKey);
          }
        }
      }
    }
  }

  WeakKeySet(Object lock) {
    this.lock = lock;
  }

  public void add(Key<?> key, State state, Object source) {
    if (backingSet == null) {
      backingSet = Maps.newHashMap();
    }
    // if it's an instanceof Class, it was a JIT binding, which we don't
    // want to retain.
    if (source instanceof Class || source == SourceProvider.UNKNOWN_SOURCE) {
      source = null;
    }
    Object mapKey = toMapKey(key);
    Multiset<Object> sources = backingSet.get(mapKey);
    if (sources == null) {
      sources = LinkedHashMultiset.create();
      backingSet.put(mapKey, sources);
    }
    Object convertedSource = Errors.convert(source);
    sources.add(convertedSource);

    // Avoid all the extra work if we can.
    if (state.parent() != State.NONE) {
      Set<KeyAndSource> keyAndSources = evictionCache.getIfPresent(state);
      if (keyAndSources == null) {
        evictionCache.put(state, keyAndSources = Sets.newHashSet());
      }
      keyAndSources.add(new KeyAndSource(mapKey, convertedSource));
    }
  }

  public boolean contains(Key<?> key) {
    evictionCache.cleanUp();
    return backingSet != null && backingSet.containsKey(key);
  }

  public Set<Object> getSources(Key<?> key) {
    evictionCache.cleanUp();
    Multiset<Object> sources = backingSet.get(key);
    return (sources == null) ? null : sources.elementSet();
  }
  
  private static Object toMapKey(Key<?> key) {
    Annotation annotation = key.getAnnotation();
    if (annotation != null
        // HACK: See comment on backingSet for more info. This is tested in MultibinderTest,
        // MapBinderTest, and OptionalBinderTest in the multibinder test suite.
        && "com.google.inject.multibindings.RealElement".equals(annotation.getClass().getName())) {
      return key.toString();
    }
    return key;
  }

  private static final class KeyAndSource {
    final Object mapKey;
    final Object source;

    KeyAndSource(Object mapKey, Object source) {
      this.mapKey = mapKey;
      this.source = source;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(mapKey, source);
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
      return Objects.equal(mapKey, other.mapKey)
          && Objects.equal(source, other.source);
    }
  }
}
