/*
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

package com.google.inject.internal;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.inject.Provider;
import com.google.inject.spi.Dependency;
import java.lang.invoke.MethodHandle;
import javax.annotation.Nullable;

/**
 * Creates objects which will be injected.
 *
 * @author crazybob@google.com (Bob Lee)
 */
abstract class InternalFactory<T> {

  /**
   * Accessed in a double checked manner and updated under the `this` lock. See {@link
   * HandleCache#getHandleAndMaybeUpdateCache(boolean, MethodHandleResult)}
   */
  private volatile HandleCache handleCache = HandleCache.EMPTY;

  /**
   * Creates an object to be injected.
   *
   * @param context of this injection
   * @param linked true if getting as a result of a linked binding
   * @throws com.google.inject.internal.InternalProvisionException if a value cannot be provided
   * @return instance that was created
   */
  abstract T get(InternalContext context, Dependency<?> dependency, boolean linked)
      throws InternalProvisionException;

  /** Returns a provider for the object to be injected. */
  Provider<T> makeProvider(InjectorImpl injector, Dependency<?> dependency) {
    return makeDefaultProvider(this, injector, dependency);
  }

  /**
   * Returns the method handle for the object to be injected.
   *
   * <p>The signature of the method handle is {@code (InternalContext) -> T} where `T` is the type
   * of the object to be injected as determined by the {@link Dependency}.
   *
   * @param context the linkage context
   * @param linked true if getting as a result of a linked binding
   * @return the method handle for the object to be injected
   */
  final MethodHandle getHandle(LinkageContext context, boolean linked) {
    var currentCache = handleCache;
    var local = currentCache.getHandle(linked);
    if (local != null) {
      return local;
    }
    // NOTE: we do not hold a lock while actually constructing the handle, instead we atomically
    // update the handleCache using double-checked locking.  The `updateCache` methods ensure that
    // we converge quickly on a terminal state so we will method will simply ensure that we only
    // save one.  The reason for this is that the `getHandle` method can be invoked by multiple
    // threads concurrently and can be re-entrant. For a single thread the re-entrancy is managed by
    // the `LinkageContext` class but for multiple threads do not have a solution. So holding a lock
    // while constructing handles could lead to a deadlock.
    // Instead we just ensure that exactly one handle is actually stored and races instead just
    // create garbage handles.
    var result = context.makeHandle(this, linked);
    return HandleCache.getHandleAndMaybeUpdateCache(this, linked, result);
  }

  /**
   * Produce the method handle for the object to be injected.
   *
   * @param context should be propagated to delegate {@link #getHandle(LinkageContext, boolean)}
   *     calls
   * @param linked true if getting as a result of a linked binding
   * @return a {@link MethodHandleResult} containing the method handle and its cachability settings
   */
  abstract MethodHandleResult makeHandle(LinkageContext context, boolean linked);

  static MethodHandleResult makeCachable(MethodHandle methodHandle) {
    return new MethodHandleResult(methodHandle, MethodHandleResult.Cachability.ALWAYS);
  }

  static MethodHandleResult makeCachableOnLinkedSetting(MethodHandle methodHandle) {
    return new MethodHandleResult(methodHandle, MethodHandleResult.Cachability.ON_LINKED_SETTING);
  }

  static MethodHandleResult makeUncachable(MethodHandle methodHandle) {
    return new MethodHandleResult(methodHandle, MethodHandleResult.Cachability.NEVER);
  }

  static final class MethodHandleResult {
    static enum Cachability {
      ALWAYS,
      ON_LINKED_SETTING,
      NEVER,
    }

    final MethodHandle methodHandle;
    final Cachability cachability;

    private MethodHandleResult(MethodHandle methodHandle, Cachability cachability) {
      this.methodHandle = methodHandle;
      this.cachability = cachability;
    }
  }

