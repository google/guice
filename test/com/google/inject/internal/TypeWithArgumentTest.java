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

import junit.framework.TestCase;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class TypeWithArgumentTest extends TestCase {

  private Map<String, Integer> mapStringInteger; // a generic type for comparison

  public void testEqualsAndHashcode() throws NoSuchFieldException {
    Type actual = new TypeWithArgument(Map.class, String.class, Integer.class);
    Type expected = getClass().getDeclaredField("mapStringInteger").getGenericType();
    assertTrue(actual.equals(expected));
    assertTrue(expected.equals(actual));
    assertEquals(expected.hashCode(), actual.hashCode());
    assertEquals(expected.toString(), actual.toString());
  }

  public void testDefensiveCopies() {
    Type[] arguments = new Type[] { String.class, Integer.class };
    ParameterizedType parameterizedType = new TypeWithArgument(Map.class, arguments);
    arguments[0] = null;
    assertEquals(String.class, parameterizedType.getActualTypeArguments()[0]);
    parameterizedType.getActualTypeArguments()[1] = null;
    assertEquals(Integer.class, parameterizedType.getActualTypeArguments()[1]);
  }
}
