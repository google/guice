/**
 * Copyright (C) 2009 Google Inc.
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

package com.google.inject.util;

import com.google.inject.Binding;
import com.google.inject.ConfigurationException;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Scope;
import com.google.inject.Scopes;
import com.google.inject.internal.ImmutableList;
import com.google.inject.internal.ImmutableMap;
import com.google.inject.internal.Lists;
import com.google.inject.internal.Maps;
import static com.google.inject.internal.Preconditions.checkArgument;
import com.google.inject.spi.BindingScopingVisitor;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.HasDependencies;
import com.google.inject.spi.Message;
import com.google.inject.spi.ProviderBinding;
import java.lang.annotation.Annotation;
import static java.util.Arrays.asList;
import java.util.List;
import java.util.Map;

/**
 * Inspects an injector for scoping violations. Scoping violations exist whenever a long-lived
 * object (such as a singleton) depends on a short-lived object (such as a request-scoped object).
 * To use, create an scope checker and call it's {@code check()} method with your scoping
 * annotations in decreasing duration:
 * <pre><code>
 *     ScopeChecker scopeChecker = new ScopeChecker(injector);
 *     scopeChecker.check(Singleton.class, SessionScoped.class, RequestScoped.class);
 * </code></pre>
 * If there are scoping violations in the injector, the call will fail with a detailed {@code
 * ConfigurationException}.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class ScopeChecker {

  private final Injector injector;

  public ScopeChecker(Injector injector) {
    this.injector = injector;
  }

  /**
   * Checks this checker's injector for scoping violations.
   *
   * @param longest the outermost scope, such as {@code Singleton.class}.
   * @param nested a scope immediately nested within {@code longest}
   * @param furtherNested any scopes nested within {@code nested}, in decreasing duration.
   * @throws ConfigurationException if any violations are found.
   */
  public void check(Class<? extends Annotation> longest, Class<? extends Annotation> nested,
      Class<? extends Annotation>... furtherNested) {
    Ranker ranker = new Ranker(longest, nested, furtherNested);
    Map<Key<?>, Node> nodes = Maps.newHashMap();

    // build the graph of node dependencies with scope ranks
    for (Binding<?> binding : injector.getAllBindings().values()) {
      Key<?> key = binding.getKey();
      Node node = getNode(nodes, key);
      ranker.rank(binding, node);

      // explicitly ignore dependencies that come via providers.
      if (binding instanceof ProviderBinding) {
        continue;
      }

      if (binding instanceof HasDependencies) {
        HasDependencies hasDependencies = (HasDependencies) binding;
        for (Dependency<?> dependency : hasDependencies.getDependencies()) {
          getNode(nodes, dependency.getKey()).addUser(node);
        }
      }
    }

    // walk through the nodes, pushing effective scopes through dependencies
    for (Node node : nodes.values()) {
      node.pushScopeToUsers();
    }

    // on the nodes with dependencies narrower than themselves, print an error
    List<Message> messages = Lists.newArrayList();
    for (Node node : nodes.values()) {
      if (node.isScopedCorrectly()) {
        continue;
      }

      StringBuilder error = new StringBuilder("Illegal scoped dependency: ").append(node);
      Node dependency = node;
      do {
        dependency = dependency.effectiveScopeDependency();
        error.append("\n  depends on ").append(dependency);
      } while (!dependency.isEffectiveScopeAppliedScope());
      messages.add(new Message(error.toString()));
    }

    if (!messages.isEmpty()) {
      throw new ConfigurationException(messages);
    }
  }

  private Node getNode(Map<Key<?>, Node> nodes, Key<?> key) {
    Node node = nodes.get(key);
    if (node == null) {
      node = new Node(key);
      nodes.put(key, node);
    }
    return node;
  }

  /**
   * Applies the scoping rank to a node. Scopes are stored as integers, and narrower scopes get
   * greater values.
   */
  private class Ranker implements BindingScopingVisitor<Scope> {
    private final ImmutableList<Class<? extends Annotation>> scopeAnnotations;
    private final ImmutableMap<Scope, Integer> scopeToRank;

    private Ranker(Class<? extends Annotation> longest, Class<? extends Annotation> nested,
      Class<? extends Annotation>... furtherNested) {
      scopeAnnotations = new ImmutableList.Builder<Class<? extends Annotation>>()
          .add(longest)
          .add(nested)
          .addAll(asList(furtherNested))
          .build();

      ImmutableMap.Builder<Scope, Integer> scopeToRankBuilder = ImmutableMap.builder();
      Map<Class<? extends Annotation>, Scope> annotationToScope = injector.getScopeBindings();
      int i = 0;
      for (Class<? extends Annotation> scopeAnnotation : scopeAnnotations) {
        Scope scope = annotationToScope.get(scopeAnnotation);
        checkArgument(scope != null, "No scope binding for %s", scopeAnnotation);
        scopeToRankBuilder.put(scope, i++);
      }
      scopeToRank = scopeToRankBuilder.build();
    }

    public void rank(Binding<?> binding, Node node) {
      Scope scope = binding.acceptScopingVisitor(this);
      Integer rank = scopeToRank.get(scope);
      if (rank != null) {
        node.setScopeRank(rank, scopeAnnotations.get(rank));
      }
    }

    public Scope visitEagerSingleton() {
      return Scopes.SINGLETON;
    }

    public com.google.inject.Scope visitScope(com.google.inject.Scope scope) {
      return scope;
    }

    public Scope visitScopeAnnotation(Class<? extends Annotation> scopeAnnotation) {
      throw new AssertionError();
    }

    public Scope visitNoScoping() {
      return Scopes.NO_SCOPE;
    }
  }
}
