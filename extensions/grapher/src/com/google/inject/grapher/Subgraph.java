package com.google.inject.grapher;

import java.util.HashSet;
import java.util.Set;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.spi.ExposedBinding;

/**
 * Subgraph to represents a private module.
 *
 * @author houcheng@gmail.com (Houcheng Lin)
 */
public class Subgraph {
  Injector   injector;
  Key <?>    key;
  String     name;
  Set <Node> nodes = new HashSet <Node>();
  Set <Edge> edges = new HashSet <Edge>();
  Subgraph(ExposedBinding binding) {
    injector = binding.getPrivateElements().getInjector();
    key = binding.getKey();
    name = binding.getKey().getTypeLiteral().toString();
  }
}
