/**
 * Copyright (C) 2011 Google Inc.
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

package com.google.inject.grapher;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.AbstractModule;
import com.google.inject.BindingAnnotation;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.spi.InjectionPoint;

import junit.framework.TestCase;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Member;
import java.util.Set;

/**
 * Test cases for {@link AbstractInjectorGrapher}. This indirectly tests most classes in this
 * package.
 *
 * @author bojand@google.com (Bojan Djordjevic)
 */

public class AbstractInjectorGrapherTest extends TestCase {
  private static final String TEST_STRING = "test";

  private static class FakeGrapher extends AbstractInjectorGrapher {
    final Set<Node> nodes = Sets.newHashSet();
    final Set<Edge> edges = Sets.newHashSet();

    @Override protected void reset() {
      nodes.clear();
      edges.clear();
    }

    @Override protected void newInterfaceNode(InterfaceNode node) {
      assertFalse(nodes.contains(node));
      nodes.add(node);
    }

    @Override protected void newImplementationNode(ImplementationNode node) {
      assertFalse(nodes.contains(node));
      nodes.add(node);
    }

    @Override protected void newInstanceNode(InstanceNode node) {
      assertFalse(nodes.contains(node));
      nodes.add(node);
    }

    @Override protected void newDependencyEdge(DependencyEdge edge) {
      assertFalse(edges.contains(edge));
      edges.add(edge);
    }

    @Override protected void newBindingEdge(BindingEdge edge) {
      assertFalse(edges.contains(edge));
      edges.add(edge);
    }

    @Override protected void postProcess() {}
  }

  private static final class Wrapper<T> {
    T value;
  }

  @BindingAnnotation
  @Retention(RetentionPolicy.RUNTIME)
  private static @interface Ann {}
  private static interface IA {}
  private static class A implements IA {
    @Inject public A(String str) {}
  }
  private static class A2 implements IA {
    @Inject public A2(Provider<String> strProvider) {}
  }

  private Node aNode;
  private Node a2Node;
  private Node iaNode;
  private Node iaAnnNode;
  private Node stringNode;
  private Node stringInstanceNode;

  private FakeGrapher grapher;

  @Override protected void setUp() throws Exception {
    super.setUp();
    grapher = new FakeGrapher();
    Node.ignoreSourceInComparisons = true;
    aNode = new ImplementationNode(NodeId.newTypeId(Key.get(A.class)), null,
        ImmutableList.<Member>of(A.class.getConstructor(String.class)));
    a2Node = new ImplementationNode(NodeId.newTypeId(Key.get(A2.class)), null,
        ImmutableList.<Member>of(A2.class.getConstructor(Provider.class)));
    iaNode = new InterfaceNode(NodeId.newTypeId(Key.get(IA.class)), null);
    iaAnnNode = new InterfaceNode(NodeId.newTypeId(Key.get(IA.class, Ann.class)), null);
    stringNode = new InterfaceNode(NodeId.newTypeId(Key.get(String.class)), null);
    stringInstanceNode = new InstanceNode(NodeId.newInstanceId(Key.get(String.class)), null,
        TEST_STRING, ImmutableList.<Member>of());
  }

  public void testLinkedAndInstanceBindings() throws Exception {
    grapher.graph(Guice.createInjector(new AbstractModule() {
        @Override protected void configure() {
          bind(IA.class).to(A.class);
          bind(IA.class).annotatedWith(Ann.class).to(A.class);
          bind(String.class).toInstance(TEST_STRING);
        }
    }));

    Set<Node> expectedNodes =
        ImmutableSet.<Node>of(iaNode, iaAnnNode, aNode, stringNode, stringInstanceNode);
    Set<Edge> expectedEdges = ImmutableSet.<Edge>of(
        new BindingEdge(iaNode.getId(), aNode.getId(), BindingEdge.Type.NORMAL),
        new BindingEdge(iaAnnNode.getId(), aNode.getId(), BindingEdge.Type.NORMAL),
        new BindingEdge(stringNode.getId(), stringInstanceNode.getId(), BindingEdge.Type.NORMAL),
        new DependencyEdge(aNode.getId(), stringNode.getId(),
            InjectionPoint.forConstructor(A.class.getConstructor(String.class))));
    assertEquals(expectedNodes, grapher.nodes);
    assertEquals(expectedEdges, grapher.edges);
  }

  public void testProviderBindings() throws Exception {
    final Wrapper<Provider<A2>> wrapper = new Wrapper<Provider<A2>>();
    grapher.graph(Guice.createInjector(new AbstractModule() {
        @Override protected void configure() {
          wrapper.value = getProvider(A2.class);
          bind(IA.class).toProvider(wrapper.value);
          bind(A2.class);
          bind(String.class).toInstance(TEST_STRING);
        }
    }));

    Node a2ProviderNode = new InstanceNode(NodeId.newInstanceId(Key.get(IA.class)), null,
        wrapper.value, ImmutableList.<Member>of());
    Set<Node> expectedNodes =
        ImmutableSet.<Node>of(iaNode, stringNode, a2Node, stringInstanceNode, a2ProviderNode);
    Set<Edge> expectedEdges = ImmutableSet.<Edge>of(
        new BindingEdge(stringNode.getId(), stringInstanceNode.getId(), BindingEdge.Type.NORMAL),
        new BindingEdge(iaNode.getId(), a2ProviderNode.getId(), BindingEdge.Type.PROVIDER),
        new DependencyEdge(a2Node.getId(), stringNode.getId(),
            InjectionPoint.forConstructor(A2.class.getConstructor(Provider.class))),
        new DependencyEdge(a2ProviderNode.getId(), a2Node.getId(), null));
    assertEquals("wrong nodes", expectedNodes, grapher.nodes);
    assertEquals("wrong edges", expectedEdges, grapher.edges);
  }

  public void testGraphWithGivenRoot() throws Exception {
    grapher.graph(Guice.createInjector(new AbstractModule() {
        @Override protected void configure() {
          bind(IA.class).to(A.class);
          bind(IA.class).annotatedWith(Ann.class).to(A.class);
          bind(String.class).toInstance(TEST_STRING);
        }
    }), ImmutableSet.<Key<?>>of(Key.get(String.class)));

    Set<Node> expectedNodes = ImmutableSet.<Node>of(stringNode, stringInstanceNode);
    Set<Edge> expectedEdges = ImmutableSet.<Edge>of(
        new BindingEdge(stringNode.getId(), stringInstanceNode.getId(), BindingEdge.Type.NORMAL));
    assertEquals(expectedNodes, grapher.nodes);
    assertEquals(expectedEdges, grapher.edges);
  }
}
