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

import java.lang.annotation.Annotation;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class UniqueAnnotationsTest extends TestCase {

  @UniqueAnnotations.Internal(31) public Void unused;
  
  public void testEqualsHashCodeToString() {
    Annotation actual = UniqueAnnotations.create(31);

    Annotation expected = getClass().getFields()[0].getAnnotations()[0];

    assertEquals(expected.toString(), actual.toString());
    assertEquals(expected.hashCode(), actual.hashCode());
    assertEquals(expected, actual);
  }
}
