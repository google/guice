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

package com.google.inject.internal;

import com.google.common.collect.ImmutableList;
import static com.google.inject.Asserts.assertEqualsBothWays;
import static com.google.inject.Asserts.assertNotSerializable;
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
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import junit.framework.TestCase;

public class TypeResolverTest extends TestCase {
  Type arrayListOfString = newParameterizedType(ArrayList.class, String.class);
  Type hasGenericFieldsOfShort = newParameterizedTypeWithOwner(
      getClass(), HasGenericFields.class, Short.class);
  Type hasGenericConstructorOfShort = newParameterizedTypeWithOwner(
      getClass(), HasGenericConstructor.class, Short.class);
  Type throwerOfNpe = newParameterizedTypeWithOwner(
      getClass(), Thrower.class, NullPointerException.class);
  Type hasArrayOfShort = newParameterizedTypeWithOwner(getClass(), HasArray.class, Short.class);
  Type hasRelatedOfString = newParameterizedTypeWithOwner(
      getClass(), HasRelated.class, String.class, String.class);
  Field list;
  Field instance;
  Constructor<HasGenericConstructor> newHasGenericConstructor;
  Constructor<Thrower> newThrower;
  Constructor newString;
  Method stringIndexOf;
  Method comparableCompareTo;
  Method getArray;
  Method getSetOfArray;
  Method echo;
  Method throwS;

  protected void setUp() throws Exception {
    super.setUp();

    list = HasGenericFields.class.getField("list");
    instance = HasGenericFields.class.getField("instance");
    newHasGenericConstructor = HasGenericConstructor.class.getConstructor(Object.class, Object.class);
    newThrower = Thrower.class.getConstructor();
    stringIndexOf = String.class.getMethod("indexOf", String.class);
    newString = String.class.getConstructor(String.class);
    comparableCompareTo = Comparable.class.getMethod("compareTo", Object.class);
    getArray = HasArray.class.getMethod("getArray");
    getSetOfArray = HasArray.class.getMethod("getSetOfArray");
    echo = HasRelated.class.getMethod("echo", Object.class);
    throwS = Thrower.class.getMethod("throwS");
  }

  public void testDirectInheritance() throws NoSuchMethodException {
    TypeResolver resolver = new TypeResolver(arrayListOfString);
    assertEquals(listOf(String.class),
        resolver.getReturnType(List.class.getMethod("subList", int.class, int.class)));
    assertEquals(ImmutableList.<Type>of(String.class), 
        resolver.getParameterTypes(Collection.class.getMethod("add", Object.class)));
  }
  
  public void testGenericSupertype() {
    TypeResolver resolver = new TypeResolver(arrayListOfString);
    assertEquals(newParameterizedType(Collection.class, String.class),
        resolver.getSupertype(Collection.class));
    assertEquals(newParameterizedType(Iterable.class, String.class),
        resolver.getSupertype(Iterable.class));
    assertEquals(newParameterizedType(AbstractList.class, String.class),
        resolver.getSupertype(AbstractList.class));
    assertEquals(Object.class, resolver.getSupertype(Object.class));
  }

  public void testRecursiveTypeVariable() {
    TypeResolver resolver = new TypeResolver(MyInteger.class);
    assertEquals(MyInteger.class, resolver.getParameterTypes(comparableCompareTo).get(0));
  }

  interface MyComparable<E extends MyComparable<E>> extends Comparable<E> {}

  static class MyInteger implements MyComparable<MyInteger> {
    int value;
    public int compareTo(MyInteger o) {
      return value - o.value;
    }
  }
  
  public void testFields() {
    TypeResolver resolver = new TypeResolver(hasGenericFieldsOfShort);
    assertEquals(listOf(Short.class), resolver.getFieldType(list));
    assertEquals(Short.class, resolver.getFieldType(instance));
  }

  static class HasGenericFields<T> {
    public List<T> list;
    public T instance;
  }

  public void testGenericConstructor() throws NoSuchMethodException {
    TypeResolver resolver = new TypeResolver(hasGenericConstructorOfShort);
    assertEquals(Short.class, resolver.getParameterTypes(newHasGenericConstructor).get(0));
  }

