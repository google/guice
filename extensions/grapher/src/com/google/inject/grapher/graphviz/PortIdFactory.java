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

import java.lang.reflect.Member;

/**
 * Interface for a service that returns Graphviz port IDs, used for naming the
 * rows in {@link com.google.inject.grapher.ImplementationNode}-displaying {@link GraphvizNode}s.
 *
 * @author phopkins@gmail.com (Pete Hopkins)
 */
public interface PortIdFactory {
  String getPortId(Member member);
}
