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

import com.google.inject.Binding;

/**
 * Creator of node aliases. Used by dependency graphers to merge nodes in the internal Guice graph
 * into a single node on the rendered graph.
 *
 * @author bojand@google.com (Bojan Djordjevic)
 * @since 4.0
 */
public interface AliasCreator {
  /**
   * Returns aliases for the given dependency graph. The aliases do not need to be transitively
   * resolved, i.e. it is valid to return an alias (X to Y) and an alias (Y to Z). It is the
   * responsibility of the caller to resolve this to (X to Z) and (Y to Z).
   *
   * @param bindings bindings that make up the dependency graph
   * @return aliases that should be applied on the graph
   */
  Iterable<Alias> createAliases(Iterable<Binding<?>> bindings);
}
