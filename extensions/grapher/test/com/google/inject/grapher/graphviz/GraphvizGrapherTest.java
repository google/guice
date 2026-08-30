/*
 * Copyright (C) 2026 Google Inc.
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
import com.google.inject.grapher.ShortNameFactory;
import java.lang.reflect.Member;
import junit.framework.TestCase;

/** Tests for {@link GraphvizGrapher}. */
public class GraphvizGrapherTest extends TestCase {

  /**
   * The subtitle is rendered inside an HTML table label, so special characters have to be escaped
   * just like the title and the field names/values.
   */
  public void testGetNodeLabelEscapesSubtitle() {
    GraphvizGrapher grapher =
        new GraphvizGrapher(
            new ShortNameFactory(),
            new PortIdFactory() {
              @Override
              public String getPortId(Member member) {
                return member.getName();
              }
            });

    GraphvizNode node = new GraphvizNode(NodeId.newTypeId(Key.get(String.class)));
    node.setTitle("Thing");
    node.addSubtitle(0, "a<b> & c");
    node.setHeaderTextColor("#ffffff");

    String label = grapher.getNodeLabel(node);

    assertTrue(label.contains("a&lt;b&gt; &amp; c"));
    assertFalse(label.contains("a<b> & c"));
  }
}
