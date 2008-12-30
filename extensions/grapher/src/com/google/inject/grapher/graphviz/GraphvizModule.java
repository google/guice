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

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.grapher.BindingEdge;
import com.google.inject.grapher.DependencyEdge;
import com.google.inject.grapher.ImplementationNode;
import com.google.inject.grapher.InterfaceNode;
import com.google.inject.grapher.NameFactory;
import com.google.inject.grapher.NodeAliasFactory;
import com.google.inject.grapher.NodeIdFactory;
import com.google.inject.grapher.Renderer;
import com.google.inject.grapher.ShortNameFactory;
import com.google.inject.grapher.StringNodeIdFactory;

/**
 * Module that provides {@link GraphvizRenderer} as the {@link Renderer} and
 * binds the other Graphviz factories.
 *
 * @author phopkins@gmail.com (Pete Hopkins)
 */
public class GraphvizModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(Renderer.class).to(GraphvizRenderer.class);
    bind(new TypeLiteral<NodeAliasFactory<String>>() {}).to(GraphvizRenderer.class);
    bind(GraphvizRenderer.class).in(Singleton.class);

    bind(NameFactory.class).to(ShortNameFactory.class);
    bind(new TypeLiteral<NodeIdFactory<String>>() {}).to(StringNodeIdFactory.class);
    bind(PortIdFactory.class).to(PortIdFactoryImpl.class);

    bind(new TypeLiteral<BindingEdge.Factory<String, BindingEdge<String>>>() {})
        .to(BindingEdgeFactory.class);
    bind(new TypeLiteral<DependencyEdge.Factory<String, DependencyEdge<String>>>() {})
        .to(DependencyEdgeFactory.class);
    bind(new TypeLiteral<InterfaceNode.Factory<String, InterfaceNode<String>>>() {})
        .to(InterfaceNodeFactory.class);
    bind(new TypeLiteral<ImplementationNode.Factory<String, ImplementationNode<String>>>() {})
        .to(ImplementationNodeFactory.class);
  }
}
