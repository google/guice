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

import com.google.inject.grapher.ImplementationNode;
import com.google.inject.grapher.NodeAliasFactory;
import com.google.inject.grapher.Renderer;
import com.google.inject.internal.util.Join;
import com.google.inject.internal.util.Lists;
import com.google.inject.internal.util.Maps;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * {@link Renderer} implementation that writes out a Graphviz DOT file of the
 * graph. Bound in {@link GraphvizModule}.
 * <p>
 * Specify the {@link PrintWriter} to output to with
 * {@link #setOut(PrintWriter)}.
 *
 * @author phopkins@gmail.com (Pete Hopkins)
 */
public class GraphvizRenderer implements Renderer, NodeAliasFactory<String> {
  private final List<GraphvizNode> nodes = Lists.newArrayList();
  private final List<GraphvizEdge> edges = Lists.newArrayList();
  private final Map<String, String> aliases = Maps.newHashMap();
  
  private PrintWriter out;
  private String rankdir = "TB";

  public GraphvizRenderer setOut(PrintWriter out) {
    this.out = out;
    return this;
  }

  public GraphvizRenderer setRankdir(String rankdir) {
    this.rankdir = rankdir;
    return this;
  }

  public void addNode(GraphvizNode node) {
    nodes.add(node);
  }
  
  public void addEdge(GraphvizEdge edge) {
    edges.add(edge);
  }

  public void newAlias(String fromId, String toId) {
    aliases.put(fromId, toId);
  }

  protected String resolveAlias(String id) {
    while (aliases.containsKey(id)) {
      id = aliases.get(id);
    }
    
    return id;
  }
  
  public void render() {
    start();
    
    for (GraphvizNode node : nodes) {
      renderNode(node);
    }

    for (GraphvizEdge edge : edges) {
      renderEdge(edge);
    }
    
    finish();
    
    out.flush();
  }

  protected Map<String, String> getGraphAttributes() {
    Map<String, String> attrs = Maps.newHashMap();
    attrs.put("rankdir", rankdir);
    return attrs;
  }

  protected void start() {
    out.println("digraph injector {");
    
    Map<String, String> attrs = getGraphAttributes();
    out.println("graph " + getAttrString(attrs) + ";");
  }

  protected void finish() {
    out.println("}");
  }

  protected void renderNode(GraphvizNode node) {
    Map<String, String> attrs = getNodeAttributes(node);
    out.println(node.getNodeId() + " " + getAttrString(attrs));       
  }
  
  protected Map<String, String> getNodeAttributes(GraphvizNode node) {
    Map<String, String> attrs = Maps.newHashMap();

    attrs.put("label", getNodeLabel(node));
    // remove most of the margin because the table has internal padding
    attrs.put("margin", "0.02,0");
    attrs.put("shape", node.getShape().toString());
    attrs.put("style", node.getStyle().toString());
    
    return attrs;
  }

  /**
   * Creates the "label" for a node. This is a string of HTML that defines a
   * table with a heading at the top and (in the case of
   * {@link ImplementationNode}s) rows for each of the member fields.
   */
  protected String getNodeLabel(GraphvizNode node) {
    String cellborder = node.getStyle() == NodeStyle.INVISIBLE ? "1" : "0";
    
    StringBuilder html = new StringBuilder();
    html.append("<");
    html.append("<table cellspacing=\"0\" cellpadding=\"5\" cellborder=\"");
    html.append(cellborder).append("\" border=\"0\">");
    
    html.append("<tr>").append("<td align=\"left\" port=\"header\" ");
    html.append("bgcolor=\"" + node.getHeaderBackgroundColor() + "\">");
    
    String subtitle = Join.join("<br align=\"left\"/>", node.getSubtitles());
    if (subtitle.length() != 0) {
      html.append("<font color=\"").append(node.getHeaderTextColor());
      html.append("\" point-size=\"10\">");
      html.append(subtitle).append("<br align=\"left\"/>").append("</font>");
    }

    html.append("<font color=\"" + node.getHeaderTextColor() + "\">");
    html.append(htmlEscape(node.getTitle())).append("<br align=\"left\"/>");
    html.append("</font>").append("</td>").append("</tr>");

    for (Map.Entry<String, String> field : node.getFields().entrySet()) {
      html.append("<tr>");
      html.append("<td align=\"left\" port=\"").append(field.getKey()).append("\">");
      html.append(htmlEscape(field.getValue()));
      html.append("</td>").append("</tr>");
    }

    html.append("</table>");
    html.append(">");
    return html.toString();
  }

  protected void renderEdge(GraphvizEdge edge) {
    Map<String, String> attrs = getEdgeAttributes(edge);
    
    String tailId = getEdgeEndPoint(resolveAlias(edge.getTailNodeId()), edge.getTailPortId(),
        edge.getTailCompassPoint());

    String headId = getEdgeEndPoint(resolveAlias(edge.getHeadNodeId()), edge.getHeadPortId(),
        edge.getHeadCompassPoint());
    
    out.println(tailId + " -> " + headId + " " + getAttrString(attrs));
  }

  protected Map<String, String> getEdgeAttributes(GraphvizEdge edge) {
    Map<String, String> attrs = Maps.newHashMap();
    
    attrs.put("arrowhead", getArrowString(edge.getArrowHead()));
    attrs.put("arrowtail", getArrowString(edge.getArrowTail()));
    attrs.put("style", edge.getStyle().toString());
    
    return attrs;
  }
  
  private String getAttrString(Map<String, String> attrs) {
    List<String> attrList = Lists.newArrayList();
    
    for (Entry<String, String> attr : attrs.entrySet()) {
      String value = attr.getValue();

      if (value != null) {
        attrList.add(attr.getKey() + "=" + value);
      }
    }
    
    return "[" + Join.join(", ", attrList) + "]";
  }

  /**
   * Turns a {@link List} of {@link ArrowType}s into a {@link String} that
   * represents combining them. With Graphviz, that just means concatenating
   * them.
   */
  protected String getArrowString(List<ArrowType> arrows) {
    return Join.join("", arrows);
  }

  protected String getEdgeEndPoint(String nodeId, String portId, CompassPoint compassPoint) {
    List<String> portStrings = Lists.newArrayList(nodeId);
    
    if (portId != null) {
      portStrings.add(portId);
    }
    
    if (compassPoint != null) {
      portStrings.add(compassPoint.toString());
    }
    
    return Join.join(":", portStrings);
  }

  protected String htmlEscape(String str) {
    return str.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
