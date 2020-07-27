package com.google.inject.internal;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Key;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A container for most just-in-time (JIT) binding data corresponding to an Injector. It
 * includes @Inject constructor bindings. It does not include {@link MembersInjectorStore} or {@link
 * ProvisionListenerCallbackStore}, which are still stored in {@link InjectorImpl}.
 */
final class InjectorJitBindingData {
  /** Just-in-time binding cache. Guarded by {@link #lock}. */
  private final Map<Key<?>, BindingImpl<?>> jitBindings = Maps.newHashMap();
  /**
   * Cache of Keys that we were unable to create JIT bindings for, so we don't keep trying. Guarded
   * by {@link #lock}.
   */
  private final Set<Key<?>> failedJitBindings = Sets.newHashSet();

  // The set of JIT binding keys that are banned for this particular injector, because a binding
  // already exists in a child injector. Guarded by {@link #lock}.
  private final WeakKeySet bannedKeys;

  // The InjectorJitBindingData corresponding to the Injector's parent, if it exists.
  private final Optional<InjectorJitBindingData> parent;

  /**
   * This lock is needed for threadsafe InjectorJitBindingData accesses. It corresponds to this
   * InjectorJitBindingData's highest ancestor.
   */
  private final Object lock;

  InjectorJitBindingData(Optional<InjectorJitBindingData> parent) {
    this.parent = parent;
    this.lock = parent.isPresent() ? parent.get().lock() : this;
    this.bannedKeys = new WeakKeySet(lock);
  }

  Map<Key<?>, BindingImpl<?>> getJitBindings() {
    return Collections.unmodifiableMap(jitBindings);
  }

  BindingImpl<?> getJitBinding(Key<?> key) {
    return jitBindings.get(key);
  }

  void putJitBinding(Key<?> key, BindingImpl<?> binding) {
    jitBindings.put(key, binding);
  }

  void removeJitBinding(Key<?> key) {
    jitBindings.remove(key);
  }

  boolean isFailedJitBinding(Key<?> key) {
    return failedJitBindings.contains(key);
  }

  void addFailedJitBinding(Key<?> key) {
    failedJitBindings.add(key);
  }

  /**
   * Forbids the corresponding injector and its ancestors from creating a binding to {@code key}.
   * Child injectors ban their bound keys on their parent injectors to prevent just-in-time bindings
   * on the parent injector that would conflict, and pass along their InjectorBindingData to control
   * the banned key's lifetime.
   */
  void banKey(Key<?> key, InjectorBindingData injectorBindingData, Object source) {
    banKeyInParent(key, injectorBindingData, source);
    bannedKeys.add(key, injectorBindingData, source);
  }

  /**
   * Similar to {@link #banKey(Key, InjectorBindingData, Object)} but we only begin banning the
   * binding at the parent level. This is used to prevent JIT bindings in the parent injector from
   * overriding explicit bindings declared in a child injector.
   */
  void banKeyInParent(Key<?> key, InjectorBindingData injectorBindingData, Object source) {
    if (parent.isPresent()) {
      parent.get().banKey(key, injectorBindingData, source);
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

  Object lock() {
    return lock;
  }
}
