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
import com.google.inject.internal.util.ImmutableMap;
import com.google.inject.internal.util.Maps;
import java.util.List;
import java.util.Map;

/**
 * Data object to encapsulate the attributes of Graphviz nodes that we're
 * interested in drawing.
 *
 * @author phopkins@gmail.com (Pete Hopkins)
 */
public class GraphvizNode {
  private final String nodeId;

  private NodeStyle style = NodeStyle.SOLID;
  private NodeShape shape = NodeShape.BOX;
  
  private String title = "";
  private Map<Integer, String> subtitles = Maps.newTreeMap();
  
  private String headerTextColor = "#000000";
  private String headerBackgroundColor = "#ffffff";

  /** {@link Map} from port ID to field title */
  private Map<String, String> fields = Maps.newLinkedHashMap();

  public GraphvizNode(String nodeId) {
    this.nodeId = nodeId;
  }
  
  public String getNodeId() {
    return nodeId;
  }

  public NodeShape getShape() {
    return shape;
  }
  
  public void setShape(NodeShape shape) {
    this.shape = shape;
  }
  
  public NodeStyle getStyle() {
    return style;
  }

  public void setStyle(NodeStyle style) {
    this.style = style;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public List<String> getSubtitles() {
    return ImmutableList.copyOf(subtitles.values());
  }

  public void addSubtitle(int position, String subtitle) {
    this.subtitles.put(position, subtitle);
  }

  public String getHeaderTextColor() {
    return headerTextColor;
  }

  public void setHeaderTextColor(String headerTextColor) {
    this.headerTextColor = headerTextColor;
  }

  public String getHeaderBackgroundColor() {
    return headerBackgroundColor;
  }

  public void setHeaderBackgroundColor(String headerBackgroundColor) {
    this.headerBackgroundColor = headerBackgroundColor;
  }

  public void addField(String portId, String title) {
    fields.put(portId, title);
  }

  public Map<String, String> getFields() {
    return ImmutableMap.copyOf(fields);
  }
}
