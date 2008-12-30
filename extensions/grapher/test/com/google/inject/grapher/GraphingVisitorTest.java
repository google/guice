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

package com.google.inject.grapher;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.spi.ConstructorBinding;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.ProviderInstanceBinding;

import java.util.Collection;
import java.util.List;

import junit.framework.TestCase;

/**
 * Tests for {@link GraphingVisitor}.
 *
 * @author phopkins@gmail.com (Pete Hopkins)
 */
public class GraphingVisitorTest extends TestCase {
  private NodeIdFactory<String> nodeIdFactory;
  private InterfaceNode.Factory<String, InterfaceNode<String>> interfaceNodeFactory;
  private ImplementationNode.Factory<String, ImplementationNode<String>> implementationNodeFactory;
  private BindingEdge.Factory<String, BindingEdge<String>> bindingEdgeFactory;
  private DependencyEdge.Factory<String, DependencyEdge<String>> dependencyEdgeFactory;
  private NodeAliasFactory<String> nodeAliasFactory;

  private GraphingVisitor<String, InterfaceNode<String>, ImplementationNode<String>,
      BindingEdge<String>, DependencyEdge<String>> graphingVisitor;

  private List<Object> mocks;

  @SuppressWarnings("unchecked")
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    mocks = Lists.newArrayList();

    nodeIdFactory = new StringNodeIdFactory();
    interfaceNodeFactory = recordMock(createMock(InterfaceNode.Factory.class));
    implementationNodeFactory = recordMock(createMock(ImplementationNode.Factory.class));
    bindingEdgeFactory = recordMock(createMock(BindingEdge.Factory.class));
    dependencyEdgeFactory = new DependencyEdgeFactory();
    nodeAliasFactory = recordMock(createMock(NodeAliasFactory.class));

    graphingVisitor = new GraphingVisitor<String, InterfaceNode<String>,
        ImplementationNode<String>, BindingEdge<String>, DependencyEdge<String>>(
            nodeIdFactory, interfaceNodeFactory, implementationNodeFactory,
            bindingEdgeFactory, dependencyEdgeFactory, nodeAliasFactory);
  }

  private <T> T recordMock(T mock) {
    mocks.add(mock);
    return mock;
  }

  public void testNewDependencies_withInjectionPoints() throws Exception {
    replayAll();

    ConstructorBinding<?> binding = (ConstructorBinding<?>) createInjector().getBinding(Obj.class);

    Collection<DependencyEdge<String>> edges = graphingVisitor.newDependencyEdges("",
        binding.getInjectionPoints(), binding.getDependencies());

    assertEquals("There should be three edges, from the InjectionPoints", 3, edges.size());
    
    verifyAll();
  }

  public void testNewDependencies_withDependencies() throws Exception {
    replayAll();

    ProviderInstanceBinding<?> binding =
        (ProviderInstanceBinding<?>) createInjector().getBinding(Intf.class);

    Collection<DependencyEdge<String>> edges = graphingVisitor.newDependencyEdges("",
        ImmutableList.<InjectionPoint>of(), binding.getDependencies());

    assertEquals("There should be three edges, from the parameter Dependencies", 3, edges.size());
    
    verifyAll();
  }

  private Injector createInjector() {
    return Guice.createInjector(new TestModule());
  }

  private void replayAll() {
    for (Object mock : mocks) {
      replay(mock);
    }
  }

  private void verifyAll() {
    for (Object mock : mocks) {
      verify(mock);
    }
  }

  private class DependencyEdgeFactory
  implements DependencyEdge.Factory<String, DependencyEdge<String>> {
    public DependencyEdge<String> newDependencyEdge(String fromId,
        InjectionPoint fromPoint, String toId) {
      @SuppressWarnings("unchecked")
      DependencyEdge<String> edge = createMock(DependencyEdge.class);
      return edge;
    }
  }

  private static class TestModule extends AbstractModule {
    @Override
    protected void configure() {
      bind(String.class).toInstance("String");
      bind(Integer.class).toInstance(Integer.valueOf(8));
      bind(Boolean.class).toInstance(Boolean.TRUE);
    }

    @Provides
    public Intf provideIntf(String string, Integer integer, Boolean bool) {
      return null;
    }
  }

  private static interface Intf {}

  private static class Obj {
    @Inject String string;
    @Inject Integer integer;
    @Inject Boolean bool;
  }
}
