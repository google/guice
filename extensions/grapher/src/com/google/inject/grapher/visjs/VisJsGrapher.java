package com.google.inject.grapher.visjs;

import com.google.common.base.Optional;
import com.google.common.collect.*;
import com.google.inject.Inject;
import com.google.inject.grapher.BindingEdge;
import com.google.inject.grapher.Edge;
import com.google.inject.grapher.NameFactory;
import com.google.inject.grapher.NodeId;
import com.google.inject.grapher.general.GeneralEdge;
import com.google.inject.grapher.general.GeneralGrapher;
import com.google.inject.grapher.general.GeneralNode;
import com.google.inject.grapher.graphviz.PortIdFactory;

import java.io.PrintWriter;
import java.util.Comparator;
import java.util.List;

/**
 * A simple class that outputs the gathered Guice "metadata" into basic vis.js.
 * Look <a href="http://visjs.org/index.html">here</a>. Copied from "basic example" and others.
 *
 * @author ksaric
 */
public class VisJsGrapher extends GeneralGrapher {

  private enum GraphColors {
    YELLOW("#FFFF00"),
    LIGHT_BLUE("#97C2FC"),
    ORANGE("#FFA807"),
    RED("#FB7E81"),
    DARK("#00002D"),
    PALE_GREEN("#7BE141"),
    PALE_BLUE("#6E6EFD");

    private final String hexColor;

    GraphColors(final String hexColor) {
      this.hexColor = hexColor;
    }

    public String getHexColor() {
      return hexColor;
    }
  }

  private PrintWriter out;

  private final List<String> nodesContent = Lists.newLinkedList();
  private final List<String> edgesContent = Lists.newLinkedList();

  @Inject
  VisJsGrapher(@VisJs final NameFactory nameFactory, @VisJs final PortIdFactory portIdFactory) {
    super(nameFactory, portIdFactory);
  }

  public void setOut(final PrintWriter out) {
    this.out = out;
  }

  @Override
  protected void postProcess() {
    start();

    final List<GeneralNode> nodes = Lists.newArrayList(this.nodes.values());

    // we sort it since we want deterministic behaviour, could sort it by number (toInteger)
    nodes.sort(
        new Comparator<GeneralNode>() {
          @Override
          public int compare(final GeneralNode generalNode1, GeneralNode generalNode2) {
            return generalNode1.getIdentifier().compareTo(generalNode2.getIdentifier());
          }
        });

    for (final GeneralNode node : nodes) {
      renderNode(node);
    }

    out.println(
        "  ]);\n" + "\n" + "  // create an array with edges\n" + "  var edges = new vis.DataSet([");

    for (final GeneralEdge edge : edges) {
      renderEdge(edge);
    }

    finish();

    out.flush();
  }

  protected void start() {
    out.println(
        "<!DOCTYPE html>\n"
            + "<!-- copied from url http://visjs.org/examples/network/basicUsage.html -->\n"
            + "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n"
            + "  <title>Guice module visualization</title>\n"
            + "\n"
            + "  <script type=\"text/javascript\" src=\"https://cdnjs.cloudflare.com/ajax/libs/vis/4.16.1/vis.min.js\"></script>\n"
            + "  <link href=\"https://cdnjs.cloudflare.com/ajax/libs/vis/4.16.1/vis.min.css\" rel=\"stylesheet\" type=\"text/css\">\n"
            + "\n"
            + "  <style type=\"text/css\">\n"
            + "    #mynetwork {\n"
            + "      width: 1024px;\n"
            + "      height: 768px;\n"
            + "      border: 1px solid lightgray;\n"
            + "    }\n"
            + "  </style>\n"
            + "</head>\n"
            + "<body>\n"
            + "\n"
            + "<div id=\"mynetwork\"></div>\n"
            + "\n"
            + "<script type=\"text/javascript\">\n"
            + "  // create an array with nodes\n"
            + "  var nodes = new vis.DataSet([");
  }

  protected void renderNode(final GeneralNode node) {

    final String nodeContent =
        "\t{id: "
            + node.getIdentifier()
            + ", label: '"
            + node.getTitle()
            + "', "
            + "color:'"
            + getNodeColor(node)
            + "'},";

    nodesContent.add(nodeContent);
    out.println(nodeContent);
  }

  private String getNodeColor(final GeneralNode node) {

    if (node.getNodeType().equals(NodeId.NodeType.TYPE)) {
      return GraphColors.PALE_GREEN.getHexColor();
    } else {
      return GraphColors.PALE_BLUE.getHexColor();
    }
  }

  protected void renderEdge(final GeneralEdge edge) {

    final NodeId tailNodeId = edge.getTailNodeId();
    final NodeId headNodeId = edge.getHeadNodeId();

    final String tailIdentifier = nodes.get(tailNodeId).getIdentifier();
    final String headIdentifier = nodes.get(headNodeId).getIdentifier();

    final String edgeContent =
        "\t{from: "
            + headIdentifier
            + ", to: "
            + tailIdentifier
            + ", color:'"
            + getEdgeColor(edge.getBindingEdgeType())
            + "'"
            + getEdgeArrow(edge.getEdgeType());

    edgesContent.add(edgeContent);
    out.println(edgeContent);
  }

  private String getEdgeArrow(final Edge.Type edgeType) {

    if (edgeType.equals(Edge.Type.BINDING)) {
      return ", arrows:'to' },";
    } else {
      // DEPENDENCY
      return ", arrows:'to', dashes:true },";
    }
  }

  private String getEdgeArrow(final Optional<BindingEdge.Type> bindingEdgeType) {
    if (!bindingEdgeType.isPresent()) return "},";

    final BindingEdge.Type type = bindingEdgeType.get();
    if (type.equals(BindingEdge.Type.NORMAL)) {
      return ", arrows:'to' },";
    } else if (type.equals(BindingEdge.Type.PROVIDER)) {
      return ", arrows:'to', dashes:true },";
    } else if (type.equals(BindingEdge.Type.CONVERTED_CONSTANT)) {
      return ", arrows:'to, from' },";
    } else {
      return ", arrows:{middle:{scaleFactor:0.5},from:true}}";
    }
  }

  private String getEdgeColor(final Optional<BindingEdge.Type> bindingEdgeType) {

    if (!bindingEdgeType.isPresent()) return GraphColors.RED.getHexColor();

    final BindingEdge.Type type = bindingEdgeType.get();
    if (type.equals(BindingEdge.Type.NORMAL)) {
      return GraphColors.LIGHT_BLUE.getHexColor();
    } else if (type.equals(BindingEdge.Type.PROVIDER)) {
      return GraphColors.ORANGE.getHexColor();
    } else if (type.equals(BindingEdge.Type.CONVERTED_CONSTANT)) {
      return GraphColors.YELLOW.getHexColor();
    } else {
      return GraphColors.DARK.getHexColor();
    }
  }

  protected void finish() {
    out.println(
        "  ]);\n"
            + "\n"
            + "  // create a network\n"
            + "  var container = document.getElementById('mynetwork');\n"
            + "  var data = {\n"
            + "    nodes: nodes,\n"
            + "    edges: edges\n"
            + "  };\n"
            + "  var options = {};\n"
            + "  var network = new vis.Network(container, data, options);\n"
            + "</script>\n"
            + "\n"
            + "\n"
            + "</body>\n"
            + "<div></div>\n"
            + "</html>\n");
  }

  public List<String> getNodesContent() {
    return nodesContent;
  }

  public List<String> getEdgesContent() {
    return edgesContent;
  }
}