  static class HasGenericConstructor<S> {
    @SuppressWarnings("UnusedDeclaration")
    public <T> HasGenericConstructor(S s, T t) {}
  }

  public void testThrowsExceptions() {
    TypeResolver resolver = new TypeResolver(throwerOfNpe);
    assertEquals(NullPointerException.class, resolver.getExceptionTypes(newThrower).get(0));
    assertEquals(NullPointerException.class, resolver.getExceptionTypes(throwS).get(0));
  }

  static class Thrower<S extends Exception> {
    public Thrower() throws S {}
    public void throwS() throws S {}
  }

  public void testArrays() {
    TypeResolver resolver = new TypeResolver(hasArrayOfShort);
    assertEquals(arrayOf(Short.class), resolver.getReturnType(getArray));
    assertEquals(setOf(arrayOf(Short.class)), resolver.getReturnType(getSetOfArray));
  }

  static interface HasArray<T extends Number> {
    T[] getArray();
    Set<T[]> getSetOfArray();
  }

  public void testRelatedTypeVariables() {
    TypeResolver resolver = new TypeResolver(hasRelatedOfString);
    assertEquals(String.class, resolver.getParameterTypes(echo).get(0));
    assertEquals(String.class, resolver.getReturnType(echo));
  }

  interface HasRelated<T, R extends T> {
    T echo(R r);
  }

  /** Ensure the cache doesn't cache too much */
  public void testCachingAndReindexing() throws NoSuchMethodException {
    TypeResolver resolver = new TypeResolver(
        newParameterizedTypeWithOwner(getClass(), HasLists.class, String.class, Short.class));
    assertEquals(listOf(String.class),
        resolver.getReturnType(HasLists.class.getMethod("listS")));
    assertEquals(listOf(Short.class),
        resolver.getReturnType(HasLists.class.getMethod("listT")));
  }

  interface HasLists<S, T> {
    List<S> listS();
    List<T> listT();
    List<Map.Entry<S, T>> listEntries();
  }

  public void testUnsupportedQueries() throws NoSuchMethodException {
    TypeResolver resolver = new TypeResolver(arrayListOfString);

    try {
      resolver.getExceptionTypes(stringIndexOf);
    } catch (IllegalArgumentException e) {
      assertEquals("public int java.lang.String.indexOf(java.lang.String) is not defined by a "
          + "supertype of java.util.ArrayList<java.lang.String>", e.getMessage());
    }
    try {
      resolver.getParameterTypes(stringIndexOf);
    } catch (Exception e) {
      assertEquals("public int java.lang.String.indexOf(java.lang.String) is not defined by a "
          + "supertype of java.util.ArrayList<java.lang.String>", e.getMessage());
    }
    try {
      resolver.getReturnType(stringIndexOf);
    } catch (Exception e) {
      assertEquals("public int java.lang.String.indexOf(java.lang.String) is not defined by a "
          + "supertype of java.util.ArrayList<java.lang.String>", e.getMessage());
    }
    try {
      resolver.getSupertype(String.class);
    } catch (Exception e) {
      assertEquals("class java.lang.String is not a supertype of "
          + "java.util.ArrayList<java.lang.String>", e.getMessage());
    }
    try {
      resolver.getExceptionTypes(newString);
    } catch (Exception e) {
      assertEquals("public java.lang.String(java.lang.String) does not construct "
          + "a supertype of java.util.ArrayList<java.lang.String>", e.getMessage());
    }
    try {
      resolver.getParameterTypes(newString);
    } catch (Exception e) {
      assertEquals("public java.lang.String(java.lang.String) does not construct "
          + "a supertype of java.util.ArrayList<java.lang.String>", e.getMessage());
    }
  }

  // TODO(jessewilson): tests for tricky bounded types like <T extends Collection, Serializable>
  // TODO(jessewilson): tests for wildcard types

  public void testEqualsAndHashCode() throws IOException {
    TypeResolver a1 = new TypeResolver(arrayListOfString);
    TypeResolver a2 = new TypeResolver(arrayListOfString);
    TypeResolver b = new TypeResolver(listOf(String.class));
    assertEqualsBothWays(a1, a2);
    assertNotSerializable(a1);
    assertFalse(a1.equals(b));
  }
}
