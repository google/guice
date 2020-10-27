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

/**
 * Internal context. Used to coordinate injections and support circular dependencies.
 *
 * @author crazybob@google.com (Bob Lee)
 */
final class InternalContext implements AutoCloseable {

  private final InjectorOptions options;

  private final IdentityHashMap<Object, ConstructionContext<?>> constructionContexts =
      new IdentityHashMap<>();

  /** Keeps track of the type that is currently being requested for injection. */
  private Dependency<?> dependency;

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

  /**
   * Used to set the current dependency.
   *
   * <p>The currentDependency field is only used by InternalFactoryToProviderAdapter to propagate
   * information to singleton scope. See comments in that class about alternatives.
   */
  void setDependency(Dependency<?> dependency) {
    this.dependency = dependency;
  }
}
