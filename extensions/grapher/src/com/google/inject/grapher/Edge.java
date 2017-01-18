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
 * Edge in a guice dependency graph.
 *
 * @author bojand@google.com (Bojan Djordjevic)
 * @since 4.0
 */
public abstract class Edge {
  private final NodeId fromId;
  private final NodeId toId;

  protected Edge(NodeId fromId, NodeId toId) {
    this.fromId = fromId;
    this.toId = toId;
  }

  public NodeId getFromId() {
    return fromId;
  }

  public NodeId getToId() {
    return toId;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Edge)) {
      return false;
    }
    Edge other = (Edge) obj;
    return Objects.equal(fromId, other.fromId) && Objects.equal(toId, other.toId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(fromId, toId);
  }

  /**
   * Returns a copy of the edge with new node IDs.
   *
   * @param fromId new ID of the 'from' node
   * @param toId new ID of the 'to' node
   * @return copy of the edge with the new node IDs
   */
  public abstract Edge copy(NodeId fromId, NodeId toId);
}
