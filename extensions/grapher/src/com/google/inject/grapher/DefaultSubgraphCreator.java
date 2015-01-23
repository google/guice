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
import com.google.common.collect.Lists;
import com.google.inject.Binding;
import com.google.inject.spi.ConstructorBinding;
import com.google.inject.spi.DefaultBindingTargetVisitor;
import com.google.inject.spi.ExposedBinding;

import java.util.Collection;
import java.util.List;

/**
 * Default subgraph creator. This creator use a visitor to visit every
 * binding, and for the exposed binding, create one subgraph instance.
 * Later, the graph injector creates nodes and edges for each subgraph.
 *
 * @see AbstractInjectorGrapher
 * @author houcheng@gmail.com (Houcheng Lin)
 */
final class DefaultSubgraphCreator implements SubgraphCreator {
  @Override
  public Iterable<Subgraph> getSubgraphs(Iterable<Binding<?>> bindings) {
    List<Subgraph> subs = Lists.newArrayList();
    SubgraphVisitor visitor = new SubgraphVisitor();
    for (Binding<?> binding : bindings) {
      subs.addAll(binding.acceptTargetVisitor(visitor));
    }
    return subs;
  }

  /**
   * {@link BindingTargetVisitor} that adds subgraphs to the graph based on the visited {@link Binding}.
   */
  private static final class SubgraphVisitor extends 
      DefaultBindingTargetVisitor<Object, Collection<Subgraph>> {

    /**
     * Visitor for {@link ExposedBinding}s. These are for classes that Guice will instantiate to
     * satisfy injection requests.
     */
    @Override
    public Collection<Subgraph> visit(ExposedBinding<?> exposedBinding) {
      return ImmutableList.<Subgraph>of(getSubgraph(exposedBinding));
    }

    @Override public Collection<Subgraph> visitOther(Binding<?> binding) {
      return ImmutableList.of();
    }  

    /**
     * Returns a new sub-graph for the exposed binding.
     *
     * @param exposed binding for the sub-graph to create
     * @return sub-graph for the given binding
     */
    private Subgraph getSubgraph(ExposedBinding<?> exposedBinding) {
      return new Subgraph(exposedBinding);
    }
  }
}
