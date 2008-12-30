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

/**
 * Interface for an edge that connects an interface to the type or instance
 * that is bound to implement it.
 *
 * @author phopkins@gmail.com (Pete Hopkins)
 *
 * @param <K> The type for node IDs.
 */
public interface BindingEdge<K> {
  /**
   * Classification for what kind of binding this edge represents.
   */
  enum Type {
    /** Binding is to an instance or class of the binding's same type. */
    NORMAL,
    /** Binding is to an instance or class that provides the binding's type. */
    PROVIDER,
    /** Binding is to the interface for a constant of a different type. */
    CONVERTED_CONSTANT
  }

  void setType(Type type);

  /**
   * Factory interface for {@link BindingEdge}s. Renderer implementations will
   * need to provide an implementation for this.
   *
   * @param <K> The type for node IDs.
   * @param <T> The {@link BindingEdge} sub-type that this factory provides.
   */
  interface Factory<K, T extends BindingEdge<K>> {
    /**
     * Creates a new {@link BindingEdge} instance and adds it to the graph.
     *
     * @param fromId Node ID for the interface node.
     * @param toId Node ID for the implementation (class or instance) node.
     * @return The newly created and added {@link BindingEdge}.
     */
    T newBindingEdge(K fromId, K toId);
  }
}
