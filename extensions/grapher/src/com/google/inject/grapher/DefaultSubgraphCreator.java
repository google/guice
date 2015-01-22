package com.google.inject.grapher;

import java.util.Collection;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.Binding;
import com.google.inject.spi.DefaultBindingTargetVisitor;
import com.google.inject.spi.ExposedBinding;

/**
 * Default subgraph creator.
 *
 * @author houcheng@gmail.com (Houcheng Lin)
 */
public class DefaultSubgraphCreator implements SubgraphCreator {
  @Override
  public Iterable<Subgraph> getSubs(Iterable<Binding<?>> bindings) {
    List<Subgraph> subs = Lists.newArrayList();
    SubgraphVisitor visitor = new SubgraphVisitor();
    for (Binding<?> binding : bindings) {
      subs.addAll(binding.acceptTargetVisitor(visitor));
    }
    return subs;
  }
}

class SubgraphVisitor extends 
  DefaultBindingTargetVisitor<Object, Collection<Subgraph>> {
  @Override
  public Collection<Subgraph> visit(ExposedBinding<?> exposedBinding) {
    return ImmutableList.<Subgraph>of(getSubgraph(exposedBinding));
  }
  @Override public Collection<Subgraph> visitOther(Binding<?> binding) {
    return ImmutableList.of();
  }  
  private Subgraph getSubgraph(ExposedBinding<?> exposedBinding) {
    return new Subgraph(exposedBinding);
  }
}

