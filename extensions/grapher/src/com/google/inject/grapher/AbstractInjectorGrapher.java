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

import com.google.common.collect.SetMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Abstract injector grapher that builds the dependency graph but doesn't render it.
 *
 * @author bojand@google.com (Bojan Djordjevic)
 * @since 4.0
 */
public abstract class AbstractInjectorGrapher implements InjectorGrapher {
  private final RootKeySetCreator rootKeySetCreator;
  private final AliasCreator aliasCreator;
  private final NodeCreator nodeCreator;
  private final EdgeCreator edgeCreator;

  /**
   * Parameters used to override default settings of the grapher.
   *
   * @since 4.0
   */
  public static final class GrapherParameters {
    private RootKeySetCreator rootKeySetCreator = new DefaultRootKeySetCreator();
    private AliasCreator aliasCreator = new ProviderAliasCreator();
    private NodeCreator nodeCreator = new DefaultNodeCreator();
    private EdgeCreator edgeCreator = new DefaultEdgeCreator();

    public RootKeySetCreator getRootKeySetCreator() {
      return rootKeySetCreator;
    }

    public GrapherParameters setRootKeySetCreator(RootKeySetCreator rootKeySetCreator) {
      this.rootKeySetCreator = rootKeySetCreator;
      return this;
    }

    public AliasCreator getAliasCreator() {
      return aliasCreator;
    }

    public GrapherParameters setAliasCreator(AliasCreator aliasCreator) {
      this.aliasCreator = aliasCreator;
      return this;
    }

    public NodeCreator getNodeCreator() {
      return nodeCreator;
    }

    public GrapherParameters setNodeCreator(NodeCreator nodeCreator) {
      this.nodeCreator = nodeCreator;
      return this;
    }

    public EdgeCreator getEdgeCreator() {
      return edgeCreator;
    }

    public GrapherParameters setEdgeCreator(EdgeCreator edgeCreator) {
      this.edgeCreator = edgeCreator;
      return this;
    }
  }

  public AbstractInjectorGrapher() {
    this(new GrapherParameters());
  }

  public AbstractInjectorGrapher(GrapherParameters options) {
    this.rootKeySetCreator = options.getRootKeySetCreator();
    this.aliasCreator = options.getAliasCreator();
    this.nodeCreator = options.getNodeCreator();
    this.edgeCreator = options.getEdgeCreator();
  }

  @Override
  public final void graph(Injector injector) throws IOException {
    graph(injector, rootKeySetCreator.getRootKeys(injector));
  }

  @Override
  public final void graph(Injector injector, Set<Key<?>> root) throws IOException {
    reset();

    Iterable<Binding<?>> bindings = getBindings(injector, root);
    Map<NodeId, NodeId> aliases = resolveAliases(aliasCreator.createAliases(bindings));
    createNodes(nodeCreator.getNodes(bindings), aliases);
    createEdges(edgeCreator.getEdges(bindings), aliases);
    postProcess();
  }

  /** Resets the state of the grapher before rendering a new graph. */
  protected abstract void reset() throws IOException;

  /** Adds a new interface node to the graph. */
  protected abstract void newInterfaceNode(InterfaceNode node) throws IOException;

  /** Adds a new implementation node to the graph. */
  protected abstract void newImplementationNode(ImplementationNode node) throws IOException;

  /** Adds a new instance node to the graph. */
  protected abstract void newInstanceNode(InstanceNode node) throws IOException;

  /** Adds a new dependency edge to the graph. */
  protected abstract void newDependencyEdge(DependencyEdge edge) throws IOException;

  /** Adds a new binding edge to the graph. */
  protected abstract void newBindingEdge(BindingEdge edge) throws IOException;

  /** Performs any post processing required after all nodes and edges have been added. */
  protected abstract void postProcess() throws IOException;

  private void createNodes(Iterable<Node> nodes, Map<NodeId, NodeId> aliases) throws IOException {
    for (Node node : nodes) {
      NodeId originalId = node.getId();
      NodeId resolvedId = resolveAlias(aliases, originalId);
      node = node.copy(resolvedId);

      // Only render nodes that aren't aliased to some other node.
      if (resolvedId.equals(originalId)) {
        if (node instanceof InterfaceNode) {
          newInterfaceNode((InterfaceNode) node);
        } else if (node instanceof ImplementationNode) {
          newImplementationNode((ImplementationNode) node);
        } else {
          newInstanceNode((InstanceNode) node);
        }
      }
    }
  }

  private void createEdges(Iterable<Edge> edges, Map<NodeId, NodeId> aliases) throws IOException {
    for (Edge edge : edges) {
      edge =
          edge.copy(resolveAlias(aliases, edge.getFromId()), resolveAlias(aliases, edge.getToId()));
      if (!edge.getFromId().equals(edge.getToId())) {
        if (edge instanceof BindingEdge) {
          newBindingEdge((BindingEdge) edge);
        } else {
          newDependencyEdge((DependencyEdge) edge);
        }
      }
    }
  }

  private NodeId resolveAlias(Map<NodeId, NodeId> aliases, NodeId nodeId) {
    return aliases.getOrDefault(nodeId, nodeId);
  }

  /**
   * Transitively resolves aliases. Given aliases (X to Y) and (Y to Z), it will return mappings (X
   * to Z) and (Y to Z).
   */
  private Map<NodeId, NodeId> resolveAliases(Iterable<Alias> aliases) {
    Map<NodeId, NodeId> resolved = Maps.newHashMap();
    SetMultimap<NodeId, NodeId> inverse = HashMultimap.create();

    for (Alias alias : aliases) {
      NodeId from = alias.getFromId();
      NodeId to = alias.getToId();
      if (resolved.containsKey(to)) {
        to = resolved.get(to);
      }
      resolved.put(from, to);
      inverse.put(to, from);

      Set<NodeId> prev = inverse.get(from);
      if (prev != null) {
        for (NodeId id : prev) {
          resolved.remove(id);
          inverse.remove(from, id);
          resolved.put(id, to);
          inverse.put(to, id);
        }
      }
    }

    return resolved;
  }

  /** Returns the bindings for the root keys and their transitive dependencies. */
  private Iterable<Binding<?>> getBindings(Injector injector, Set<Key<?>> root) {
    Set<Key<?>> keys = Sets.newHashSet(root);
    Set<Key<?>> visitedKeys = Sets.newHashSet();
    List<Binding<?>> bindings = Lists.newArrayList();
    TransitiveDependencyVisitor keyVisitor = new TransitiveDependencyVisitor();

    while (!keys.isEmpty()) {
      Iterator<Key<?>> iterator = keys.iterator();
      Key<?> key = iterator.next();
      iterator.remove();

      if (!visitedKeys.contains(key)) {
        Binding<?> binding = injector.getBinding(key);
        bindings.add(binding);
        visitedKeys.add(key);
        keys.addAll(binding.acceptTargetVisitor(keyVisitor));
      }
    }
    return bindings;
  }
}
