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

import com.google.inject.Key;

import java.lang.reflect.Member;

/**
 * Node for classes and instances that have {@link Dependency}s and are
 * bound to {@link InterfaceNode}s. These nodes will often have fields for
 * {@link Member}s that are {@link InjectionPoint}s.
 * 
 * @see DependencyEdge
 *
 * @author phopkins@gmail.com (Pete Hopkins)
 *
 * @param <K> The type for node IDs.
 */
public interface ImplementationNode<K> {
  /**
   * Sets the {@link Key} that this node is for. Used when the node is
   * representing a class that Guice will instantiate.
   */
  void setClassKey(Key<?> key);

  /**
   * Sets the {@link Object} that's the already-created instance. Used when
   * this node is represeting the instance instead of a class.
   */
  void setInstance(Object instance);

  void setSource(Object source);
  void addMember(Member member);

  /**
   * Factory interface for {@link ImplementationNode}s. Renderer
   * implementations will need to provide an implementation for this.
   *
   * @param <K> The type for node IDs.
   * @param <T> The {@link ImplementationNode} sub-type that this factory
   *     provides.
   */
  interface Factory<K, T extends ImplementationNode<K>> {
    /**
     * Creates a new {@link ImplementationNode} and adds it to the graph.
     *
     * @param nodeId ID for the node.
     * @return The new {@link ImplementationNode} instance.
     */
    T newImplementationNode(K nodeId);
  }
}
