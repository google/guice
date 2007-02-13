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

package com.google.inject.util;

import junit.framework.TestCase;

import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class SurrogateAnnotationsTest extends TestCase {

  public void testDirect() throws DuplicateAnnotationException {
    assertEquals(10,
        SurrogateAnnotations.findAnnotation(Foo.class, Direct.class).value());
  }

  @Foo(10)
  static class Direct {}

  public void testIndirect() throws DuplicateAnnotationException {
    assertEquals(5,
        SurrogateAnnotations.findAnnotation(Foo.class, Indirect.class).value());
  }

  @Surrogate
  static class Indirect {}

  public void testException() {
    try {
      SurrogateAnnotations.findAnnotation(Foo.class, Broken.class).value();
      fail();
    } catch (DuplicateAnnotationException e) { /* expected */ }
  }

  @Foo(10)
  @Surrogate
  static class Broken {}

  @Target({ ElementType.TYPE, ElementType.ANNOTATION_TYPE })
  @Retention(RUNTIME)
  @interface Foo {
    int value();
  }

  @Retention(RUNTIME)
  @Foo(5)
  @interface Surrogate {}
}
