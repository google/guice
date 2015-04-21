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
 * Creator of graph edges to render. All edges will be rendered on the graph after node aliasing is
 * performed.
 *
 * @author bojand@google.com (Bojan Djordjevic)
 * @since 4.0
 */
public interface EdgeCreator {

  /** Returns edges for the given dependency graph. */
  Iterable<Edge> getEdges(Iterable<Binding<?>> bindings);
}
