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

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.BindingTargetVisitor;

import java.util.Collection;

/**
 * Module for the common bindings for {@link InjectorGrapher}. You will also
 * need to bind a {@link Module} that satisfies the {@link Renderer}
 * dependency.
 * <p>
 * If you want to use subtypes of the node and edge classes, or a different
 * node ID type, you will need to override the {@link GraphingVisitor} binding
 * to specify the new type parameters. 
 *
 * @author phopkins@gmail.com (Pete Hopkins)
 */
public class GrapherModule extends AbstractModule {
  @Override
  protected void configure() {
    requireBinding(Renderer.class);

    bind(InjectorGrapher.class);

    bind(new TypeLiteral<BindingTargetVisitor<Object, Collection<Key<?>>>>() {})
        .to(TransitiveDependencyVisitor.class);

    bind(new TypeLiteral<BindingTargetVisitor<Object, Void>>() {})
        .to(new TypeLiteral<GraphingVisitor<String, InterfaceNode<String>,
            ImplementationNode<String>, BindingEdge<String>, DependencyEdge<String>>>() {});
  }
}
