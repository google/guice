// Copyright 2006 Google Inc. All Rights Reserved.

package com.google.inject;

import junit.framework.TestCase;

import java.util.List;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class KeyTest extends TestCase {

  public void foo(List<String> a, List<String> b) {}

  public void testEquality() throws Exception {
    Method m = getClass().getMethod("foo", List.class, List.class);
    Type[] types = m.getGenericParameterTypes();
    assertEquals(types[0], types[1]);
    System.err.println(types[0]);
  }
}
