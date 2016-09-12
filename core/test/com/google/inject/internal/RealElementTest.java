/*
 * Copyright (C) 2014 Google Inc.
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

package com.google.inject.internal;

import com.google.inject.internal.Element.Type;
import junit.framework.TestCase;

/** Tests for {@link com.google.inject.internal.RealElement}. */
public class RealElementTest extends TestCase {

  private Element systemElement;
  private RealElement realElement;

  @Override
  protected void setUp() throws Exception {
    this.systemElement = Holder.class.getAnnotation(Element.class);
    this.realElement = new RealElement("b", Type.MULTIBINDER, "a", 1);
  }

  public void testEquals() {
    assertEquals(systemElement, realElement);
    assertEquals(realElement, systemElement);
  }

  public void testHashCode() {
    assertEquals(systemElement.hashCode(), realElement.hashCode());
  }

  public void testProperties() {
    assertEquals("a", realElement.keyType());
    assertEquals("b", realElement.setName());
    assertEquals(Type.MULTIBINDER, realElement.type());
    assertEquals(1, realElement.uniqueId());
  }

  @Element(keyType = "a", setName = "b", type = Type.MULTIBINDER, uniqueId = 1)
  static class Holder {}
}
