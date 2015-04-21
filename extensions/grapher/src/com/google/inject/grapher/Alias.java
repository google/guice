/**
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

/**
 * Alias between two nodes. Causes the 'from' node to be aliased with the 'to' node, which means
 * that the 'from' node is not rendered and all edges going to it instead go to the 'to' node.
 *
 * @author bojand@google.com (Bojan Djordjevic)
 * @since 4.0
 */
public final class Alias {
  private final NodeId fromId;
  private final NodeId toId;

  public Alias(NodeId fromId, NodeId toId) {
    this.fromId = fromId;
    this.toId = toId;
  }

  public NodeId getFromId() {
    return fromId;
  }

  public NodeId getToId() {
    return toId;
  }
}
