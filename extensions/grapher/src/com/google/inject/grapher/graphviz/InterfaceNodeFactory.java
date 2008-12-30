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
import com.google.inject.grapher.InterfaceNode;
import com.google.inject.grapher.NameFactory;

/**
 * Graphviz-specific implementation of {@link InterfaceNode.Factory}. Uses
 * a {@link GraphvizEdgeAdaptor} to delegate to a {@link GraphvizNode}.
 *
 * @author phopkins@gmail.com (Pete Hopkins)
 */
public class InterfaceNodeFactory implements InterfaceNode.Factory<String, InterfaceNode<String>> {
  private final GraphvizRenderer renderer;
  private final NameFactory nameFactory;

  @Inject
  public InterfaceNodeFactory(GraphvizRenderer renderer, NameFactory nameFactory) {
    this.renderer = renderer;
    this.nameFactory = nameFactory;
  }

  public InterfaceNode<String> newInterfaceNode(String nodeId) {
    GraphvizNode node = new GraphvizNode(nodeId);

    renderer.addNode(node);
    return newAdaptor(node);
  }

  private GraphvizNodeAdaptor newAdaptor(GraphvizNode node) {
    return new GraphvizNodeAdaptor(node);
  }

  /**
   * Adaptor class that converts {@link InterfaceNode} methods to display
   * operations on a {@link GraphvizNode}.
   */
  protected class GraphvizNodeAdaptor implements InterfaceNode<String> {
    protected final GraphvizNode node;

    public GraphvizNodeAdaptor(GraphvizNode node) {
      this.node = node;

      this.node.setStyle(NodeStyle.DASHED);
    }

    public void setKey(Key<?> key) {
      String title = nameFactory.getClassName(key);
      node.setTitle(title);
      node.addSubtitle(0, nameFactory.getAnnotationName(key));
    }

    public void setSource(Object source) {
      // TODO(phopkins): Show the Module on the graph, which comes from the
      // class name when source is a StackTraceElement.
    }
  }
}
