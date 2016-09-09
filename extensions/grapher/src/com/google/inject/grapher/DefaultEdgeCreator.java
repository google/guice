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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.Binding;
import com.google.inject.spi.ConstructorBinding;
import com.google.inject.spi.ConvertedConstantBinding;
import com.google.inject.spi.DefaultBindingTargetVisitor;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.HasDependencies;
import com.google.inject.spi.InstanceBinding;
import com.google.inject.spi.LinkedKeyBinding;
import com.google.inject.spi.ProviderBinding;
import com.google.inject.spi.ProviderInstanceBinding;
import com.google.inject.spi.ProviderKeyBinding;
import java.util.Collection;
import java.util.List;

/**
 * Default edge creator.
 *
 * @author bojand@google.com (Bojan Djordjevic)
 */
final class DefaultEdgeCreator implements EdgeCreator {

  @Override
  public Iterable<Edge> getEdges(Iterable<Binding<?>> bindings) {
    List<Edge> edges = Lists.newArrayList();
    EdgeVisitor visitor = new EdgeVisitor();
    for (Binding<?> binding : bindings) {
      edges.addAll(binding.acceptTargetVisitor(visitor));
    }
    return edges;
  }

  /**
   * {@link BindingTargetVisitor} that adds edges to the graph based on the visited {@link Binding}.
   */
  private static final class EdgeVisitor
      extends DefaultBindingTargetVisitor<Object, Collection<Edge>> {

    /**
     * Returns a dependency edge for each {@link Dependency} in the binding. These will be from the
     * given node ID to the {@link Dependency}'s {@link Key}.
     *
     * @param nodeId ID of the node that should be the tail of the dependency edges
     * @param binding {@link Binding} for the dependencies
     */
    private <T extends Binding<?> & HasDependencies> Collection<Edge> newDependencyEdges(
        NodeId nodeId, T binding) {
      ImmutableList.Builder<Edge> builder = ImmutableList.builder();
      for (Dependency<?> dependency : binding.getDependencies()) {
        NodeId to = NodeId.newTypeId(dependency.getKey());
        builder.add(new DependencyEdge(nodeId, to, dependency.getInjectionPoint()));
      }
      return builder.build();
    }

    /**
     * Visitor for {@link ConstructorBinding}s. These are for classes that Guice will instantiate to
     * satisfy injection requests.
     */
    @Override
    public Collection<Edge> visit(ConstructorBinding<?> binding) {
      return newDependencyEdges(NodeId.newTypeId(binding.getKey()), binding);
    }

    /**
     * Visitor for {@link ConvertedConstantBinding}. The {@link Binding}'s {@link Key} will be of an
     * annotated primitive type, and the value of {@link ConvertedConstantBinding#getSourceKey()}
     * will be of a {@link String} with the same annotation.
     */
    @Override
    public Collection<Edge> visit(ConvertedConstantBinding<?> binding) {
      return ImmutableList.<Edge>of(
          new BindingEdge(
              NodeId.newTypeId(binding.getKey()),
              NodeId.newTypeId(binding.getSourceKey()),
              BindingEdge.Type.CONVERTED_CONSTANT));
    }

    /**
     * Visitor for {@link InstanceBinding}. We then render any dependency edgess that the instance
     * may have, which come either from {@link InjectionPoint}s (method and field) on the instance,
     * or on {@link Dependency}s the instance declares through the {@link HasDependencies}
     * interface.
     */
    @Override
    public Collection<Edge> visit(InstanceBinding<?> binding) {
      return new ImmutableList.Builder<Edge>()
          .add(
              new BindingEdge(
                  NodeId.newTypeId(binding.getKey()),
                  NodeId.newInstanceId(binding.getKey()),
                  BindingEdge.Type.NORMAL))
          .addAll(newDependencyEdges(NodeId.newInstanceId(binding.getKey()), binding))
          .build();
    }

    /**
     * Visitor for {@link LinkedKeyBinding}. This is the standard {@link Binding} you get from
     * binding an interface class to an implementation class. We draw a {@link BindingEdge} from the
     * interface node to the node of the implementing class.
     */
    @Override
    public Collection<Edge> visit(LinkedKeyBinding<?> binding) {
      return ImmutableList.<Edge>of(
          new BindingEdge(
              NodeId.newTypeId(binding.getKey()),
              NodeId.newTypeId(binding.getLinkedKey()),
              BindingEdge.Type.NORMAL));
    }

    /**
     * Visitor for {@link ProviderBinding}. These {@link Binding}s arise from an {@link
     * InjectionPoint} for the {@link Provider} interface.
     */
    @Override
    public Collection<Edge> visit(ProviderBinding<?> binding) {
      return ImmutableList.<Edge>of(
          new BindingEdge(
              NodeId.newTypeId(binding.getKey()),
              NodeId.newTypeId(binding.getProvidedKey()),
              BindingEdge.Type.PROVIDER));
    }

    /**
     * Same as {@link #visit(InstanceBinding)}, but the binding edge is {@link
     * BindingEdge.Type#PROVIDER}.
     */
    @Override
    public Collection<Edge> visit(ProviderInstanceBinding<?> binding) {
      return new ImmutableList.Builder<Edge>()
          .add(
              new BindingEdge(
                  NodeId.newTypeId(binding.getKey()),
                  NodeId.newInstanceId(binding.getKey()),
                  BindingEdge.Type.PROVIDER))
          .addAll(newDependencyEdges(NodeId.newInstanceId(binding.getKey()), binding))
          .build();
    }

    /**
     * Same as {@link #visit(LinkedKeyBinding)}, but the binding edge is {@link
     * BindingEdge.Type#PROVIDER}.
     */
    @Override
    public Collection<Edge> visit(ProviderKeyBinding<?> binding) {
      return ImmutableList.<Edge>of(
          new BindingEdge(
              NodeId.newTypeId(binding.getKey()),
              NodeId.newTypeId(binding.getProviderKey()),
              BindingEdge.Type.PROVIDER));
    }

    @Override
    public Collection<Edge> visitOther(Binding<?> binding) {
      return ImmutableList.of();
    }
  }
}
