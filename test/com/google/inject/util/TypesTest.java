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

import static com.google.inject.Asserts.assertContains;
import static com.google.inject.Asserts.assertEqualWhenReserialized;
import static com.google.inject.Asserts.assertEqualsBothWays;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.MoreTypes;
import static com.google.inject.util.Types.subtypeOf;
import static com.google.inject.util.Types.supertypeOf;
import java.io.IOException;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
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
  Outer<String>.Inner f;

  private ParameterizedType mapStringInteger;
  private ParameterizedType innerFloatDouble;
  private ParameterizedType listStringArray;
  private ParameterizedType listString;
  private ParameterizedType setString;
  private GenericArrayType stringArray;
  private ParameterizedType outerInner;

  protected void setUp() throws Exception {
    super.setUp();
    mapStringInteger = (ParameterizedType) getClass().getDeclaredField("a").getGenericType();
    innerFloatDouble = (ParameterizedType) getClass().getDeclaredField("b").getGenericType();
    listStringArray = (ParameterizedType) getClass().getDeclaredField("c").getGenericType();
    listString = (ParameterizedType) getClass().getDeclaredField("d").getGenericType();
    setString = (ParameterizedType) getClass().getDeclaredField("e").getGenericType();
    stringArray = (GenericArrayType) listStringArray.getActualTypeArguments()[0];
    outerInner = (ParameterizedType) getClass().getDeclaredField("f").getGenericType();
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
    // The JDK prints this out as:
    //     com.google.inject.util.TypesTest.com.google.inject.util.TypesTest$Inner<java.lang.Float, java.lang.Double>
    // and we think that's wrong, so the assertEquals comparison is worthless. :-(
//    assertEquals(innerFloatDouble.toString(), actual.toString());
    
    // We think the correct comparison is:
    assertEquals("com.google.inject.util.TypesTest$Inner<java.lang.Float, java.lang.Double>", actual.toString());
  }

  public void testTypeParametersMustNotBePrimitives() {
    try {
      Types.newParameterizedType(Map.class, String.class, int.class);
      fail();
    } catch (IllegalArgumentException expected) {
      assertContains(expected.getMessage(),
          "Primitive types are not allowed in type parameters: int");
    }
  }

  public List<? extends CharSequence> wildcardExtends;
  public List<? super CharSequence> wildcardSuper;
  public List<?> wildcardObject;

  public void testWildcardTypes() throws NoSuchFieldException, IOException {
    assertEqualsBothWays(getWildcard("wildcardSuper"), supertypeOf(CharSequence.class));
    assertEqualsBothWays(getWildcard("wildcardExtends"), subtypeOf(CharSequence.class));
    assertEqualsBothWays(getWildcard("wildcardObject"), subtypeOf(Object.class));

    assertEquals("? super java.lang.CharSequence", supertypeOf(CharSequence.class).toString());
    assertEquals("? extends java.lang.CharSequence", subtypeOf(CharSequence.class).toString());
    assertEquals("?", subtypeOf(Object.class).toString());

    assertEqualWhenReserialized(supertypeOf(CharSequence.class));
    assertEqualWhenReserialized(subtypeOf(CharSequence.class));
  }
  
  public void testWildcardBoundsMustNotBePrimitives() {
    try {
      supertypeOf(int.class);
      fail();
    } catch (IllegalArgumentException expected) {
      assertContains(expected.getMessage(),
          "Primitive types are not allowed in wildcard bounds: int");
    }

    try {
      subtypeOf(int.class);
      fail();
    } catch (IllegalArgumentException expected) {
      assertContains(expected.getMessage(),
          "Primitive types are not allowed in wildcard bounds: int");
    }
  }

  private WildcardType getWildcard(String fieldName) throws NoSuchFieldException {
    ParameterizedType type = (ParameterizedType) getClass().getField(fieldName).getGenericType();
    return (WildcardType) type.getActualTypeArguments()[0];
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
    Assert.assertEquals("java.lang.String", MoreTypes.typeToString(String.class));
    assertEquals("java.lang.String[][]", MoreTypes.typeToString(stringArray));
    assertEquals("java.util.Map<java.lang.String, java.lang.Integer>",
        MoreTypes.typeToString(mapStringInteger));
    assertEquals("java.util.List<java.lang.String[][]>",
        MoreTypes.typeToString(listStringArray));
    assertEquals(innerFloatDouble.toString(),
        MoreTypes.typeToString(innerFloatDouble));
  }

  static class Owning<A> {}

  /**
   * Ensure that owning types are required when necessary, and forbidden
   * otherwise.
   */
  public void testCanonicalizeRequiresOwnerTypes() throws NoSuchFieldException {
    try {
      Types.newParameterizedType(Owning.class, String.class);
      fail();
    } catch (IllegalArgumentException expected) {
      assertContains(expected.getMessage(),
          "No owner type for enclosed " + Owning.class);
    }

    try {
      Types.newParameterizedTypeWithOwner(Object.class, Set.class, String.class);
    } catch (IllegalArgumentException expected) {
      assertContains(expected.getMessage(),
          "Owner type for unenclosed " + Set.class);
    }
  }

  @SuppressWarnings("UnusedDeclaration")
  class Inner<T1, T2> {}
  
  public void testInnerParameterizedEvenWithZeroArgs() {
    TypeLiteral<Outer<String>.Inner> type = new TypeLiteral<Outer<String>.Inner>() {};
    assertEqualsBothWays(outerInner, type.getType());

    ParameterizedType parameterizedType = (ParameterizedType) type.getType();
    assertEquals(0, parameterizedType.getActualTypeArguments().length);
    assertEquals(new TypeLiteral<Outer<String>>() {}.getType(), parameterizedType.getOwnerType());
    assertEquals(Outer.Inner.class, parameterizedType.getRawType());
  }

  static class Outer<T> {
    class Inner {}
  }
}
