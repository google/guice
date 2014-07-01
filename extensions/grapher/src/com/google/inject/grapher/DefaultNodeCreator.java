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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.Binding;
import com.google.inject.spi.ConstructorBinding;
import com.google.inject.spi.DefaultBindingTargetVisitor;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.HasDependencies;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.InstanceBinding;
import com.google.inject.spi.ProviderInstanceBinding;

import java.lang.reflect.Member;
import java.util.Collection;
import java.util.List;

/**
 * Default node creator.
 *
 * @author bojand@google.com (Bojan Djordjevic)
 */
final class DefaultNodeCreator implements NodeCreator {
  @Override public Iterable<Node> getNodes(Iterable<Binding<?>> bindings) {
    List<Node> nodes = Lists.newArrayList();
    NodeVisitor visitor = new NodeVisitor();
    for (Binding<?> binding : bindings) {
      nodes.addAll(binding.acceptTargetVisitor(visitor));
    }
    return nodes;
  }

  /**
   * {@link BindingTargetVisitor} that adds nodes to the graph based on the visited {@link Binding}.
   */
  private static final class NodeVisitor
      extends DefaultBindingTargetVisitor<Object, Collection<Node>> {

    /** Returns a new interface node for the given {@link Binding}. */
    private InterfaceNode newInterfaceNode(Binding<?> binding) {
      return new InterfaceNode(NodeId.newTypeId(binding.getKey()), binding.getSource());
    }

    /**
     * Returns a new implementation node for the given binding.
     *
     * @param binding binding for the node to create
     * @param members members to add to the node
     * @return implementation node for the given binding
     */
    private ImplementationNode newImplementationNode(Binding<?> binding,
        Collection<Member> members) {
      return new ImplementationNode(NodeId.newTypeId(binding.getKey()), binding.getSource(),
          members);
    }

    /**
     * Returns a new instance node for the given {@link Binding}.
     *
     * @param binding binding for the node to create
     * @param instance value of the instance
     * @return instance node for the given binding
     */
    private <T extends Binding<?> & HasDependencies> InstanceNode newInstanceNode(T binding,
        Object instance) {
      Collection<Member> members = Lists.newArrayList();
      for (Dependency<?> dependency : binding.getDependencies()) {
        InjectionPoint injectionPoint = dependency.getInjectionPoint();

        if (injectionPoint != null) {
          members.add(injectionPoint.getMember());
        }
      }
      return new InstanceNode(NodeId.newInstanceId(binding.getKey()), binding.getSource(), instance,
          members);
    }

    /**
     * Visitor for {@link ConstructorBinding}s. These are for classes that Guice will instantiate to
     * satisfy injection requests.
     */
    @Override public Collection<Node> visit(ConstructorBinding<?> binding) {
      Collection<Member> members = Lists.newArrayList();
      members.add(binding.getConstructor().getMember());
      for (InjectionPoint injectionPoint : binding.getInjectableMembers()) {
        members.add(injectionPoint.getMember());
      }

      return ImmutableList.<Node>of(newImplementationNode(binding, members));
    }

    /**
     * Visitor for {@link InstanceBinding}. We render two nodes in this case: an interface node for
     * the binding's {@link Key}, and then an implementation node for the instance {@link Object}
     * itself.
     */
    @Override public Collection<Node> visit(InstanceBinding<?> binding) {
      return ImmutableList.<Node>of(newInterfaceNode(binding), newInstanceNode(binding,
          binding.getInstance()));
    }

    /**
     * Same as {@link #visit(InstanceBinding)}, but the binding edge is
     * {@link BindingEdgeType#PROVIDER}.
     */
    @Override public Collection<Node> visit(ProviderInstanceBinding<?> binding) {
      return ImmutableList.<Node>of(newInterfaceNode(binding), newInstanceNode(binding,
          binding.getUserSuppliedProvider()));
    }

    @Override public Collection<Node> visitOther(Binding<?> binding) {
      return ImmutableList.<Node>of(newInterfaceNode(binding));
    }
  }
}
