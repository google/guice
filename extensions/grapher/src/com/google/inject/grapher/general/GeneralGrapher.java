package com.google.inject.grapher.general;

import com.google.common.base.Optional;
import com.google.common.collect.*;
import com.google.inject.Key;
import com.google.inject.grapher.*;
import com.google.inject.grapher.graphviz.PortIdFactory;
import com.google.inject.spi.InjectionPoint;

import java.lang.reflect.Member;
import java.util.List;
import java.util.Map;

/**
 * Grapher that extracts the maximum available amount of information and collects it
 * into {@link GeneralEdge} and {@link GeneralNode}. This way we separate the process of result
 * "graphing" from the process of data gathering and the resulting class is much simpler.
 * The resulting classes that handle that data are then <b>interpreters</b> of the general data strucure.
 * <p>
 * See {@link com.google.inject.grapher.xml.XmlGrapher}
 * <p>
 * See {@link com.google.inject.grapher.visjs.VisJsGrapher}
 *
 * @author ksaric
 */
public abstract class GeneralGrapher extends AbstractInjectorGrapher {

  protected final Map<NodeId, GeneralNode> nodes = Maps.newHashMap();
  protected final List<GeneralEdge> edges = Lists.newArrayList();

  private final NameFactory nameFactory;
  private final PortIdFactory portIdFactory;

  protected GeneralGrapher(final NameFactory nameFactory, final PortIdFactory portIdFactory) {
    this.nameFactory = nameFactory;
    this.portIdFactory = portIdFactory;
  }

  @Override
  protected void reset() {
    nodes.clear();
    edges.clear();
  }

  @Override
  protected void newInterfaceNode(final InterfaceNode node) {
    // TODO(phopkins): Show the Module on the graph, which comes from the
    // class name when source is a StackTraceElement.

    final NodeId nodeId = node.getId();
    final Key<?> key = nodeId.getKey();

    final GeneralNode generalNode = new GeneralNode();
    generalNode.setNodeId(nodeId);
    generalNode.setNodeType(NodeId.NodeType.TYPE);
    generalNode.setTitle(nameFactory.getClassName(key));
    generalNode.setAnnotation(nameFactory.getAnnotationName(key));
    generalNode.setIdentifier(Integer.valueOf(nodes.size()).toString());
    //        generalNode.setIdentifier( "node" + nodes.size() );

    nodes.put(generalNode.getNodeId(), generalNode);
  }

  @Override
  protected void newImplementationNode(final ImplementationNode node) {

    final NodeId nodeId = node.getId();
    final GeneralNode generalNode = new GeneralNode();
    generalNode.setNodeId(nodeId);
    generalNode.setNodeType(NodeId.NodeType.INSTANCE);
    generalNode.setTitle(nameFactory.getClassName(nodeId.getKey()));
    generalNode.setIdentifier(Integer.valueOf(nodes.size()).toString());
    //        generalNode.setIdentifier( "node" + nodes.size() );

    for (final Member member : node.getMembers()) {
      final String portId = portIdFactory.getPortId(member);
      final String memberName = nameFactory.getMemberName(member);

      generalNode.addField(portId, memberName);
    }

    nodes.put(generalNode.getNodeId(), generalNode);
  }

  @Override
  protected void newInstanceNode(final InstanceNode node) {

    final NodeId nodeId = node.getId();
    final String sourceName = nameFactory.getSourceName(node.getSource());

    final GeneralNode generalNode = new GeneralNode();
    generalNode.setNodeId(nodeId);
    generalNode.setNodeType(NodeId.NodeType.INSTANCE);
    generalNode.setTitle(nameFactory.getClassName(nodeId.getKey()));
    generalNode.setSourceName(sourceName);
    generalNode.setIdentifier(Integer.valueOf(nodes.size()).toString());
    //        generalNode.setIdentifier( "node" + nodes.size() );

    for (final Member member : node.getMembers()) {
      final String portId = portIdFactory.getPortId(member);
      final String memberName = nameFactory.getMemberName(member);

      generalNode.addField(portId, memberName);
    }

    nodes.put(generalNode.getNodeId(), generalNode);
  }

  @Override
  protected void newDependencyEdge(final DependencyEdge edge) {

    final GeneralEdge generalEdge = new GeneralEdge();
    generalEdge.setHeadNodeId(edge.getFromId());
    generalEdge.setTailNodeId(edge.getToId());

    // Enum - free or bound?
    final InjectionPoint fromPoint = edge.getInjectionPoint();
    if (fromPoint == null) {
      generalEdge.setTailPortId("header");
    } else {
      final String portId = portIdFactory.getPortId(fromPoint.getMember());
      generalEdge.setTailPortId(portId);
    }

    generalEdge.setEdgeType(Edge.Type.DEPENDENCY);
    generalEdge.setBindingEdgeType(Optional.<BindingEdge.Type>absent());

    edges.add(generalEdge);
  }

  @Override
  protected void newBindingEdge(final BindingEdge edge) {
    final GeneralEdge generalEdge = new GeneralEdge();
    generalEdge.setHeadNodeId(edge.getFromId());
    generalEdge.setTailNodeId(edge.getToId());

    generalEdge.setEdgeType(Edge.Type.BINDING);
    generalEdge.setBindingEdgeType(Optional.fromNullable(edge.getType()));

    edges.add(generalEdge);
  }
}
