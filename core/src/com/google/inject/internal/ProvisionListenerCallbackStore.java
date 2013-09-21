/**
 * Copyright (C) 2011 Google Inc.
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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Stage;
import com.google.inject.spi.ProvisionListener;
import com.google.inject.spi.ProvisionListenerBinding;

import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * {@link ProvisionListenerStackCallback} for each key.
 *
 * @author sameb@google.com (Sam Berlin)
 */
final class ProvisionListenerCallbackStore {

  // TODO(sameb): Consider exposing this in the API somehow?  Maybe?
  // Lots of code often want to skip over the internal stuffs.
  private static final Set<Key<?>> INTERNAL_BINDINGS =
      ImmutableSet.of(Key.get(Injector.class), Key.get(Stage.class), Key.get(Logger.class));
  
  private final ImmutableList<ProvisionListenerBinding> listenerBindings;

  private final LoadingCache<KeyBinding, ProvisionListenerStackCallback<?>> cache
      = CacheBuilder.newBuilder().build(
          new CacheLoader<KeyBinding, ProvisionListenerStackCallback<?>>() {
            public ProvisionListenerStackCallback<?> load(KeyBinding key) {
              return create(key.binding);
            }
          });

  ProvisionListenerCallbackStore(List<ProvisionListenerBinding> listenerBindings) {
    this.listenerBindings = ImmutableList.copyOf(listenerBindings);
  }

  /** Returns a new {@link ProvisionListenerStackCallback} for the key.
   */
  @SuppressWarnings("unchecked") // the ProvisionListenerStackCallback type always agrees with the passed type
  public <T> ProvisionListenerStackCallback<T> get(Binding<T> binding) {
    // Never notify any listeners for internal bindings.
    if (!INTERNAL_BINDINGS.contains(binding.getKey())) {
      return (ProvisionListenerStackCallback<T>) cache.getUnchecked(
          new KeyBinding(binding.getKey(), binding));
    }
    return ProvisionListenerStackCallback.emptyListener();
  }

  /**
   * Purges a key from the cache. Use this only if the type is not actually valid for
   * binding and needs to be purged. (See issue 319 and
   * ImplicitBindingTest#testCircularJitBindingsLeaveNoResidue and
   * #testInstancesRequestingProvidersForThemselvesWithChildInjectors for examples of when this is
   * necessary.)
   * 
   * Returns true if the type was stored in the cache, false otherwise.
   */
  boolean remove(Binding<?> type) {
    return cache.asMap().remove(type) != null;
  }

  /**
   * Creates a new {@link ProvisionListenerStackCallback} with the correct listeners
   * for the key.
   */
  private <T> ProvisionListenerStackCallback<T> create(Binding<T> binding) {
    List<ProvisionListener> listeners = null;
    for (ProvisionListenerBinding provisionBinding : listenerBindings) {
      if (provisionBinding.getBindingMatcher().matches(binding)) {
        if (listeners == null) {
          listeners = Lists.newArrayList();
        }
        listeners.addAll(provisionBinding.getListeners());
      }
    }
    if (listeners == null || listeners.isEmpty()) {
      // Optimization: don't bother constructing the callback if there are
      // no listeners.
      return ProvisionListenerStackCallback.emptyListener();
    }
    return new ProvisionListenerStackCallback<T>(binding, listeners);
  }
  
  /** A struct that holds key & binding but uses just key for equality/hashcode. */
  private static class KeyBinding {
    final Key<?> key;
    final Binding<?> binding;
    
    KeyBinding(Key<?> key, Binding<?> binding) {
      this.key = key;
      this.binding = binding;
    }
    
    @Override
    public boolean equals(Object obj) {
      return obj instanceof KeyBinding && key.equals(((KeyBinding)obj).key);
    }
    @Override
    public int hashCode() {
      return key.hashCode();
    }
  }
}
