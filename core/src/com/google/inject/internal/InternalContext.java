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

package com.google.inject.internal;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Key;
import com.google.inject.internal.InjectorImpl.InjectorOptions;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.DependencyAndSource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Internal context. Used to coordinate injections and support circular
 * dependencies.
 *
 * @author crazybob@google.com (Bob Lee)
 */
final class InternalContext {

  private final InjectorOptions options;

  private Map<Object, ConstructionContext<?>> constructionContexts = Maps.newHashMap();

  /** Keeps track of the type that is currently being requested for injection. */
  private Dependency<?> dependency;

  /** Keeps track of the hierarchy of types needed during injection. */
  private final DependencyStack state = new DependencyStack();

  InternalContext(InjectorOptions options) {
    this.options = options;
  }

  public InjectorOptions getInjectorOptions() {
    return options;
  }

  @SuppressWarnings("unchecked")
  public <T> ConstructionContext<T> getConstructionContext(Object key) {
    ConstructionContext<T> constructionContext
        = (ConstructionContext<T>) constructionContexts.get(key);
    if (constructionContext == null) {
      constructionContext = new ConstructionContext<T>();
      constructionContexts.put(key, constructionContext);
    }
    return constructionContext;
  }

  public Dependency<?> getDependency() {
    return dependency;
  }

  /** Sets the new current dependency & adds it to the state. */
  public Dependency<?> pushDependency(Dependency<?> dependency, Object source) {
    Dependency<?> previous = this.dependency;
    this.dependency = dependency;
    state.add(dependency, source);
    return previous;
  }

  /** Pops the current state & sets the new dependency. */
  public void popStateAndSetDependency(Dependency<?> newDependency) {
    state.pop();
    this.dependency = newDependency;
  }

  /** Adds to the state without setting the dependency. */
  public void pushState(Key<?> key, Object source) {
    state.add(key, source);
  }
  
  /** Pops from the state without setting a dependency. */
  public void popState() {
    state.pop();
  }

  /** Returns the current dependency chain (all the state). */
  public List<DependencyAndSource> getDependencyChain() {
    ImmutableList.Builder<DependencyAndSource> builder = ImmutableList.builder();
    for (int i = 0; i < state.size(); i += 2) {
      Object evenEntry = state.get(i);
      Dependency<?> dependency;
      if (evenEntry instanceof Key) {
        dependency = Dependency.get((Key<?>) evenEntry);
      } else {
        dependency = (Dependency<?>) evenEntry;
      }
      builder.add(new DependencyAndSource(dependency, state.get(i + 1)));
    }
    return builder.build();
  }

  /**
   * Keeps track of the hierarchy of types needed during injection.
   *
   * <p>This is a pairwise combination of dependencies and sources, with dependencies or keys on
   * even indices, and sources on odd indices. This structure is to avoid the memory overhead of
   * DependencyAndSource objects, which can add to several tens of megabytes in large applications.
   */
  private static final class DependencyStack {
    private Object[] elements = new Object[16];
    private int size = 0;

    public void add(Object dependencyOrKey, Object source) {
      if (elements.length < size + 2) {
        elements = Arrays.copyOf(elements, (elements.length*3)/2 + 2);
      }
      elements[size++] = dependencyOrKey;
      elements[size++] = source;
    }

    public void pop() {
      elements[--size] = null;
      elements[--size] = null;
    }

    public Object get(int i) {
      return elements[i];
    }

    public int size() {
      return size;
    }
  }
}
