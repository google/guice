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

import com.google.inject.Key;
import com.google.inject.internal.util.ImmutableList;
import com.google.inject.internal.util.Maps;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.DependencyAndSource;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Internal context. Used to coordinate injections and support circular
 * dependencies.
 *
 * @author crazybob@google.com (Bob Lee)
 */
final class InternalContext {

  private Map<Object, ConstructionContext<?>> constructionContexts = Maps.newHashMap();
  /** Keeps track of the type that is currently being requested for injection. */
  private Dependency dependency;
  /** Keeps track of the hierarchy of types needed during injection. */
  private LinkedList<DependencyAndSource> state = new LinkedList<DependencyAndSource>();

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

  public Dependency getDependency() {
    return dependency;
  }

  /** Sets the new current dependency & adds it to the state. */
  public Dependency pushDependency(Dependency dependency, Object source) {
    Dependency previous = this.dependency;
    this.dependency = dependency;
    state.addLast(new DependencyAndSource(dependency, source));
    return previous;
  }
  
  /** Pops the current state & sets the new dependency. */
  public void popStateAndSetDependency(Dependency newDependency) {
    popState();
    this.dependency = newDependency;
  }
  
  /** Adds to the state without setting the dependency. */
  public void pushState(Key<?> key, Object source) {
    state.addLast(new DependencyAndSource(key == null ? null : Dependency.get(key), source));
  }
  
  /** Pops from the state without setting a dependency. */
  public void popState() {
    state.removeLast();
  }
  
  /** Returns the current dependency chain (all the state). */
  public List<DependencyAndSource> getDependencyChain() {
    return ImmutableList.copyOf(state);
  }
}