  /**
   * Returns a provider for the object to be injected using the given factory.
   *
   * <p>This delegates to the {@link #get} method.
   */
  static <T> Provider<T> makeDefaultProvider(
      InternalFactory<T> factory, InjectorImpl injector, Dependency<?> dependency) {
    if (InternalFlags.getUseMethodHandlesOption()) {
      return InternalMethodHandles.makeProvider(factory, injector, dependency);
    }
    return new DefaultProvider<>(factory, injector, dependency);
  }

  /** Returns a provider for the object to be injected, handling nullable values. */
  static <T> Provider<T> makeProviderForNullable(
      T instance, InternalFactory<T> factory, Dependency<?> dependency, Object source) {
    if (instance == null) {
      return makeProviderForNull(source, factory, dependency);
    }
    return makeProviderFor(instance, factory);
  }

  /** Returns a provider for the object to be injected, handling literal {@code null} values. */
  static <T> Provider<T> makeProviderForNull(
      Object source, InternalFactory<T> factory, Dependency<?> dependency) {
    if (dependency.isNullable()) {
      return new InstanceProvider<>(null, factory);
    }
    return new NullProvider<>(source, factory, dependency);
  }

  /** Returns a provider for the object to be injected, handling non-nullable values. */
  static <T> Provider<T> makeProviderFor(T instance, InternalFactory<T> factory) {
    checkNotNull(instance);
    return new InstanceProvider<>(instance, factory);
  }

  static class DefaultProvider<T> implements Provider<T> {
    private final InternalFactory<T> factory;
    private final InjectorImpl injector;
    private final Dependency<?> dependency;

    DefaultProvider(InternalFactory<T> factory, InjectorImpl injector, Dependency<?> dependency) {
      this.factory = factory;
      this.injector = injector;
      this.dependency = dependency;
    }

    @Override
    public T get() {
      InternalContext currentContext = injector.enterContext();
      try {
        return factory.get(currentContext, dependency, false);
      } catch (InternalProvisionException e) {
        throw e.addSource(dependency).toProvisionException();
      } finally {
        currentContext.close();
      }
    }

    @Override
    public String toString() {
      return factory.toString();
    }
  }

  static class NullProvider<T> implements Provider<T> {
    private final Object source;
    private final InternalFactory<T> factory;
    private final Dependency<?> dependency;

    NullProvider(Object source, InternalFactory<T> factory, Dependency<?> dependency) {
      this.source = source;
      this.factory = factory;
      this.dependency = dependency;
    }

    @Override
    public T get() {
      try {
        InternalProvisionException.onNullInjectedIntoNonNullableDependency(source, dependency);
        return null;
      } catch (InternalProvisionException e) {
        throw e.addSource(dependency).toProvisionException();
      }
    }

    @Override
    public String toString() {
      return factory.toString();
    }
  }

  static class InstanceProvider<T> implements Provider<T> {
    private final T instance;
    private final InternalFactory<T> factory;

    InstanceProvider(T instance, InternalFactory<T> factory) {
      this.instance = instance;
      this.factory = factory;
    }

    @Override
    public T get() {
      return instance;
    }

    @Override
    public String toString() {
      return factory.toString();
    }
  }

  /**
   * A small latice of classes that can handle the cache transitions that may occur.
   *
   * <p>Each cache object is immutable and updates require creating new cache objects.
   */
  private abstract static class HandleCache {

    static MethodHandle getHandleAndMaybeUpdateCache(
        InternalFactory<?> factory, boolean linked, MethodHandleResult result) {
      if (result.cachability == MethodHandleResult.Cachability.NEVER) {
        return result.methodHandle;
      }

      // Update the cache under the factory lock to ensure that we pick a consistent winner.
      // NOTE: we could perform the cache updates outside of the lock and use a CAS style pattern
      // but that seems like overkill since the `updateCache` methods are very fast and simple.
      HandleCache cache;
      synchronized (factory) {
        cache = factory.handleCache.updateCache(linked, result);
        factory.handleCache = cache;
      }
      return cache.getHandle(linked);
    }

