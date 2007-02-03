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

package com.google.inject.intercept;

import static com.google.inject.intercept.Queries.*;

import junit.framework.TestCase;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class QueryTest extends TestCase {

  public void testAny() {
    assertTrue(all().matches(null));
  }

  public void testNot() {
    assertFalse(not(all()).matches(null));
  }

  public void testAnd() {
    assertTrue(all().and(all()).matches(null));
    assertFalse(all().and(not(all())).matches(null));
  }

  public void testAnnotatedWith() {
    assertTrue(annotatedWith(Foo.class).matches(Bar.class));
    assertFalse(annotatedWith(Foo.class).matches(
        QueryTest.class.getMethods()[0]));
  }

  public void testSubclassesOf() {
    assertTrue(subclassesOf(Runnable.class).matches(Runnable.class));
    assertTrue(subclassesOf(Runnable.class).matches(MyRunnable.class));
    assertFalse(subclassesOf(Runnable.class).matches(Object.class));
  }

  public void testOnly() {
    assertTrue(only(1000).matches(new Integer(1000)));
    assertFalse(only(1).matches(new Integer(1000)));
  }

  public void testSameAs() {
    Object o = new Object();
    assertTrue(only(o).matches(o));
    assertFalse(only(o).matches(new Object()));
  }

  public void testInPackage() {
    assertTrue(inPackage(Queries.class.getPackage())
        .matches(QueryTest.class));
    assertFalse(inPackage(Queries.class.getPackage())
        .matches(Object.class));
  }

//  public void testReturns() throws NoSuchMethodException {
//    Predicate<Class<String>> returnTypePredicate = sameAs(String.class);
//    Predicate<Method> predicate = returns(returnTypePredicate);
//    assertTrue(predicate.matches(
//        Object.class.getMethod("toString")));
//  }
  
  static abstract class MyRunnable implements Runnable {}
  
  @Retention(RetentionPolicy.RUNTIME)
  @interface Foo {}

  @Foo
  static class Bar {}
}
