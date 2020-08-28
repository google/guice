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

import com.google.inject.internal.InjectorImpl.InjectorOptions;
import com.google.inject.spi.Dependency;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Internal context. Used to coordinate injections and support circular dependencies.
 *
 * @author crazybob@google.com (Bob Lee)
 */
final class InternalContext implements AutoCloseable {

  private final InjectorOptions options;

  private final Map<Object, ConstructionContext<?>> constructionContexts =
      new IdentityHashMap<Object, ConstructionContext<?>>();

  /** Keeps track of the type that is currently being requested for injection. */
  private Dependency<?> dependency;

  /**
   * Keeps track of the hierarchy of types needed during injection.
   *
   * <p>This is a pairwise combination of dependencies and sources, with dependencies or keys on
   * even indices, and sources on odd indices. This structure is to avoid the memory overhead of
   * DependencyAndSource objects, which can add to several tens of megabytes in large applications.
   */
  private Object[] dependencyStack = new Object[16];

  private int dependencyStackSize = 0;

  /**
   * The number of times {@link #enter()} has been called + 1 for initial construction. This value
   * is decremented when {@link #exit()} is called.
   */
  private int enterCount;

  /**
   * A single element array to clear when the {@link #enterCount} hits {@code 0}.
   *
   * <p>This is the value stored in the {@code InjectorImpl.localContext} thread local.
   */
  private final Object[] toClear;

  InternalContext(InjectorOptions options, Object[] toClear) {
    this.options = options;
    this.toClear = toClear;
    this.enterCount = 1;
  }

  /** Should only be called by InjectorImpl.enterContext(). */
  void enter() {
    enterCount++;
  }

  /** Should be called any any method that received an instance via InjectorImpl.enterContext(). */
  @Override
  public void close() {
    int newCount = --enterCount;
    if (newCount < 0) {
      throw new IllegalStateException("Called close() too many times");
    }
    if (newCount == 0) {
      toClear[0] = null;
    }
  }

  InjectorOptions getInjectorOptions() {
    return options;
  }

  @SuppressWarnings("unchecked")
  <T> ConstructionContext<T> getConstructionContext(Object key) {
    ConstructionContext<T> constructionContext =
        (ConstructionContext<T>) constructionContexts.get(key);
    if (constructionContext == null) {
      constructionContext = new ConstructionContext<>();
      constructionContexts.put(key, constructionContext);
    }
    return constructionContext;
  }

  Dependency<?> getDependency() {
    return dependency;
  }

  /** Sets the new current dependency & adds it to the state. */
  Dependency<?> pushDependency(Dependency<?> dependency, Object source) {
    Dependency<?> previous = this.dependency;
    this.dependency = dependency;
    doPushState(dependency, source);
    return previous;
  }

  /** Pops the current state & sets the new dependency. */
  void popStateAndSetDependency(Dependency<?> newDependency) {
    popState();
    this.dependency = newDependency;
  }

  /** Adds to the state without setting the dependency. */
  void pushState(com.google.inject.Key<?> key, Object source) {
    doPushState(key, source);
  }

  private void doPushState(Object dependencyOrKey, Object source) {
    int localSize = dependencyStackSize;
    Object[] localStack = dependencyStack;
    if (localStack.length < localSize + 2) {
      localStack = dependencyStack =
        java.util.Arrays.copyOf(localStack, (localStack.length * 3) / 2 + 2);
    }
    localStack[localSize++] = dependencyOrKey;
    localStack[localSize++] = source;
    dependencyStackSize = localSize;
  }

  /** Pops from the state without setting a dependency. */
  void popState() {
    // N.B. we don't null out the array entries.  It isn't necessary since all the objects in the
    // array (Key, Dependency, or Binding source objects) are all tied to the lifetime of the
    // injector, which is greater than the lifetime of this object.  So removing them from the array
    // doesn't matter.
    dependencyStackSize -= 2;
  }

  /** Returns the current dependency chain (all the state stored in the dependencyStack). */
  java.util.List<com.google.inject.spi.DependencyAndSource> getDependencyChain() {
    com.google.common.collect.ImmutableList.Builder<com.google.inject.spi.DependencyAndSource>
        builder = com.google.common.collect.ImmutableList.builder();
    for (int i = 0; i < dependencyStackSize; i += 2) {
      Object evenEntry = dependencyStack[i];
      Dependency<?> dependency;
      if (evenEntry instanceof com.google.inject.Key) {
        dependency = Dependency.get((com.google.inject.Key<?>) evenEntry);
      } else {
        dependency = (Dependency<?>) evenEntry;
      }
      builder.add(new com.google.inject.spi.DependencyAndSource(dependency, dependencyStack[i + 1]));
    }
    return builder.build();
  }
}
