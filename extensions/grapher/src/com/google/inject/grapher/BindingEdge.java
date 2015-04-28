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

import com.google.common.base.Objects;

/**
 * Edge that connects an interface to the type or instance that is bound to implement it.
 *
 * @author phopkins@gmail.com (Pete Hopkins)
 * @since 4.0 (since 2.0 as an interface)
 */
public class BindingEdge extends Edge {
  /**
   * Classification for what kind of binding this edge represents.
   */
  public enum Type {
    /** Binding is to an instance or class of the binding's same type. */
    NORMAL,
    /** Binding is to an instance or class that provides the binding's type. */
    PROVIDER,
    /** Binding is to the interface for a constant of a different type. */
    CONVERTED_CONSTANT
  }

  private final Type type;

  public BindingEdge(NodeId fromId, NodeId toId, Type type) {
    super(fromId, toId);
    this.type = type;
  }

  public Type getType() {
    return type;
  }

  @Override public boolean equals(Object obj) {
    if (!(obj instanceof BindingEdge)) {
      return false;
    }
    BindingEdge other = (BindingEdge) obj;
    return super.equals(other) && Objects.equal(type, other.type);
  }

  @Override public int hashCode() {
    return 31 * super.hashCode() + Objects.hashCode(type);
  }

  @Override public String toString() {
    return "BindingEdge{fromId=" + getFromId() + " toId=" + getToId() + " type=" + type + "}";
  }

  @Override public Edge copy(NodeId fromId, NodeId toId) {
    return new BindingEdge(fromId, toId, type);
  }
}
