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

import com.google.common.base.Objects;
import java.lang.reflect.Member;
import java.util.Collection;

/**
 * Node for types that have {@link com.google.inject.spi.Dependency}s and are bound to {@link
 * InterfaceNode}s. These nodes will often have fields for {@link Member}s that are {@link
 * com.google.inject.spi.InjectionPoint}s.
 *
 * @see DependencyEdge
 * @author phopkins@gmail.com (Pete Hopkins)
 * @since 4.0 (since 2.0 as an interface)
 */
public class ImplementationNode extends Node {
  private final Collection<Member> members;

  public ImplementationNode(NodeId id, Object source, Collection<Member> members) {
    super(id, source);
    this.members = members;
  }

  public Collection<Member> getMembers() {
    return members;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ImplementationNode)) {
      return false;
    }
    ImplementationNode other = (ImplementationNode) obj;
    return super.equals(other) && Objects.equal(members, other.members);
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + Objects.hashCode(members);
  }

  @Override
  public String toString() {
    return "ImplementationNode{id="
        + getId()
        + " source="
        + getSource()
        + " members="
        + members
        + "}";
  }

  @Override
  public Node copy(NodeId id) {
    return new ImplementationNode(id, getSource(), getMembers());
  }
}
