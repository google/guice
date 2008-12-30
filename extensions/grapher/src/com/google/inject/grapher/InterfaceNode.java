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

/**
 * Node for an interface class that has been bound to an implementation class
 * or instance. These nodes are basically defined by a {@link Key}.
 *
 * @see BindingEdge
 *
 * @author phopkins@gmail.com (Pete Hopkins)
 *
 * @param <K> The type for node IDs.
 */
public interface InterfaceNode<K> {
  void setKey(Key<?> key);
  void setSource(Object source);

  /**
   * Factory interface for {@link InterfaceNode}s. Renderer implementations
   * will need to provide an implementation for this.
   *
   * @param <K> The type for node IDs.
   * @param <T> The {@link InterfaceNode} sub-type that this factory provides.
   */
  interface Factory<K, T extends InterfaceNode<K>> {
    /**
     * Creates a new {@link InterfaceNode} and adds it to the graph.
     *
     * @param nodeId ID for the node.
     * @return The new {@link InterfaceNode} instance.
     */
    T newInterfaceNode(K nodeId);
  }
}
