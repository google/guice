/*
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

import static com.google.inject.Asserts.assertContains;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import org.junit.jupiter.api.Test;

/** @author jessewilson@google.com (Jesse Wilson) */
public class SerializationTest {

  @Test
  public void testAbstractModuleIsSerializable() throws IOException {
    Asserts.reserialize(new MyAbstractModule());
  }

  static class MyAbstractModule extends AbstractModule implements Serializable {
  }

  @Test
  public void testCreationExceptionIsSerializable() throws IOException {
    CreationException exception = createCreationException();
    CreationException reserialized = Asserts.reserialize(exception);
    assertContains(
        reserialized.getMessage(),
        "1) [Guice/MissingImplementation]: No implementation for List was bound.",
        "at SerializationTest$1.configure");
  }

  private CreationException createCreationException() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              bind(List.class);
            }
          });
      return fail();
    } catch (CreationException e) {
      return e;
    }
  }

  static class A {
    @Inject B b;
  }

  static class B {}
}
