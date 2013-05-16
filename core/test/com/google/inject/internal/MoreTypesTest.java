/**
 * Copyright (C) 2010 Google Inc.
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

import com.google.inject.TypeLiteral;

import junit.framework.TestCase;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;

/**
 * @author schmitt@google.com (Peter Schmitt)
 */
public class MoreTypesTest extends TestCase {

  public void testParameterizedTypeToString() {
    TypeLiteral<Inner<String>> innerString = new TypeLiteral<Inner<String>>(){};
    assertEquals("com.google.inject.internal.MoreTypesTest$Inner<java.lang.String>",
        MoreTypes.typeToString(innerString.getType()));

    TypeLiteral<Set<Inner<Integer>>> mapInnerInteger = new TypeLiteral<Set<Inner<Integer>>>() {};
    assertEquals("java.util.Set<com.google.inject.internal.MoreTypesTest$Inner<java.lang.Integer>>",
        MoreTypes.typeToString(mapInnerInteger.getType()));

    TypeLiteral<Map<Inner<Long>, Set<Inner<Long>>>> mapInnerLongToSetInnerLong =
        new TypeLiteral<Map<Inner<Long>, Set<Inner<Long>>>>() {};
    assertEquals("java.util.Map<com.google.inject.internal.MoreTypesTest$Inner<java.lang.Long>, "
            + "java.util.Set<com.google.inject.internal.MoreTypesTest$Inner<java.lang.Long>>>",
        MoreTypes.typeToString(mapInnerLongToSetInnerLong.getType()));
  }

  public <T> void testEquals_typeVariable() throws Exception {
    Type type = getClass().getMethod("testEquals_typeVariable").getTypeParameters()[0];
    assertTrue(MoreTypes.equals(new TypeLiteral<T>() {}.getType(), type));
  }

  public static class Inner<T> {}
}
