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

package com.google.inject.grapher;

import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.internal.util.Sets;
import com.google.inject.spi.BindingTargetVisitor;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Root class for graphing an {@link Injector}. Bound in {@link GrapherModule}.
 * <p>
 * Use {@link #of(Injector)} to specify the {@link Injector} to use, and
 * {@link graph()} to graph the {@link Injector} using the currently-bound
 * {@link Renderer}.
 * <p>
 * By default, this will graph the entire {@link Injector}. Use
 * {@link #rootedAt(Class...)} or {@link #rootedAt(Key...)} to specify an
 * initial set of {@link Class}es or {@link Key}s to use, and this will graph
 * their transitive bindings and dependencies.
 *
 * @author phopkins@gmail.com (Pete Hopkins)
 */
public class InjectorGrapher {  
  private static final Key<Logger> loggerKey = Key.get(Logger.class);

  private final BindingTargetVisitor<Object, Collection<Key<?>>> keyVisitor;
  private final BindingTargetVisitor<Object, Void> graphingVisitor;
  private final Renderer renderer;
  
  private Injector injector;
  private Set<Key<?>> root;
  
  @Inject
  public InjectorGrapher(BindingTargetVisitor<Object, Collection<Key<?>>> keyVisitor,
      BindingTargetVisitor<Object, Void> graphingVisitor, Renderer renderer) {
    this.keyVisitor = keyVisitor;
    this.graphingVisitor = graphingVisitor;
    this.renderer = renderer;
  }

  /**
   * Sets the {@link Injector} to graph.
   */
  public InjectorGrapher of(Injector injector) {
    this.injector = injector;
    return this;
  }

  /**
   * Sets an initial group of {@link Class}es to use as the starting point for
   * the graph. The graph will be of these classes and their transitive
   * dependencies and bindings.
   */
  public InjectorGrapher rootedAt(Class<?>... classes) {
    this.root = Sets.newHashSet();
    
    for (Class<?> clazz : classes) {
      this.root.add(Key.get(clazz));
    }
    
    return this;
  }

  /**
   * Sets an initial group of {@link Key}s to use as the starting point for
   * the graph. The graph will be of these keys and their transitive
   * dependencies and bindings.
   */
  public InjectorGrapher rootedAt(Key<?>... keys) {
    this.root = Sets.newHashSet();

    for (Key<?> key : keys) {
      this.root.add(key);
    }

    return this;
  }

  /**
   * Renders a graph with the bound {@link Renderer}. The {@link Injector}
   * must have already been specified with {@link #of(Injector)}.
   */
  public void graph() throws IOException {
    processBindings();
    renderer.render();
  }

  /**
   * Tests for {@link Key}s that we don't want to include by default in the
   * graph. They're left out of the initial set, but will appear if depended
   * upon by other classes. Leaves out Guice classes (such as the
   * {@link Injector}) and the {@link Logger}.
   */
  private boolean skipKey(Key<?> key) {
    return key.getTypeLiteral().getRawType().getPackage() == Guice.class.getPackage()
        || loggerKey.equals(key);
  }

  /**
   * Takes the set of starting {@link Binding}s, which comes either from the
   * {@link Injector} or from {@link #rootedAt(Class...)}, and applies the
   * {@link #graphingVisitor} to them. Uses the {@link #keyVisitor} to build
   * out the set of {@link Key}s so that the graph covers the transitive
   * dependencies and bindings.
   */
  private void processBindings() {
    Set<Key<?>> keys = Sets.newLinkedHashSet();
    Set<Key<?>> visitedKeys = Sets.newHashSet();

    // build up the root set from the Injector if it wasn't specified
    if (root == null) {
      for (Key<?> key : injector.getBindings().keySet()) {
        if (!skipKey(key)) {
          keys.add(key);
        }
      }
    } else {
      keys.addAll(root);
    }
    
    while (!keys.isEmpty()) {
      Iterator<Key<?>> iterator = keys.iterator();
      Key<?> key = iterator.next();
      iterator.remove();
      
      if (visitedKeys.contains(key)) {
        continue;
      }
      
      Binding<?> binding = injector.getBinding(key);
      visitedKeys.add(key);

      binding.acceptTargetVisitor(graphingVisitor);

      // find the dependencies and make sure that they get visited
      Collection<Key<?>> newKeys = binding.acceptTargetVisitor(keyVisitor);
      if (newKeys != null) {
        keys.addAll(newKeys);
      }
    }
  }
}
