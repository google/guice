package com.google.inject.grapher.general;

import com.google.common.collect.*;
import com.google.inject.grapher.NodeId;

import java.util.Map;

/**
 * The most general <b>node</b> that we can gather from Guice "metadata".
 * It's much easier to shuffle data later than to write custom functions that fetch required data.
 *
 * @author ksaric
 */
public class GeneralNode {

  private String identifier;
  private NodeId nodeId;
  private String title;
  private String annotation;
  private NodeId.NodeType nodeType;
  private Map<String, String> fields = Maps.newLinkedHashMap();
  private String sourceName;

  public GeneralNode() {}

  public String getIdentifier() {
    return identifier;
  }

  public void setIdentifier(String identifier) {
    this.identifier = identifier;
  }

  public NodeId getNodeId() {
    return nodeId;
  }

  public void setNodeId(NodeId nodeId) {
    this.nodeId = nodeId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getAnnotation() {
    return annotation;
  }

  public void setAnnotation(String annotation) {
    this.annotation = annotation;
  }

  public NodeId.NodeType getNodeType() {
    return nodeType;
  }

  public void setNodeType(NodeId.NodeType nodeType) {
    this.nodeType = nodeType;
  }

  public void addField(String portId, String memberName) {
    fields.put(portId, memberName);
  }

  public void setSourceName(String sourceName) {
    this.sourceName = sourceName;
  }

  public String getSourceName() {
    return sourceName;
  }
}