    static final HandleCache EMPTY = new EmptyCache();

    /** Returns the cached handle or `null` matching the {@code linked} setting. */
    @Nullable
    abstract MethodHandle getHandle(boolean linked);

    /**
     * Updates the cache using the given result for the {@code linked} setting.
     *
     * <p>Returns a new cache object that should be used to update the cache.
     */
    abstract HandleCache updateCache(boolean linked, MethodHandleResult result);

    /** An always empty cache, suitable for starting the cache process. */
    private static final class EmptyCache extends HandleCache {
      @Nullable
      @Override
      MethodHandle getHandle(boolean linked) {
        return null;
      }

      @Override
      HandleCache updateCache(boolean linked, MethodHandleResult result) {
        switch (result.cachability) {
          case NEVER:
            throw new IllegalArgumentException("Caller should have handled NEVER");
          case ON_LINKED_SETTING:
            return new PartialLinkedCache(linked, result.methodHandle);
          case ALWAYS:
            return new AlwaysCache(result.methodHandle);
        }
        throw new AssertionError("Unsupported cachability: " + result.cachability);
      }
    }

    /**
     * A cache that is only partially filled with a handle that is sensitive the the `{@code linked}
     * setting.
     */
    private static final class PartialLinkedCache extends HandleCache {
      private final boolean linked;
      private final MethodHandle handle;

      PartialLinkedCache(boolean linked, MethodHandle result) {
        this.linked = linked;
        this.handle = result;
      }

      @Nullable
      @Override
      MethodHandle getHandle(boolean linked) {
        return linked == this.linked ? handle : null;
      }

      @Override
      HandleCache updateCache(boolean linked, MethodHandleResult result) {
        checkArgument(
            result.cachability == MethodHandleResult.Cachability.ON_LINKED_SETTING,
            "Once a cache has transitioned to ON_LINKED_SETTING racy updates shouldn't have a"
                + " different setting, got %s",
            result.cachability);
        var thisLinked = this.linked;
        if (linked == thisLinked) {
          return this; // caller lost a race condition
        }
        if (thisLinked) {
          return new FullLinkedCache(handle, result.methodHandle);
        }
        return new FullLinkedCache(result.methodHandle, handle);
      }
    }

    /** A full cache for handles that are sensitive to the {@code linked} setting. */
    private static final class FullLinkedCache extends HandleCache {
      private final MethodHandle linkedHandle;
      private final MethodHandle notLinkedHandle;

      FullLinkedCache(MethodHandle linkedHandle, MethodHandle notLinkedHandle) {
        this.linkedHandle = linkedHandle;
        this.notLinkedHandle = notLinkedHandle;
      }

      @Override
      MethodHandle getHandle(boolean linked) {
        return linked ? linkedHandle : notLinkedHandle;
      }

      @Override
      HandleCache updateCache(boolean linked, MethodHandleResult result) {
        checkArgument(
            result.cachability == MethodHandleResult.Cachability.ON_LINKED_SETTING,
            "Once a cache has transitioned to ON_LINKED_SETTING racy updates shouldn't have a"
                + " different setting, got %s",
            result.cachability);
        // Ignore the result since we are always cachable and already cached.
        return this;
      }
    }

    /** A cache holding a handle that is not sensitive to the {@code linked} setting. */
    private static final class AlwaysCache extends HandleCache {
      private final MethodHandle handle;

      AlwaysCache(MethodHandle handle) {
        this.handle = handle;
      }

      @Override
      MethodHandle getHandle(boolean linked) {
        return handle;
      }

      @Override
      HandleCache updateCache(boolean linked, MethodHandleResult result) {
        checkArgument(
            result.cachability == MethodHandleResult.Cachability.ALWAYS,
            "Once a cache has transitioned to ALWAYS racy updatee shouldn't have a different"
                + " setting, got %s",
            result.cachability);
        // Ignore the result since we are always cachable and already cached.
        return this;
      }
    }
  }
}
