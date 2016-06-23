package com.google.inject.grapher.xml;

import com.google.common.base.Optional;
import com.google.common.collect.*;
import com.google.inject.Inject;
import com.google.inject.grapher.BindingEdge;
import com.google.inject.grapher.NameFactory;
import com.google.inject.grapher.NodeId;
import com.google.inject.grapher.general.GeneralEdge;
import com.google.inject.grapher.general.GeneralGrapher;
import com.google.inject.grapher.general.GeneralNode;
import com.google.inject.grapher.graphviz.PortIdFactory;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A simple class that outputs the gathered Guice "metadata" into XML.
 *
 * @author ksaric
 */
public class XmlGrapher extends GeneralGrapher {

  private PrintWriter out;

  private final List<String> nodesContent = Lists.newLinkedList();
  private final List<String> edgesContent = Lists.newLinkedList();

  @Inject
  XmlGrapher(@Xml final NameFactory nameFactory, @Xml final PortIdFactory portIdFactory) {
    super(nameFactory, portIdFactory);
  }

  public void setOut(final PrintWriter out) {
    this.out = out;
  }

  @Override
  protected void postProcess() {
    start();

    final List<GeneralNode> nodes = Lists.newArrayList(this.nodes.values());

    // we sort it since we want deterministic behaviour
    Collections.sort(
        nodes,
        new Comparator<GeneralNode>() {
          @Override
          public int compare(final GeneralNode generalNode1, GeneralNode generalNode2) {
            return generalNode1.getIdentifier().compareTo(generalNode2.getIdentifier());
          }
        });

    for (final GeneralNode node : nodes) {
      renderNode(node);
    }

    for (final GeneralEdge edge : edges) {
      renderEdge(edge);
    }

    finish();

    out.flush();
  }

  protected void start() {
    out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    out.println("<graph>");
  }

  protected void renderNode(final GeneralNode node) {

    final String currentNodeContent =
        "<node id=\""
            + node.getIdentifier()
            + "\" name=\""
            + node.getTitle()
            + "\" annotation=\""
            + Optional.fromNullable(node.getAnnotation()).or("")
            + "\" type=\""
            + node.getNodeType()
            + "\"/>";

    nodesContent.add(currentNodeContent);
    out.println(currentNodeContent);
  }

  protected void renderEdge(final GeneralEdge edge) {

    final NodeId tailNodeId = edge.getTailNodeId();
    final NodeId headNodeId = edge.getHeadNodeId();

    final String tailIdentifier = nodes.get(tailNodeId).getIdentifier();
    final String headIdentifier = nodes.get(headNodeId).getIdentifier();

    final String edgeContent =
        "<edge "
            + "head_id=\""
            + headIdentifier
            + "\" tail_id=\""
            + tailIdentifier
            + "\" head_name=\""
            + nodes.get(headNodeId).getTitle()
            + "\" tail_name=\""
            + nodes.get(tailNodeId).getTitle()
            + "\" type=\""
            + edge.getEdgeType()
            + "\" binding_type=\""
            + getBindingEdgeType(edge.getBindingEdgeType())
            + "\"/> ";

    edgesContent.add(edgeContent);
    out.println(edgeContent);
  }

  private String getBindingEdgeType(final Optional<BindingEdge.Type> bindingEdgeType) {
    // use Java 8 function and Optional if possible ?!
    if (!bindingEdgeType.isPresent()) {
      return "";
    } else {
      return bindingEdgeType.get().toString();
    }
  }

  protected void finish() {
    out.println("</graph>");
  }

  public List<String> getNodesContent() {
    return nodesContent;
  }

  public List<String> getEdgesContent() {
    return edgesContent;
  }
}
