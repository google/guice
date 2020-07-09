package com.google.inject.internal;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Key;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** A container for just-in-time (JIT) binding data corresponding to an Injector. */
final class InjectorJitBindingData {
  // TODO(b/159459925): the State object is currently used as the lock for accessing all the JIT
  //   binding data fields. Migrate to using InjectorJitBindingData as a lock for these accesses
  //   instead.
  // TODO(b/159459925): Hide direct access to internal jitBindings and failedJitBindings fields once
  //  locks are managed by this class
  /** Just-in-time binding cache. Guarded by state.lock() */
  private final Map<Key<?>, BindingImpl<?>> jitBindings = Maps.newHashMap();
  /**
   * Cache of Keys that we were unable to create JIT bindings for, so we don't keep trying. Also
   * guarded by state.lock().
   */
  private final Set<Key<?>> failedJitBindings = Sets.newHashSet();

  // The set of JIT binding keys that are banned for this particular injector, because a binding
  // already exists in a child injector.
  private final WeakKeySet bannedKeys;

  // The InjectorJitBindingData corresponding to the Injector's parent, if it exists.
  private final Optional<InjectorJitBindingData> parent;

  InjectorJitBindingData(Optional<InjectorJitBindingData> parent, State state) {
    this.parent = parent;
    this.bannedKeys = new WeakKeySet(state.lock());
  }

  /**
   * Returns a mutable map containing the JIT bindings for the injector. Accesses to this need to be
   * guarded by state.lock().
   */
  Map<Key<?>, BindingImpl<?>> getJitBindings() {
    return jitBindings;
  }

  /**
   * Returns a mutable set containing the failed JIT bindings for the injector. Accesses to this
   * need to be guarded by state.lock().
   */
  Set<Key<?>> getFailedJitBindings() {
    return failedJitBindings;
  }

  /**
   * Forbids the corresponding injector and its ancestors from creating a binding to {@code key}.
   * Child injectors ban their bound keys on their parent injectors to prevent just-in-time bindings
   * on the parent injector that would conflict, and pass along their State to control the banned
   * key's lifetime.
   */
  void banKey(Key<?> key, State state, Object source) {
    banKeyInParent(key, state, source);
    bannedKeys.add(key, state, source);
  }

  /**
   * Similar to {@link #banKey(Key, State, Object)} but we only begin banning the binding at the
   * parent level. This is used to prevent JIT bindings in the parent injector from overriding
   * explicit bindings declared in a child injector.
   */
  void banKeyInParent(Key<?> key, State state, Object source) {
    if (parent.isPresent()) {
      parent.get().banKey(key, state, source);
    }
  }

  /**
   * Returns true if {@code key} is forbidden from being bound in the injector corresponding to this
   * data object. This indicates that one of the injector's children has bound the key.
   */
  boolean isBannedKey(Key<?> key) {
    return bannedKeys.contains(key);
  }

  /** Returns the source of a banned key. */
  Set<Object> getSourcesForBannedKey(Key<?> key) {
    return bannedKeys.getSources(key);
  }
}
