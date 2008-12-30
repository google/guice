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

import com.google.common.base.Nullable;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Binding;
import com.google.inject.Inject;
import com.google.inject.spi.BindingTargetVisitor;
import com.google.inject.spi.ConstructorBinding;
import com.google.inject.spi.ConvertedConstantBinding;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.ExposedBinding;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.InstanceBinding;
import com.google.inject.spi.LinkedKeyBinding;
import com.google.inject.spi.ProviderBinding;
import com.google.inject.spi.ProviderInstanceBinding;
import com.google.inject.spi.ProviderKeyBinding;
import com.google.inject.spi.UntargettedBinding;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * {@link BindingTargetVisitor} that adds nodes and edges to the graph based on
 * the visited {@link Binding}.
 * <p>
 * This class is parameterized over the four graph element types
 * ({@link InterfaceNode}, {@link ImplementationNode}, {@link BindingEdge}, and
 * {@link DependencyEdge}) so that you can extend those interfaces and also
 * extend this class, and the helper methods will all return your new types.
 * 
 * @author phopkins@gmail.com (Pete Hopkins)
 *
 * @param <K> The type for node IDs.
 * @param <N> Type for {@link InterfaceNode}s.
 * @param <M> Type for {@link ImplementationNode}.s
 * @param <B> Type for {@link BindingEdge}s.
 * @param <D> Type for {@link DependencyEdge}s.
 */
public class GraphingVisitor<K, N extends InterfaceNode<K>, M extends ImplementationNode<K>,
    B extends BindingEdge<K>, D extends DependencyEdge<K>>
