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
import java.lang.reflect.Member;

/**
 * Node for instances. Used when a type is bound to an instance.
 *
 * @author bojand@google.com (Bojan Djordjevic)
 * @since 4.0
 */
public class InstanceNode extends Node {
  private final Object instance;
  private final Iterable<Member> members;

  public InstanceNode(NodeId id, Object source, Object instance, Iterable<Member> members) {
    super(id, source);
    this.instance = instance;
    this.members = members;
  }

  public Object getInstance() {
    return instance;
  }

  public Iterable<Member> getMembers() {
    return members;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof InstanceNode)) {
      return false;
    }
    InstanceNode other = (InstanceNode) obj;
    return super.equals(other)
        && Objects.equal(instance, other.instance)
        && Objects.equal(members, other.members);
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + Objects.hashCode(instance, members);
  }

  @Override
  public String toString() {
    return "InstanceNode{id="
        + getId()
        + " source="
        + getSource()
        + " instance="
        + instance
        + " members="
        + members
        + "}";
  }

  @Override
  public Node copy(NodeId id) {
    return new InstanceNode(id, getSource(), getInstance(), getMembers());
  }
}
