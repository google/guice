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


package com.google.inject.name;

import static com.google.inject.Asserts.assertEqualWhenReserialized;
import static com.google.inject.Asserts.assertEqualsBothWays;
import junit.framework.TestCase;

import java.io.IOException;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class NamesTest extends TestCase {

  @Named("foo") private String foo;
  private Named namedFoo;
  
  protected void setUp() throws Exception {
    super.setUp();
    namedFoo = getClass().getDeclaredField("foo").getAnnotation(Named.class);
  }

  public void testConsistentEqualsAndHashcode() {
    Named actual = Names.named("foo");
    assertEqualsBothWays(namedFoo, actual);
    assertEquals(namedFoo.toString(), actual.toString());
  }

  public void testNamedIsSerializable() throws IOException {
    assertEqualWhenReserialized(Names.named("foo"));
  }
}
