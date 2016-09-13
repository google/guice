/*
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
 * Node for an interface type that has been bound to an implementation class or instance.
 *
 * @see BindingEdge
 * @author phopkins@gmail.com (Pete Hopkins)
 * @since 4.0 (since 2.0 as an interface)
 */
public class InterfaceNode extends Node {
  public InterfaceNode(NodeId id, Object source) {
    super(id, source);
  }

  @Override
  public Node copy(NodeId id) {
    return new InterfaceNode(id, getSource());
  }

  @Override
  public boolean equals(Object obj) {
    return (obj instanceof InterfaceNode) && super.equals(obj);
  }

  @Override
  public String toString() {
    return "InterfaceNode{id=" + getId() + " source=" + getSource() + "}";
  }
}
