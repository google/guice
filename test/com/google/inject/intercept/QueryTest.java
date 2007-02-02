// Copyright 2006 Google Inc. All Rights Reserved.

package com.google.inject.intercept;

import static com.google.inject.intercept.Queries.annotatedWith;
import static com.google.inject.intercept.Queries.any;
import static com.google.inject.intercept.Queries.equalTo;
import static com.google.inject.intercept.Queries.inPackage;
import static com.google.inject.intercept.Queries.not;
import static com.google.inject.intercept.Queries.sameAs;
import static com.google.inject.intercept.Queries.subclassesOf;

import junit.framework.TestCase;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class QueryTest extends TestCase {

  public void testAny() {
    assertTrue(any().matches(null));
  }

  public void testNot() {
    assertFalse(not(any()).matches(null));
  }

  public void testAnd() {
    assertTrue(any().and(any()).matches(null));
    assertFalse(any().and(not(any())).matches(null));
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

  public void testEqualTo() {
    assertTrue(equalTo(1000).matches(new Integer(1000)));
    assertFalse(equalTo(1).matches(new Integer(1000)));
  }

  public void testSameAs() {
    Object o = new Object();
    assertTrue(sameAs(o).matches(o));
    assertFalse(sameAs(o).matches(new Object()));
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
