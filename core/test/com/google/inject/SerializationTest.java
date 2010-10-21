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

import static com.google.inject.Asserts.assertSimilarWhenReserialized;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class SerializationTest extends TestCase {

  public void testAbstractModuleIsSerializable() throws IOException {
    Asserts.reserialize(new MyAbstractModule());
  }
  static class MyAbstractModule extends AbstractModule implements Serializable {
    protected void configure() {}
  }

  public void testCreationExceptionIsSerializable() throws IOException {
    assertSimilarWhenReserialized(createCreationException());
  }

  private CreationException createCreationException() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(List.class);
        }
      });
      throw new AssertionFailedError();
    } catch (CreationException e) {
      return e;
    }
  }

  static class A {
    @Inject B b;
  }

  static class B {}
}
