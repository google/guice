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

package com.google.inject.matcher;

import static com.google.inject.Asserts.assertEqualWhenReserialized;
import static com.google.inject.Asserts.assertEqualsBothWays;
import static com.google.inject.matcher.Matchers.annotatedWith;
import static com.google.inject.matcher.Matchers.any;
import static com.google.inject.matcher.Matchers.identicalTo;
import static com.google.inject.matcher.Matchers.inPackage;
import static com.google.inject.matcher.Matchers.inSubpackage;
import static com.google.inject.matcher.Matchers.not;
import static com.google.inject.matcher.Matchers.only;
import static com.google.inject.matcher.Matchers.returns;
import static com.google.inject.matcher.Matchers.subclassesOf;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.util.AbstractList;
import junit.framework.TestCase;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class MatcherTest extends TestCase {

  public void testAny() {
    assertTrue(any().matches(null));
    assertEquals("any()", any().toString());
    assertEqualsBothWays(any(), any());
    assertFalse(any().equals(not(any())));
  }

  public void testNot() {
    assertFalse(not(any()).matches(null));
    assertEquals("not(any())", not(any()).toString());
    assertEqualsBothWays(not(any()), not(any()));
    assertFalse(not(any()).equals(any()));
  }

  public void testAnd() {
    assertTrue(any().and(any()).matches(null));
    assertFalse(any().and(not(any())).matches(null));
    assertEquals("and(any(), any())", any().and(any()).toString());
    assertEqualsBothWays(any().and(any()), any().and(any()));
    assertFalse(any().and(any()).equals(not(any())));
  }

  public void testOr() {
    assertTrue(any().or(not(any())).matches(null));
    assertFalse(not(any()).or(not(any())).matches(null));
    assertEquals("or(any(), any())", any().or(any()).toString());
    assertEqualsBothWays(any().or(any()), any().or(any()));
    assertFalse(any().or(any()).equals(not(any())));
  }

  public void testAnnotatedWith() {
    assertTrue(annotatedWith(Foo.class).matches(Bar.class));
    assertFalse(annotatedWith(Foo.class).matches(
        MatcherTest.class.getMethods()[0]));
    assertEquals("annotatedWith(Foo.class)", annotatedWith(Foo.class).toString());
    assertEqualsBothWays(annotatedWith(Foo.class), annotatedWith(Foo.class));
    assertFalse(annotatedWith(Foo.class).equals(annotatedWith(Named.class)));

    try {
      annotatedWith(Baz.class);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  public void testSubclassesOf() {
    assertTrue(subclassesOf(Runnable.class).matches(Runnable.class));
    assertTrue(subclassesOf(Runnable.class).matches(MyRunnable.class));
    assertFalse(subclassesOf(Runnable.class).matches(Object.class));
    assertEquals("subclassesOf(Runnable.class)", subclassesOf(Runnable.class).toString());
    assertEqualsBothWays(subclassesOf(Runnable.class), subclassesOf(Runnable.class));
    assertFalse(subclassesOf(Runnable.class).equals(subclassesOf(Object.class)));
  }

  public void testOnly() {
    assertTrue(only(1000).matches(1000));
    assertFalse(only(1).matches(1000));
    assertEquals("only(1)", only(1).toString());
    assertEqualsBothWays(only(1), only(1));
    assertFalse(only(1).equals(only(2)));
  }

  @SuppressWarnings("UnnecessaryBoxing")
  public void testIdenticalTo() {
    Object o = new Object();
    assertEquals("identicalTo(1)", identicalTo(1).toString());
    assertTrue(identicalTo(o).matches(o));
    assertFalse(identicalTo(o).matches(new Object()));
    assertEqualsBothWays(identicalTo(o), identicalTo(o));
    assertFalse(identicalTo(1).equals(identicalTo(new Integer(1))));
  }

  public void testInPackage() {
    Package matchersPackage = Matchers.class.getPackage();
    assertEquals("inPackage(com.google.inject.matcher)", inPackage(matchersPackage).toString());
    assertTrue(inPackage(matchersPackage).matches(MatcherTest.class));
    assertFalse(inPackage(matchersPackage).matches(Object.class));
    assertEqualsBothWays(inPackage(matchersPackage), inPackage(matchersPackage));
    assertFalse(inPackage(matchersPackage).equals(inPackage(Object.class.getPackage())));
  }

  public void testInSubpackage() {
    String stringPackageName = String.class.getPackage().getName();
    assertEquals("inSubpackage(java.lang)", inSubpackage(stringPackageName).toString());
    assertTrue(inSubpackage(stringPackageName).matches(Object.class));
    assertTrue(inSubpackage(stringPackageName).matches(Method.class));
    assertFalse(inSubpackage(stringPackageName).matches(Matchers.class));
    assertFalse(inSubpackage("jav").matches(Object.class));
    assertEqualsBothWays(inSubpackage(stringPackageName), inSubpackage(stringPackageName));
    assertFalse(inSubpackage(stringPackageName).equals(inSubpackage(Matchers.class.getPackage().getName())));
  }

  public void testReturns() throws NoSuchMethodException {
    Matcher<Method> predicate = returns(only(String.class));
    assertTrue(predicate.matches(
        Object.class.getMethod("toString")));
    assertFalse(predicate.matches(
        Object.class.getMethod("hashCode")));
    assertEquals("returns(only(class java.lang.String))", returns(only(String.class)).toString());
    assertEqualsBothWays(predicate, returns(only(String.class)));
    assertFalse(predicate.equals(returns(only(Integer.class))));
  }
  
  public void testSerialization() throws IOException {
    assertEqualWhenReserialized(any());
    assertEqualWhenReserialized(not(any()));
    assertEqualWhenReserialized(annotatedWith(Named.class));
    assertEqualWhenReserialized(annotatedWith(Names.named("foo")));
    assertEqualWhenReserialized(only("foo"));
    assertEqualWhenReserialized(identicalTo(Object.class));
    assertEqualWhenReserialized(inPackage(String.class.getPackage()));
    assertEqualWhenReserialized(inSubpackage(String.class.getPackage().getName()));
    assertEqualWhenReserialized(returns(any()));
    assertEqualWhenReserialized(subclassesOf(AbstractList.class));
    assertEqualWhenReserialized(only("a").or(only("b")));
    assertEqualWhenReserialized(only("a").and(only("b")));
  }

  static abstract class MyRunnable implements Runnable {}
  
  @Retention(RetentionPolicy.RUNTIME)
  @interface Foo {}

  @Foo
  static class Bar {}

  @interface Baz {}

  @Baz
  static class Car {}
}