implements BindingTargetVisitor<Object, Void> {

  private final NodeIdFactory<K> idFactory;
 
  private final InterfaceNode.Factory<K, N> interfaceNodeFactory;
  private final ImplementationNode.Factory<K, M> implementationNodeFactory;
  private final BindingEdge.Factory<K, B> bindingEdgeFactory;
  private final DependencyEdge.Factory<K, D> dependencyEdgeFactory;
  private final NodeAliasFactory<K> nodeAliasFactory;

  @Inject
  public GraphingVisitor(NodeIdFactory<K> idFactory,
      InterfaceNode.Factory<K, N> interfaceNodeFactory,
      ImplementationNode.Factory<K, M> implementationNodeFactory,
      BindingEdge.Factory<K, B> bindingEdgeFactory,
      DependencyEdge.Factory<K, D> dependencyEdgeFactory,
      NodeAliasFactory<K> nodeAliasFactory) {
    this.idFactory = idFactory;
    this.interfaceNodeFactory = interfaceNodeFactory;
    this.implementationNodeFactory = implementationNodeFactory;
    this.bindingEdgeFactory = bindingEdgeFactory;
    this.dependencyEdgeFactory = dependencyEdgeFactory;
    this.nodeAliasFactory = nodeAliasFactory;
  }

  /**
   * Helper method to return the standard node ID for the {@link Binding}'s
   * {@link Key}.
   * 
   * @see NodeIdFactory#getClassNodeId(Key)
   */
  protected final K getClassNodeId(Binding<?> binding) {
    return idFactory.getClassNodeId(binding.getKey());
  }

  /**
   * Helper method to return the instance node ID for the {@link Binding}'s
   * {@link Key}.
   * 
   * @see NodeIdFactory#getInstanceNodeId(Key)
   */
  protected final K getInstanceNodeId(Binding<?> binding) {
    return idFactory.getInstanceNodeId(binding.getKey());
  }

  /**
   * Creates and returns a new {@link InterfaceNode} object for the given
   * {@link Binding}.
   */
  protected N newInterfaceNode(Binding<?> binding) {
    N node = interfaceNodeFactory.newInterfaceNode(getClassNodeId(binding));
    node.setKey(binding.getKey());
    node.setSource(binding.getSource());

    return node;
  }

  /**
   * Creates and returns a new {@link ImplementationNode} for the given
   * {@link Binding}, where the {@link Binding} is for a class that Guice
   * will instantiate, rather than a specific instance.
   */
  protected M newClassImplementationNode(Binding<?> binding,
      Collection<InjectionPoint> injectionPoints) {
    M node = implementationNodeFactory.newImplementationNode(getClassNodeId(binding));
    node.setClassKey(binding.getKey());
    // we don't set the source here because it's not interesting for classes

    for (InjectionPoint injectionPoint : injectionPoints) {
      node.addMember(injectionPoint.getMember());
    }

    return node;
  }

  /**
   * Creates and returns a new {@link ImplementationNode} for the given
   * {@link Binding}, where the {@link Binding} is for an instance, rather than
   * a class.
   */
  protected M newInstanceImplementationNode(Binding<?> binding, Object instance,
      Collection<InjectionPoint> injectionPoints) {
    M node = implementationNodeFactory.newImplementationNode(getInstanceNodeId(binding));
    node.setSource(binding.getSource());
    node.setInstance(instance);

    for (InjectionPoint injectionPoint : injectionPoints) {
      node.addMember(injectionPoint.getMember());
    }

    return node;
  }

  /**
   * Creates a new {@link BindingEdge} from the given node to the specified
   * node.
   *
   * @param nodeId ID of the {@link InterfaceNode} that binds to the other.
   * @param toId The node ID of a class or instance that is bound.
   * @param type The {@link BindingEdge.Type} of this binding.
   * @return The newly-created and added {@link BindingEdge}.
   */
  protected B newBindingEdge(K nodeId, K toId, BindingEdge.Type type) {
    B edge = bindingEdgeFactory.newBindingEdge(nodeId, toId);
    edge.setType(type);

    return edge;
  }

  /**
   * Adds {@link DependencyEdge}s to the graph from the given
   * {@link ImplementationNode} to the {@link Key}s specified in the
   * {@link InjectionPoint}s.
   * <p>
   * Also adds edges for any {@link Dependency}s passed in that are not covered
   * in the set of {@link InjectionPoint}s.
   *
   * @see #newDependencyEdge(Object, InjectionPoint, Dependency)
   * 
   * @param nodeId ID of the node that should be the tail of the
   *     {@link DependencyEdge}s.
   * @param injectionPoints {@link Collection} of {@link InjectionPoint}s on
   *     the class or instance represented by the {@link ImplementationNode}.
   * @param dependencies {@link Collection} of {@link Dependency}s from the
   *     {@link Binding}. Some {@link Binding}s may have {@link Dependency}s
   *     even if they do not have {@link InjectionPoint}s.
   * @return A {@link Collection} of the {@link DependencyEdge}s that were
   *     added to the graph.
   */
  protected Collection<D> newDependencyEdges(K nodeId, Collection<InjectionPoint> injectionPoints,
      Collection<Dependency<?>> dependencies) {
    List<D> edges = Lists.newArrayList();

    // Set to keep track of which of the given Dependencies is not duplicated
    // by the InjectionPoints.
    Set<Dependency<?>> remainingDependencies = Sets.newHashSet(dependencies);
    
    for (InjectionPoint injectionPoint : injectionPoints) {
      for (Dependency<?> dependency : injectionPoint.getDependencies()) {
        D edge = newDependencyEdge(nodeId, injectionPoint, dependency);
        edges.add(edge);
        remainingDependencies.remove(dependency);
      }
    }

    for (Dependency<?> dependency : remainingDependencies) {
      D edge = newDependencyEdge(nodeId, null, dependency);    
      edges.add(edge);
    }

    return edges;
  }

  /**
   * Creates a new {@link DependencyEdge} from the given node to a
   * {@link Dependency}.
   * <p>
   * This method takes more comprehensive parameters than strictly necessary
   * in case they would be useful to overriding implementations.
   *
   * @param nodeId ID of the {@link ImplementationNode} where the edges will start.
   * @param injectionPoint The {@link InjectionPoint} that gave rise to this
   *     {@link Dependency}, if one exists. Used to figure out which
   *     {@link Member} the edge should point from.
   * @param dependency The {@link Dependency} to represent with this edge.
   * @return The newly-created and added {@link DependencyEdge}.
   */
  protected D newDependencyEdge(K nodeId,
      @Nullable InjectionPoint injectionPoint, Dependency<?> dependency) {
    K toId = idFactory.getClassNodeId(dependency.getKey());
    return dependencyEdgeFactory.newDependencyEdge(nodeId, injectionPoint, toId);
  }


  /**
   * Visitor for {@link ConstructorBinding}s. These are for classes that Guice
   * will instantiate to satisfy injection requests. We create a new
   * {@link ImplementationNode} for the class, then add edges to everything
   * that it depends on to be instantiated.
   *
   * @see #newClassImplementationNode(Binding)
   * @see #newDependencyEdges(ImplementationNode, Collection, Collection)
   */
  public Void visitConstructor(ConstructorBinding<?> binding) {
    newClassImplementationNode(binding, binding.getInjectionPoints());
    newDependencyEdges(getClassNodeId(binding), binding.getInjectionPoints(),
        binding.getDependencies());

    return null;
  }

  /**
   * Visitor for {@link ConvertedConstantBinding}. The {@link Binding}'s
   * {@link Key} will be of an annotated primitive type, and the value of
   * {@link ConvertedConstantBinding#getSourceKey()} will be of a
   * {@link String} with the same annotation.
   * <p>
   * We render this as an {@link InterfaceNode} that has a
   * {@link BindingEdge} to the source {@link Key}. That will then be rendered
   * by {@link #visitInstance(InstanceBinding)} as an {@link InterfaceNode}
   * with a {@link BindingEdge} to the {@link String} instance.
   * 
   * @see #newInterfaceNode(Binding)
   * @see #newBindingEdge(InterfaceNode, Object, BindingEdge.Type)
   */
  public Void visitConvertedConstant(ConvertedConstantBinding<?> binding) {
    newInterfaceNode(binding);
    newBindingEdge(getClassNodeId(binding), idFactory.getClassNodeId(binding.getSourceKey()),
        BindingEdge.Type.CONVERTED_CONSTANT);

    return null;
  }

  /**
   * Currently not displayed on the graph.
   */
  public Void visitExposed(ExposedBinding<?> binding) {
    // TODO(phopkins): Decide if this is needed for graphing.
    return null;
  }

  /**
   * Visitor for {@link InstanceBinding}. We render two nodes in this case: a
   * {@link InterfaceNode} for the binding's {@link Key}, and then an
   * {@link ImplementationNode} for the instance {@link Object} itself. We run
   * a {@link BindingNode} between them.
   * <p>
   * We then render any {@link DependencyEdge}s that the instance may have,
   * which come either from {@link InjectionPoint}s (method and field) on the
   * instance, or on {@link Dependency}s the instance declares through the
   * {@link HasDependencies} interface.
   * 
   * @see #newInterfaceNode(Binding)
   * @see #newBindingEdge(InterfaceNode, Object, BindingEdge.Type)
   * @see #newInstanceImplementationNode(Binding, Object)
   * @see #newDependencyEdges(ImplementationNode, Collection, Collection)
   */
  public Void visitInstance(InstanceBinding<?> binding) {
    newInterfaceNode(binding);
    newBindingEdge(getClassNodeId(binding), getInstanceNodeId(binding),
        BindingEdge.Type.NORMAL);

    newInstanceImplementationNode(binding, binding.getInstance(), binding.getInjectionPoints());
    newDependencyEdges(getInstanceNodeId(binding), binding.getInjectionPoints(),
        binding.getDependencies());

    return null;
  }

  /**
   * Visitor for {@link LinkedKeyBinding}. This is the standard {@link Binding}
   * you get from binding an interface class to an implementation class. We
   * create an {@link InterfaceNode}, then draw a {@link BindingEdge} to the
   * node of the implementing class.
   * 
   * @see #newInterfaceNode(Binding)
   * @see #newBindingEdge(InterfaceNode, Object, BindingEdge.Type)
   */
  public Void visitLinkedKey(LinkedKeyBinding<?> binding) {
    newInterfaceNode(binding);
    newBindingEdge(getClassNodeId(binding), idFactory.getClassNodeId(binding.getLinkedKey()), 
        BindingEdge.Type.NORMAL);

    return null;
  }

  /**
   * Visitor for {@link ProviderBinding}. These {@link Binding}s arise from an
   * {@link InjectionPoint} for the {@link Provider} interface. Since this
   * isn't tremendously interesting information, we don't render this binding
   * on the graph, and instead let the {@link DependencyEdge} go straight from
   * the {@link InjectionPoint} to the node specified by
   * {@link ProviderBinding#getProvidedKey()}.
   * 
   * @see NodeAliasFactory#newAlias(Object, Object)
   */
  public Void visitProviderBinding(ProviderBinding<?> binding) {
    nodeAliasFactory.newAlias(getClassNodeId(binding),
        idFactory.getClassNodeId(binding.getProvidedKey()));

    return null;
  }

  /**
   * Same as {@link #visitInstance(InstanceBinding)}, but the
   * {@link BindingEdge} is {@link BindingEdge.Type#PROVIDER}.
   * 
   * @see #newInterfaceNode(Binding)
   * @see #newBindingEdge(InterfaceNode, Object, BindingEdge.Type)
   * @see #newInstanceImplementationNode(Binding, Object)
   * @see #newDependencyEdges(ImplementationNode, Collection, Collection)
   */
  public Void visitProviderInstance(ProviderInstanceBinding<?> binding) {
    newInterfaceNode(binding);
    newBindingEdge(getClassNodeId(binding), getInstanceNodeId(binding), BindingEdge.Type.PROVIDER);

    newInstanceImplementationNode(binding, binding.getProviderInstance(),
        binding.getInjectionPoints());
    newDependencyEdges(getInstanceNodeId(binding), binding.getInjectionPoints(),
        binding.getDependencies());

    return null;
  }

  /**
   * Same as {@link #visitLinkedKey(LinkedKeyBinding)}, but the
   * {@link BindingEdge} is {@link BindingEdge.Type#PROVIDER}.
   * 
   * @see #newInterfaceNode(Binding)
   * @see #newBindingEdge(InterfaceNode, Object, BindingEdge.Type)
   */
  public Void visitProviderKey(ProviderKeyBinding<?> binding) {
    newInterfaceNode(binding);
    newBindingEdge(getClassNodeId(binding), idFactory.getClassNodeId(binding.getProviderKey()),
        BindingEdge.Type.PROVIDER);

    return null;
  }

  /**
   * Currently not displayed on the graph.
   */
  public Void visitUntargetted(UntargettedBinding<?> binding) {
    // TODO(phopkins): Decide if this is needed for graphing.
    return null;
  }
}
