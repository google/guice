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

package com.google.inject.grapher.graphviz;

import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.grapher.ImplementationNode;
import com.google.inject.grapher.NameFactory;
import com.google.inject.grapher.graphviz.BindingEdgeFactory.GraphvizEdgeAdaptor;

import java.lang.reflect.Member;

/**
 * Graphviz-specific implementation of {@link ImplementationNode.Factory}. Uses
 * a {@link GraphvizEdgeAdaptor} to delegate to a {@link GraphvizNode}.
 *
 * @author phopkins@gmail.com (Pete Hopkins)
 */
public class ImplementationNodeFactory
implements ImplementationNode.Factory<String, ImplementationNode<String>> {
  private final GraphvizRenderer renderer;
  private final NameFactory nameFactory;
  private final PortIdFactory portIdFactory;

  @Inject
  public ImplementationNodeFactory(GraphvizRenderer renderer, NameFactory nameFactory,
      PortIdFactory portIdFactory) {
    this.renderer = renderer;
    this.nameFactory = nameFactory;
    this.portIdFactory = portIdFactory;
  }

  public ImplementationNode<String> newImplementationNode(String nodeId) {
    GraphvizNode node = new GraphvizNode(nodeId);

    renderer.addNode(node);
    return newAdaptor(node);
  }

  protected GraphvizNodeAdaptor newAdaptor(GraphvizNode node) {
    return new GraphvizNodeAdaptor(node);
  }

  /**
   * Adaptor class that converts {@link ImplementationNode} methods to display
   * operations on a {@link GraphvizNode}.
   */
  protected class GraphvizNodeAdaptor implements ImplementationNode<String> {
    protected final GraphvizNode node;

    public GraphvizNodeAdaptor(GraphvizNode node) {
      this.node = node;

      this.node.setStyle(NodeStyle.INVISIBLE);
    }

    public void setClassKey(Key<?> key) {
      node.setHeaderBackgroundColor("#000000");
      node.setHeaderTextColor("#ffffff");
      node.setTitle(nameFactory.getClassName(key));
    }

    public void setInstance(Object instance) {
      node.setHeaderBackgroundColor("#aaaaaa");
      node.setHeaderTextColor("#ffffff");
      node.setTitle(nameFactory.getInstanceName(instance));
    }

    public void setSource(Object source) {
      node.addSubtitle(0, nameFactory.getSourceName(source));
    }

    public void addMember(Member member) {
      node.addField(portIdFactory.getPortId(member), nameFactory.getMemberName(member));
    }
  }
}
