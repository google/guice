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


package com.google.inject.util;

import com.google.inject.Asserts;
import static com.google.inject.Asserts.assertEqualsBothWays;
import com.google.inject.internal.MoreTypes;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;
import junit.framework.Assert;
import junit.framework.TestCase;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class TypesTest extends TestCase {

  // generic types for comparison
  Map<String, Integer> a;
  Inner<Float, Double> b;
  List<String[][]> c;
  List<String> d;
  Set<String> e;

  private ParameterizedType mapStringInteger;
  private ParameterizedType innerFloatDouble;
  private ParameterizedType listStringArray;
  private ParameterizedType listString;
  private ParameterizedType setString;
  private GenericArrayType stringArray;

  protected void setUp() throws Exception {
    super.setUp();
    mapStringInteger = (ParameterizedType) getClass().getDeclaredField("a").getGenericType();
    innerFloatDouble = (ParameterizedType) getClass().getDeclaredField("b").getGenericType();
    listStringArray = (ParameterizedType) getClass().getDeclaredField("c").getGenericType();
    listString = (ParameterizedType) getClass().getDeclaredField("d").getGenericType();
    setString = (ParameterizedType) getClass().getDeclaredField("e").getGenericType();
    stringArray = (GenericArrayType) listStringArray.getActualTypeArguments()[0];
  }

  public void testListSetMap() {
    assertEqualsBothWays(mapStringInteger, Types.mapOf(String.class, Integer.class));
    assertEqualsBothWays(listString, Types.listOf(String.class));
    assertEqualsBothWays(setString, Types.setOf(String.class));
  }

  public void testDefensiveCopies() {
    Type[] arguments = new Type[] { String.class, Integer.class };
    ParameterizedType parameterizedType = Types.newParameterizedType(Map.class, arguments);
    arguments[0] = null;
    assertEquals(String.class, parameterizedType.getActualTypeArguments()[0]);
    parameterizedType.getActualTypeArguments()[1] = null;
    assertEquals(Integer.class, parameterizedType.getActualTypeArguments()[1]);
  }

  public void testTypeWithOwnerType() {
    ParameterizedType actual = Types.newParameterizedTypeWithOwner(
        TypesTest.class, Inner.class, Float.class, Double.class);
    assertEquals(TypesTest.class, actual.getOwnerType());
    assertEqualsBothWays(innerFloatDouble, actual);
    assertEquals(innerFloatDouble.toString(), actual.toString());
  }

  public void testTypeParametersMustNotBePrimitives() {
    try {
      Types.newParameterizedType(Map.class, String.class, int.class);
      fail();
    } catch (IllegalArgumentException expected) {
      Asserts.assertContains(expected.getMessage(),
          "Parameterized types may not have primitive arguments: int");
    }
  }

  public void testEqualsAndHashcode() {
    ParameterizedType parameterizedType
        = Types.newParameterizedType(Map.class, String.class, Integer.class);
    assertEqualsBothWays(mapStringInteger, parameterizedType);
    assertEquals(mapStringInteger.toString(), parameterizedType.toString());

    GenericArrayType genericArrayType = Types.arrayOf(
        Types.arrayOf(String.class));
    assertEqualsBothWays(stringArray, genericArrayType);
    assertEquals(stringArray.toString(), genericArrayType.toString());
  }

  public void testToString() {
    Assert.assertEquals("java.lang.String", MoreTypes.toString(String.class));
    assertEquals("java.lang.String[][]", MoreTypes.toString(stringArray));
    assertEquals("java.util.Map<java.lang.String, java.lang.Integer>",
        MoreTypes.toString(mapStringInteger));
    assertEquals("java.util.List<java.lang.String[][]>",
        MoreTypes.toString(listStringArray));
    assertEquals(innerFloatDouble.toString(),
        MoreTypes.toString(innerFloatDouble));
  }

  @SuppressWarnings("UnusedDeclaration")
  class Inner<T1, T2> {}
}
