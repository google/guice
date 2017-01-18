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
import com.google.inject.spi.InjectionPoint;

/**
 * Edge from a class or {@link InjectionPoint} to the interface node that will satisfy the
 * dependency.
 *
 * @author phopkins@gmail.com (Pete Hopkins)
 * @since 4.0 (since 2.0 as an interface)
 */
public class DependencyEdge extends Edge {
  /**
   * Injection point to which this dependency belongs, or null if the dependency isn't attached to a
   * particular injection point.
   */
  private final InjectionPoint injectionPoint;

  public DependencyEdge(NodeId fromId, NodeId toId, InjectionPoint injectionPoint) {
    super(fromId, toId);
    this.injectionPoint = injectionPoint;
  }

  public InjectionPoint getInjectionPoint() {
    return injectionPoint;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof DependencyEdge)) {
      return false;
    }
    DependencyEdge other = (DependencyEdge) obj;
    return super.equals(other) && Objects.equal(injectionPoint, other.injectionPoint);
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + Objects.hashCode(injectionPoint);
  }

  @Override
  public String toString() {
    return "DependencyEdge{fromId="
        + getFromId()
        + " toId="
        + getToId()
        + " injectionPoint="
        + injectionPoint
        + "}";
  }

  @Override
  public Edge copy(NodeId fromId, NodeId toId) {
    return new DependencyEdge(fromId, toId, injectionPoint);
  }
}
