package com.google.inject.grapher.general;

import com.google.common.base.Optional;
import com.google.inject.grapher.BindingEdge;
import com.google.inject.grapher.Edge;
import com.google.inject.grapher.NodeId;

/**
 * The most general <b>edge</b> that we can gather from Guice "metadata".
 * It's much easier to shuffle data later than to write custom functions that fetch required data.
 *
 * @author ksaric
 */
public class GeneralEdge {

  private NodeId tailNodeId;
  private NodeId headNodeId;
  private String tailPortId;
  private Edge.Type edgeType;
  private Optional<BindingEdge.Type> bindingEdgeType; // Guava optional, compatibility

  public NodeId getTailNodeId() {
    return tailNodeId;
  }

  public void setTailNodeId(NodeId tailNodeId) {
    this.tailNodeId = tailNodeId;
  }

  public NodeId getHeadNodeId() {
    return headNodeId;
  }

  public void setHeadNodeId(NodeId headNodeId) {
    this.headNodeId = headNodeId;
  }

  public void setTailPortId(String tailPortId) {
    this.tailPortId = tailPortId;
  }

  public String getTailPortId() {
    return tailPortId;
  }

  public void setEdgeType(Edge.Type edgeType) {
    this.edgeType = edgeType;
  }

  public Edge.Type getEdgeType() {
    return edgeType;
  }

  public void setBindingEdgeType(Optional<BindingEdge.Type> bindingEdgeType) {
    this.bindingEdgeType = bindingEdgeType;
  }

  public Optional<BindingEdge.Type> getBindingEdgeType() {
    return bindingEdgeType;
  }
}
