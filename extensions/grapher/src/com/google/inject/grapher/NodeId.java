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
import com.google.inject.Key;

/**
 * ID of a node in the graph. An ID is given by a {@link Key} and a node type, which is used to
 * distinguish instances and implementation classes for the same key. For example {@code
 * bind(Integer.class).toInstance(42)} produces two nodes: an interface node with the key of {@code
 * Key<Integer>} and an instance node with the same {@link Key} and value of 42.
 *
 * @author bojand@google.com (Bojan Djordjevic)
 * @since 4.0
 */
public final class NodeId {

  /**
   * Type of node.
   *
   * @since 4.0
   */
  public enum NodeType {
    /** Type or class node. */
    TYPE,

    /** Instance node, used when something is bound to an instance. */
    INSTANCE
  }

  private final Key<?> key;
  private final NodeType nodeType;

  private NodeId(Key<?> key, NodeType nodeType) {
    this.key = key;
    this.nodeType = nodeType;
  }

  public static NodeId newTypeId(Key<?> key) {
    return new NodeId(key, NodeType.TYPE);
  }

  public static NodeId newInstanceId(Key<?> key) {
    return new NodeId(key, NodeType.INSTANCE);
  }

  public Key<?> getKey() {
    return key;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(key, nodeType);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || !(obj.getClass().equals(NodeId.class))) {
      return false;
    }
    NodeId other = (NodeId) obj;
    return Objects.equal(key, other.key) && Objects.equal(nodeType, other.nodeType);
  }

  @Override
  public String toString() {
    return "NodeId{nodeType=" + nodeType + " key=" + key + "}";
  }
}
