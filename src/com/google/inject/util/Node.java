/**
 * Copyright (C) 2009 Google Inc.
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

package com.google.inject.util;

import com.google.inject.Key;
import com.google.inject.internal.Errors;
import com.google.inject.internal.util.ImmutableSet;
import com.google.inject.internal.util.Sets;
import java.lang.annotation.Annotation;
import java.util.Set;

/**
 * A node in the scoped dependency graph. Each node has two scopes. The <i>applied scope</i> is the
 * scope directly assigned to the binding by the user, such as in an {@code in()} clause. The
 * <i>effective scope</i> is the narrowest scope in which this object is used. It is derived from
 * the narrowest scope of the node's transitive dependencies. Each scope is modelled as a rank;
 * higher numbers represent narrower scopes.
 */
class Node {
  private final Key<?> key;

  private int appliedScope = Integer.MAX_VALUE;
  private Node effectiveScopeDependency;

  private int effectiveScope = Integer.MIN_VALUE;
  private Class<? extends Annotation> appliedScopeAnnotation;

  /** Places that this node is injected. */
  private Set<Node> users = ImmutableSet.of();

  Node(Key<?> key) {
    this.key = key;
  }

  /**
   * Initialize the scope ranks for this node. Called at most once per node.
   */
  void setScopeRank(int rank, Class<? extends Annotation> annotation) {
    this.appliedScope = rank;
    this.effectiveScope = rank;
    this.appliedScopeAnnotation = annotation;
  }

  /**
   * Sets this node's effective scope unless it's already better.
   */
  private void setEffectiveScope(int effectiveScope, Node effectiveScopeDependency) {
    if (this.effectiveScope >= effectiveScope) {
      return;
    }

    this.effectiveScope = effectiveScope;
    this.effectiveScopeDependency = effectiveScopeDependency;
    pushScopeToUsers();
  }

  /**
   * Pushes the narrowness of this node's effective scope to everyone that depends on this node.
   */
  void pushScopeToUsers() {
    for (Node user : users) {
      user.setEffectiveScope(effectiveScope, this);
    }
  }

  /**
   * Returns true if this node has no dependency whose scope is narrower than itself.
   */
  boolean isScopedCorrectly() {
    return appliedScope >= effectiveScope;
  }

  boolean isEffectiveScopeAppliedScope() {
    return appliedScope == effectiveScope;
  }

  /**
   * Returns the most narrowly scoped dependency. If multiple such dependencies exist, the selection
   * of which is returned is arbitrary.
   */
  Node effectiveScopeDependency() {
    return effectiveScopeDependency;
  }

  /**
   * Mark this as a dependency of {@code node}.
   */
  public void addUser(Node node) {
    if (users.isEmpty()) {
      users = Sets.newHashSet();
    }
    users.add(node);
  }

  @Override public String toString() {
    return appliedScopeAnnotation != null
        ? Errors.convert(key) + " in @" + appliedScopeAnnotation.getSimpleName()
        : Errors.convert(key).toString();
  }
}
