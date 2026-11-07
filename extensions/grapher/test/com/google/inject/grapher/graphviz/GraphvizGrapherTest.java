/*
 * Copyright (C) 2024 Google Inc.
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

import com.google.inject.Key;
import com.google.inject.grapher.NodeId;
import junit.framework.TestCase;

/**
 * Tests for {@link GraphvizGrapher}.
 *
 * @author phopkins@gmail.com (Pete Hopkins)
 */
public class GraphvizGrapherTest extends TestCase {

  public void testGetNodeLabelEscapesSubtitles() {
    GraphvizGrapher grapher = new GraphvizGrapher(null, null);
    GraphvizNode node = new GraphvizNode(NodeId.newTypeId(Key.get(String.class)));
    node.setTitle("Title");
    node.addSubtitle(0, "@Named(\"<script>&\")");

    String label = grapher.getNodeLabel(node);

    assertTrue(label, label.contains("@Named(\"&lt;script&gt;&amp;\")"));
    assertFalse(label, label.contains("<script>"));
  }
}
