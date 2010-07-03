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

import com.google.inject.internal.util.ImmutableList;
import java.util.List;

/**
 * Data object to encapsulate the attributes of Graphviz edges that we're
 * interested in drawing.
 *
 * @author phopkins@gmail.com (Pete Hopkins)
 */
public class GraphvizEdge {
  private final String headNodeId;
  private String headPortId;
  private CompassPoint headCompassPoint;
  private List<ArrowType> arrowHead = ImmutableList.of(ArrowType.NORMAL);
  
  private final String tailNodeId;
  private String tailPortId;
  private CompassPoint tailCompassPoint;
  private List<ArrowType> arrowTail = ImmutableList.of(ArrowType.NONE);

  private EdgeStyle style = EdgeStyle.SOLID;

  public GraphvizEdge(String tailNodeId, String headNodeId) {
    this.tailNodeId = tailNodeId;
    this.headNodeId = headNodeId;
  }

  public String getHeadNodeId() {
    return headNodeId;
  }

  public String getHeadPortId() {
    return headPortId;
  }

  public void setHeadPortId(String headPortId) {
    this.headPortId = headPortId;
  }

  public CompassPoint getHeadCompassPoint() {
    return headCompassPoint;
  }
  
  public void setHeadCompassPoint(CompassPoint headCompassPoint) {
    this.headCompassPoint = headCompassPoint;
  }

  public List<ArrowType> getArrowHead() {
    return arrowHead;
  }

  public void setArrowHead(List<ArrowType> arrowHead) {
    this.arrowHead = ImmutableList.copyOf(arrowHead);
  }

  public String getTailNodeId() {
    return tailNodeId;
  }

  public String getTailPortId() {
    return tailPortId;
  }

  public void setTailPortId(String tailPortId) {
    this.tailPortId = tailPortId;
  }

  public CompassPoint getTailCompassPoint() {
    return tailCompassPoint;
  }
  
  public void setTailCompassPoint(CompassPoint tailCompassPoint) {
    this.tailCompassPoint = tailCompassPoint;
  }

  public List<ArrowType> getArrowTail() {
    return arrowTail;
  }

  public void setArrowTail(List<ArrowType> arrowTail) {
    this.arrowTail = ImmutableList.copyOf(arrowTail);
  }

  public EdgeStyle getStyle() {
    return style;
  }

  public void setStyle(EdgeStyle style) {
    this.style = style;
  }
}
