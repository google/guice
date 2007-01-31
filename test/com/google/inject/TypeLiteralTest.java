/**
 * Copyright (C) 2006 Google Inc.
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

package com.google.inject;

import junit.framework.TestCase;

import java.util.List;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class TypeLiteralTest extends TestCase {

  public void testEquality() {
    TypeLiteral<List<String>> t1 = new TypeLiteral<List<String>>() {};
    TypeLiteral<List<String>> t2 = new TypeLiteral<List<String>>() {};
    TypeLiteral<List<Integer>> t3 = new TypeLiteral<List<Integer>>() {};
    TypeLiteral<String> t4 = new TypeLiteral<String>() {};

    assertEquals(t1, t2);
    assertEquals(t2, t1);

    assertFalse(t2.equals(t3));
    assertFalse(t3.equals(t2));

    assertFalse(t2.equals(t4));
    assertFalse(t4.equals(t2));

    TypeLiteral<String> t5 = TypeLiteral.get(String.class);
    assertEquals(t4, t5);
  }

  public void testMissingTypeParameter() {
    try {
      new TypeLiteral() {};
      fail();
    } catch (RuntimeException e) { /* expected */ }
  }
}
