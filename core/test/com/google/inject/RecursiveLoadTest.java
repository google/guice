/*
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

package com.google.inject;

import com.google.inject.spi.Message;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import junit.framework.TestCase;
import org.junit.Assert;

public class RecursiveLoadTest extends TestCase {

  public void testRecursiveLoad() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {}
            });
    assertBothFailures(injector, A.class);
    assertBothFailures(injector, B.class);
    assertNoImplementationFailure(injector, C.class);
    assertRecursiveFailure(injector, D.class);
    assertNoImplementationFailure(injector, E.class);
  }

  private static void assertFailure(
      Injector injector, Class<?> clazz, Consumer<List<Message>> checks) {
    try {
      injector.getBinding(clazz);
      fail("Shouldn't have been able to get binding of: " + clazz);
    } catch (ConfigurationException expected) {
      List<Message> errorMessages = new ArrayList<>(expected.getErrorMessages());
      checks.accept(errorMessages);
    }
  }

  private static void assertBothFailures(Injector injector, Class<?> clazz) {
    assertFailure(
        injector,
        clazz,
        errorMessages -> {
          Assert.assertEquals(2, errorMessages.size());

          Message msg1 = errorMessages.get(0);
          Asserts.assertContains(
              msg1.getMessage(),
              "com.google.inject.RecursiveLoadTest$B.<init>() was already loading.");

          Message msg2 = errorMessages.get(1);
          Asserts.assertContains(
              msg2.getMessage(),
              "No implementation for com.google.inject.RecursiveLoadTest$Unresolved was bound.");
        });
  }

  private static void assertRecursiveFailure(Injector injector, Class<?> clazz) {
    assertFailure(
        injector,
        clazz,
        errorMessages -> {
          Assert.assertEquals(1, errorMessages.size());

          Message msg = errorMessages.get(0);
          Asserts.assertContains(
              msg.getMessage(),
              "com.google.inject.RecursiveLoadTest$B.<init>() was already loading.");
        });
  }

  private static void assertNoImplementationFailure(Injector injector, Class<?> clazz) {
    assertFailure(
        injector,
        clazz,
        errorMessages -> {
          Assert.assertEquals(1, errorMessages.size());

          Message msg = errorMessages.get(0);
          Asserts.assertContains(
              msg.getMessage(),
              "No implementation for com.google.inject.RecursiveLoadTest$Unresolved was bound.");
        });
  }

  static class A {
    @Inject B b;
  }

  static class B {
    @Inject C c;
    @Inject D d;
  }

  static class C {
    @Inject E e;
    @Inject B b;
  }

  static class D {
    @Inject B b;
  }

  static class E {
    @Inject Unresolved unresolved;
  }

  interface Unresolved {}
}
