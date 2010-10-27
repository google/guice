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

import com.google.inject.internal.util.Nullable;
import com.google.inject.spi.InjectionPoint;

/**
 * Interface for an edge from a class or {@link InjectionPoint} to the
 * interface node that will satisfy the dependency.
 *
 * @author phopkins@gmail.com (Pete Hopkins)
 *
 * @param <K> The type for node IDs.
 */
public interface DependencyEdge<K> {
  /**
   * Factory interface for {@link DependencyEdge}s. Renderer implementations
   * will need to provide an implementation for this.
   *
   * @param <K> The type for node IDs.
   * @param <T> The {@link DependencyEdge} sub-type that this factory provides.
   */
  interface Factory<K, T extends DependencyEdge<K>> {
    /**
     * Creates a new {@link DependencyEdge} and adds it to the graph.
     *
     * @param fromId The ID for the class or instance node that has the
     *     dependency.
     * @param fromPoint The point where the dependency will be
     *     {@literal @}{@link Inject}ed. 
     * @param toId The ID for the interface node that satisfies the dependency.
     */
    T newDependencyEdge(K fromId, @Nullable InjectionPoint fromPoint, K toId);
  }
}
