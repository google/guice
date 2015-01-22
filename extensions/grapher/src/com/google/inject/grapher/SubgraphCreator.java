package com.google.inject.grapher;

import com.google.inject.Binding;

/**
 * Creator of subgraph.
 *
 * @author houcheng@gmail.com (Houcheng Lin)
 */
public interface SubgraphCreator {
  /** Find subgraphs recursively for the given dependency graph. */
  Iterable<Subgraph> getSubs(Iterable<Binding<?>> bindings);
}
