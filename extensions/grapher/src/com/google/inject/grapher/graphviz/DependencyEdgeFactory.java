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
import com.google.inject.grapher.DependencyEdge;
import com.google.inject.internal.util.ImmutableList;
import com.google.inject.spi.InjectionPoint;

/**
 * Graphviz-specific implementation of {@link DependencyEdge.Factory}. Uses a
 * {@link GraphvizEdgeAdaptor} to delegate to a {@link GraphvizEdge}.
 *
 * @author phopkins@gmail.com (Pete Hopkins)
 */
public class DependencyEdgeFactory
implements DependencyEdge.Factory<String, DependencyEdge<String>> {
  private final GraphvizRenderer renderer;
  private final PortIdFactory portIdFactory;

  @Inject
  public DependencyEdgeFactory(GraphvizRenderer renderer, PortIdFactory portIdFactory) {
    this.renderer = renderer;
    this.portIdFactory = portIdFactory;
  }

  public DependencyEdge<String> newDependencyEdge(String fromId,
      InjectionPoint fromPoint, String toId) {
    GraphvizEdge edge = new GraphvizEdge(fromId, toId);

    if (fromPoint == null) {
      edge.setTailPortId("header");
    } else {
      edge.setTailPortId(portIdFactory.getPortId(fromPoint.getMember()));
    }

    renderer.addEdge(edge);
    return newAdaptor(edge);
  }

  protected GraphvizEdgeAdaptor newAdaptor(GraphvizEdge edge) {
    return new GraphvizEdgeAdaptor(edge);
  }

  /**
   * Adaptor class that converts {@link DependencyEdge} methods to display
   * operations on a {@link GraphvizEdge}.
   */
  protected class GraphvizEdgeAdaptor implements DependencyEdge<String> {
    protected final GraphvizEdge edge;

    public GraphvizEdgeAdaptor(GraphvizEdge edge) {
      this.edge = edge;

      this.edge.setArrowHead(ImmutableList.of(ArrowType.NORMAL));
      this.edge.setTailCompassPoint(CompassPoint.EAST);
    }
  }
}
