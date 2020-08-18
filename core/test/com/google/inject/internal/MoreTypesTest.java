/*
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

import static com.google.inject.Asserts.assertContains;
import static org.junit.Assert.assertThrows;

import com.google.inject.TypeLiteral;
import com.google.inject.internal.MoreTypes.ParameterizedTypeImpl;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import junit.framework.TestCase;

/** @author schmitt@google.com (Peter Schmitt) */
public class MoreTypesTest extends TestCase {

  public void testParameterizedTypeToString() {
    TypeLiteral<Inner<String>> innerString = new TypeLiteral<Inner<String>>() {};
    assertEquals(
        "com.google.inject.internal.MoreTypesTest$Inner<java.lang.String>",
        MoreTypes.typeToString(innerString.getType()));

    TypeLiteral<Set<Inner<Integer>>> mapInnerInteger = new TypeLiteral<Set<Inner<Integer>>>() {};
    assertEquals(
        "java.util.Set<com.google.inject.internal.MoreTypesTest$Inner<java.lang.Integer>>",
        MoreTypes.typeToString(mapInnerInteger.getType()));

    TypeLiteral<Map<Inner<Long>, Set<Inner<Long>>>> mapInnerLongToSetInnerLong =
        new TypeLiteral<Map<Inner<Long>, Set<Inner<Long>>>>() {};
    assertEquals(
        "java.util.Map<com.google.inject.internal.MoreTypesTest$Inner<java.lang.Long>, "
            + "java.util.Set<com.google.inject.internal.MoreTypesTest$Inner<java.lang.Long>>>",
        MoreTypes.typeToString(mapInnerLongToSetInnerLong.getType()));
  }

  public void testParameterizedType_lessArgs() {
    IllegalArgumentException expected =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              new ParameterizedTypeImpl(MoreTypesTest.class, D.class, String.class);
            });
    assertContains(
        expected.getMessage(),
        "Length of provided type arguments is less than length of required parameters for"
            + " class");
  }

  public void testParameterizedType_correctArgs() {

    ParameterizedTypeImpl parameterizedType =
        new ParameterizedTypeImpl(MoreTypesTest.class, D.class, String.class, Integer.class);
    assertEquals(parameterizedType.getRawType(), D.class);
  }

  public void testParameterizedType_moreArgs() {

    ParameterizedTypeImpl parameterizedType =
        new ParameterizedTypeImpl(
            MoreTypesTest.class, D.class, String.class, Integer.class, Integer.class);
    assertEquals(parameterizedType.getRawType(), D.class);
  }

  public <T> void testEquals_typeVariable() throws Exception {
    Type type = getClass().getMethod("testEquals_typeVariable").getTypeParameters()[0];
    assertTrue(MoreTypes.equals(new TypeLiteral<T>() {}.getType(), type));
  }

  public <T> void testGetRawType_wildcard() throws Exception {
    WildcardType wildcard =
        (WildcardType)
            ((ParameterizedType) new TypeLiteral<List<?>>() {}.getType())
                .getActualTypeArguments()[0];
    assertEquals(Object.class, MoreTypes.getRawType(wildcard));
  }

  public static class Inner<T> {}

  static class D<S, T> {}

  static class E extends D<String, Integer> {}
}
