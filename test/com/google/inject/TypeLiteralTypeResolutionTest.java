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

package com.google.inject;

import static com.google.inject.Asserts.assertEqualsBothWays;
import static com.google.inject.Asserts.assertNotSerializable;
import com.google.inject.internal.util.ImmutableList;
import com.google.inject.util.Types;
import static com.google.inject.util.Types.arrayOf;
import static com.google.inject.util.Types.listOf;
import static com.google.inject.util.Types.newParameterizedType;
import static com.google.inject.util.Types.newParameterizedTypeWithOwner;
import static com.google.inject.util.Types.setOf;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import junit.framework.TestCase;

/**
 * This test checks that TypeLiteral can perform type resolution on its members.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class TypeLiteralTypeResolutionTest extends TestCase {
  Type arrayListOfString = newParameterizedType(ArrayList.class, String.class);
  Type hasGenericFieldsOfShort = newParameterizedTypeWithOwner(
      getClass(), HasGenericFields.class, Short.class);
  Type hasGenericConstructorOfShort = newParameterizedTypeWithOwner(
      getClass(), GenericConstructor.class, Short.class);
  Type throwerOfNpe = newParameterizedTypeWithOwner(
      getClass(), Thrower.class, NullPointerException.class);
  Type hasArrayOfShort = newParameterizedTypeWithOwner(getClass(), HasArray.class, Short.class);
  Type hasRelatedOfString = newParameterizedTypeWithOwner(
      getClass(), HasRelated.class, String.class, String.class);
  Type mapK = Map.class.getTypeParameters()[0];
  Type hashMapK = HashMap.class.getTypeParameters()[0];
  Type setEntryKV;
  Type entryStringInteger = setOf(newParameterizedTypeWithOwner(
      Map.class, Map.Entry.class, String.class, Integer.class));
  Field list;
  Field instance;
  Constructor<GenericConstructor> newHasGenericConstructor;
  Constructor<Thrower> newThrower;
  Constructor newString;
  Method stringIndexOf;
  Method comparableCompareTo;
  Method getArray;
  Method getSetOfArray;
  Method echo;
  Method throwS;

  @Override protected void setUp() throws Exception {
    super.setUp();

    list = HasGenericFields.class.getField("list");
    instance = HasGenericFields.class.getField("instance");
    newHasGenericConstructor = GenericConstructor.class.getConstructor(Object.class, Object.class);
    newThrower = Thrower.class.getConstructor();
    stringIndexOf = String.class.getMethod("indexOf", String.class);
    newString = String.class.getConstructor(String.class);
    comparableCompareTo = Comparable.class.getMethod("compareTo", Object.class);
    getArray = HasArray.class.getMethod("getArray");
    getSetOfArray = HasArray.class.getMethod("getSetOfArray");
    echo = HasRelated.class.getMethod("echo", Object.class);
    throwS = Thrower.class.getMethod("throwS");
    setEntryKV = HashMap.class.getMethod("entrySet").getGenericReturnType();
  }

  public void testDirectInheritance() throws NoSuchMethodException {
    TypeLiteral<?> resolver = TypeLiteral.get(arrayListOfString);
    assertEquals(listOf(String.class),
        resolver.getReturnType(List.class.getMethod("subList", int.class, int.class)).getType());
    assertEquals(ImmutableList.<TypeLiteral<?>>of(TypeLiteral.get(String.class)),
        resolver.getParameterTypes(Collection.class.getMethod("add", Object.class)));
  }
  
  public void testGenericSupertype() {
    TypeLiteral<?> resolver = TypeLiteral.get(arrayListOfString);
    assertEquals(newParameterizedType(Collection.class, String.class),
        resolver.getSupertype(Collection.class).getType());
    assertEquals(newParameterizedType(Iterable.class, String.class),
        resolver.getSupertype(Iterable.class).getType());
    assertEquals(newParameterizedType(AbstractList.class, String.class),
        resolver.getSupertype(AbstractList.class).getType());
    assertEquals(Object.class, resolver.getSupertype(Object.class).getType());
  }

  public void testRecursiveTypeVariable() {
    TypeLiteral<?> resolver = TypeLiteral.get(MyInteger.class);
    assertEquals(MyInteger.class, resolver.getParameterTypes(comparableCompareTo).get(0).getType());
  }

  interface MyComparable<E extends MyComparable<E>> extends Comparable<E> {}

  static class MyInteger implements MyComparable<MyInteger> {
    int value;
    public int compareTo(MyInteger o) {
      return value - o.value;
    }
  }
  
  public void testFields() {
    TypeLiteral<?> resolver = TypeLiteral.get(hasGenericFieldsOfShort);
    assertEquals(listOf(Short.class), resolver.getFieldType(list).getType());
    assertEquals(Short.class, resolver.getFieldType(instance).getType());
  }

  static class HasGenericFields<T> {
    public List<T> list;
    public T instance;
  }

  public void testGenericConstructor() throws NoSuchMethodException {
    TypeLiteral<?> resolver = TypeLiteral.get(hasGenericConstructorOfShort);
    assertEquals(Short.class,
        resolver.getParameterTypes(newHasGenericConstructor).get(0).getType());
  }

  static class GenericConstructor<S> {
    @SuppressWarnings("UnusedDeclaration")
    public <T> GenericConstructor(S s, T t) {}
  }

  public void testThrowsExceptions() {
    TypeLiteral<?> type = TypeLiteral.get(throwerOfNpe);
    assertEquals(NullPointerException.class, type.getExceptionTypes(newThrower).get(0).getType());
    assertEquals(NullPointerException.class, type.getExceptionTypes(throwS).get(0).getType());
  }

  static class Thrower<S extends Exception> {
    public Thrower() throws S {}
    public void throwS() throws S {}
  }

  public void testArrays() {
    TypeLiteral<?> resolver = TypeLiteral.get(hasArrayOfShort);
    assertEquals(arrayOf(Short.class), resolver.getReturnType(getArray).getType());
    assertEquals(setOf(arrayOf(Short.class)), resolver.getReturnType(getSetOfArray).getType());
  }

  static interface HasArray<T extends Number> {
    T[] getArray();
    Set<T[]> getSetOfArray();
  }

  public void testRelatedTypeVariables() {
    TypeLiteral<?> resolver = TypeLiteral.get(hasRelatedOfString);
    assertEquals(String.class, resolver.getParameterTypes(echo).get(0).getType());
    assertEquals(String.class, resolver.getReturnType(echo).getType());
  }

  interface HasRelated<T, R extends T> {
    T echo(R r);
  }

  /** Ensure the cache doesn't cache too much */
  public void testCachingAndReindexing() throws NoSuchMethodException {
    TypeLiteral<?> resolver = TypeLiteral.get(
        newParameterizedTypeWithOwner(getClass(), HasLists.class, String.class, Short.class));
    assertEquals(listOf(String.class),
        resolver.getReturnType(HasLists.class.getMethod("listS")).getType());
    assertEquals(listOf(Short.class),
        resolver.getReturnType(HasLists.class.getMethod("listT")).getType());
  }

  interface HasLists<S, T> {
    List<S> listS();
    List<T> listT();
    List<Map.Entry<S, T>> listEntries();
  }

  public void testUnsupportedQueries() throws NoSuchMethodException {
    TypeLiteral<?> resolver = TypeLiteral.get(arrayListOfString);

    try {
      resolver.getExceptionTypes(stringIndexOf);
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals("public int java.lang.String.indexOf(java.lang.String) is not defined by a "
          + "supertype of java.util.ArrayList<java.lang.String>", e.getMessage());
    }
    try {
      resolver.getParameterTypes(stringIndexOf);
      fail();
    } catch (Exception e) {
      assertEquals("public int java.lang.String.indexOf(java.lang.String) is not defined by a "
          + "supertype of java.util.ArrayList<java.lang.String>", e.getMessage());
    }
    try {
      resolver.getReturnType(stringIndexOf);
      fail();
    } catch (Exception e) {
      assertEquals("public int java.lang.String.indexOf(java.lang.String) is not defined by a "
          + "supertype of java.util.ArrayList<java.lang.String>", e.getMessage());
    }
    try {
      resolver.getSupertype(String.class);
      fail();
    } catch (Exception e) {
      assertEquals("class java.lang.String is not a supertype of "
          + "java.util.ArrayList<java.lang.String>", e.getMessage());
    }
    try {
      resolver.getExceptionTypes(newString);
      fail();
    } catch (Exception e) {
      assertEquals("public java.lang.String(java.lang.String) does not construct "
          + "a supertype of java.util.ArrayList<java.lang.String>", e.getMessage());
    }
    try {
      resolver.getParameterTypes(newString);
      fail();
    } catch (Exception e) {
      assertEquals("public java.lang.String(java.lang.String) does not construct "
          + "a supertype of java.util.ArrayList<java.lang.String>", e.getMessage());
    }
  }

  public void testResolve() {
    TypeLiteral<?> typeResolver = TypeLiteral.get(StringIntegerMap.class);
    assertEquals(String.class, typeResolver.resolveType(mapK));

    typeResolver = new TypeLiteral<Map<String, Integer>>() {};
    assertEquals(String.class, typeResolver.resolveType(mapK));
    assertEquals(Types.mapOf(String.class, Integer.class),
        typeResolver.getSupertype(Map.class).getType());

    typeResolver = new TypeLiteral<BetterMap<String, Integer>>() {};
    assertEquals(String.class, typeResolver.resolveType(mapK));

    typeResolver = new TypeLiteral<BestMap<String, Integer>>() {};
    assertEquals(String.class, typeResolver.resolveType(mapK));

    typeResolver = TypeLiteral.get(StringIntegerHashMap.class);
    assertEquals(String.class, typeResolver.resolveType(mapK));
    assertEquals(String.class, typeResolver.resolveType(hashMapK));
    assertEquals(entryStringInteger, typeResolver.resolveType(setEntryKV));
    assertEquals(Object.class, typeResolver.getSupertype(Object.class).getType());
  }

  public void testOnObject() {
    TypeLiteral<?> typeResolver = TypeLiteral.get(Object.class);
    assertEquals(Object.class, typeResolver.getSupertype(Object.class).getType());
    assertEquals(Object.class, typeResolver.getRawType());

    // interfaces also resolve Object
    typeResolver = TypeLiteral.get(Types.setOf(Integer.class));
    assertEquals(Object.class, typeResolver.getSupertype(Object.class).getType());
  }

  interface StringIntegerMap extends Map<String, Integer> {}
  interface BetterMap<K1, V1> extends Map<K1, V1> {}
  interface BestMap<K2, V2> extends BetterMap<K2, V2> {}
  static class StringIntegerHashMap extends HashMap<String, Integer> {}

  public void testGetSupertype() {
    TypeLiteral<AbstractList<String>> listOfString = new TypeLiteral<AbstractList<String>>() {};
    assertEquals(Types.newParameterizedType(AbstractCollection.class, String.class),
        listOfString.getSupertype(AbstractCollection.class).getType());

    TypeLiteral arrayListOfE = TypeLiteral.get(newParameterizedType(
        ArrayList.class, ArrayList.class.getTypeParameters()));
    assertEquals(
        newParameterizedType(AbstractCollection.class, ArrayList.class.getTypeParameters()),
        arrayListOfE.getSupertype(AbstractCollection.class).getType());
  }

  public void testGetSupertypeForArraysAsList() {
    Class<? extends List> arraysAsListClass = Arrays.asList().getClass();
    Type anotherE = arraysAsListClass.getTypeParameters()[0];
    TypeLiteral type = TypeLiteral.get(newParameterizedType(AbstractList.class, anotherE));
    assertEquals(newParameterizedType(AbstractCollection.class, anotherE),
        type.getSupertype(AbstractCollection.class).getType());
  }

  public void testWildcards() throws NoSuchFieldException {
    TypeLiteral<Parameterized<String>> ofString = new TypeLiteral<Parameterized<String>>() {};

    assertEquals(new TypeLiteral<List<String>>() {}.getType(),
        ofString.getFieldType(Parameterized.class.getField("t")).getType());
    assertEquals(new TypeLiteral<List<? extends String>>() {}.getType(),
        ofString.getFieldType(Parameterized.class.getField("extendsT")).getType());
    assertEquals(new TypeLiteral<List<? super String>>() {}.getType(),
        ofString.getFieldType(Parameterized.class.getField("superT")).getType());
  }

  static class Parameterized<T> {
    public List<T> t;
    public List<? extends T> extendsT;
    public List<? super T> superT;
  }

  // TODO(jessewilson): tests for tricky bounded types like <T extends Collection, Serializable>

  public void testEqualsAndHashCode() throws IOException {
    TypeLiteral<?> a1 = TypeLiteral.get(arrayListOfString);
    TypeLiteral<?> a2 = TypeLiteral.get(arrayListOfString);
    TypeLiteral<?> b = TypeLiteral.get(listOf(String.class));
    assertEqualsBothWays(a1, a2);
    assertNotSerializable(a1);
    assertFalse(a1.equals(b));
  }
}
