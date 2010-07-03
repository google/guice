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
import com.google.inject.grapher.BindingEdge;
import com.google.inject.internal.util.ImmutableList;

/**
 * Graphviz-specific implementation of {@link BindingEdge.Factory}. Uses a
 * {@link GraphvizEdgeAdaptor} to delegate to a {@link GraphvizEdge}.
 *
 * @author phopkins@gmail.com (Pete Hopkins)
 */
public class BindingEdgeFactory implements BindingEdge.Factory<String, BindingEdge<String>> {
  private final GraphvizRenderer renderer;

  @Inject
  public BindingEdgeFactory(GraphvizRenderer renderer) {
    this.renderer = renderer;
  }

  public BindingEdge<String> newBindingEdge(String fromId, String toId) {
    GraphvizEdge edge = new GraphvizEdge(fromId, toId);

    renderer.addEdge(edge);
    return newAdaptor(edge);
  }

  protected GraphvizEdgeAdaptor newAdaptor(GraphvizEdge edge) {
    return new GraphvizEdgeAdaptor(edge);
  }

  /**
   * Adaptor class that converts {@link BindingEdge} methods to display
   * operations on a {@link GraphvizEdge}.
   */
  protected class GraphvizEdgeAdaptor implements BindingEdge<String> {
    protected final GraphvizEdge edge;

    public GraphvizEdgeAdaptor(GraphvizEdge edge) {
      this.edge = edge;

      this.edge.setStyle(EdgeStyle.DASHED);
    }

    public void setType(BindingEdge.Type type) {
      switch (type) {
      case NORMAL:
        edge.setArrowHead(ImmutableList.of(ArrowType.NORMAL_OPEN));
        break;

      case PROVIDER:
        edge.setArrowHead(ImmutableList.of(ArrowType.NORMAL_OPEN, ArrowType.NORMAL_OPEN));
        break;

      case CONVERTED_CONSTANT:
        edge.setArrowHead(ImmutableList.of(ArrowType.NORMAL_OPEN, ArrowType.DOT_OPEN));
        break;
      }
    }
  }
}