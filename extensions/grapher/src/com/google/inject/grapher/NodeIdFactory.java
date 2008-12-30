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

import com.google.inject.Key;

/**
 * Factory for abstract identifiers for elements on the graph. Most graph nodes
 * will correspond directly to {@link Key}s, but we do this for additional
 * flexibility and because instances do not have separate {@link Key}s from the
 * interfaces they are bound to.
 * <p>
 * Node IDs are treated as opaque values by {@link GraphingVisitor} and the
 * other classes in this package.
 *
 * @author phopkins@gmail.com (Pete Hopkins)
 * 
 * @param <K> The type for node IDs.
 */
public interface NodeIdFactory<K> {
  K getClassNodeId(Key<?> key);
  K getInstanceNodeId(Key<?> key);
}
