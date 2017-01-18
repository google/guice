/*
 * Copyright (C) 2011 Google Inc.
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

import com.google.common.base.Objects;

/**
 * Node in a guice dependency graph.
 *
 * @author bojand@google.com (Bojan Djordjevic)
 * @since 4.0
 */
public abstract class Node {
  /**
   * When set to true, the source object is ignored in {@link #equals} and {@link #hashCode}. Only
   * used in tests.
   */
  static boolean ignoreSourceInComparisons = false;

  private final NodeId id;
  private final Object source;

  protected Node(NodeId id, Object source) {
    this.id = id;
    this.source = source;
  }

  public NodeId getId() {
    return id;
  }

  public Object getSource() {
    return source;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Node)) {
      return false;
    }
    Node other = (Node) obj;
    return Objects.equal(id, other.id)
        && (ignoreSourceInComparisons || Objects.equal(source, other.source));
  }

  @Override
  public int hashCode() {
    return ignoreSourceInComparisons ? id.hashCode() : Objects.hashCode(id, source);
  }

  /**
   * Returns a copy of the node with a new ID.
   *
   * @param id new ID of the node
   * @return copy of the node with a new ID
   */
  public abstract Node copy(NodeId id);
}
