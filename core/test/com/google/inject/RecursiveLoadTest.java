/*
 * Copyright (C) 2020 Google Inc.
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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.inject.Asserts.assertContains;
import static org.junit.Assert.fail;

@RunWith(JUnit4.class)
public final class RecursiveLoadTest {
  /**
   * This test uses failed optional bindings to trigger a recursive load crash.
   * https://github.com/google/guice/issues/785
   */
  @Test public void recursiveLoadWithOptionals() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override protected void configure() {
          bind(A.class);
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(
          expected.getMessage(),
          "Recursive load of " + B.class.getName() + ".<init>()");
    }
  }

  static class A {
    @Inject B b;
  }

  static class B {
    @Inject C c;
  }

  static class C {
    @Inject(optional = true) D d;
    @Inject E e;
  }

  static class D {
    @Inject B b;
    @Inject Unresolved unresolved;
  }

  static class E {
    @Inject B b;
  }

  @Test public void recursiveLoadWithoutOptionals() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Provides public V provideV(Z z) {
          return null;
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(
          expected.getMessage(),
          "1) Recursive load of " + Z.class.getName() + ".<init>()");
    }
  }

  static class V {
  }

  static class W {
    @Inject Y y;
    @Inject Z z;
  }

  static class X {
    @Inject Z z;
  }

  static class Y {
    @Inject Unresolved unresolved;
  }

  static class Z {
    @Inject W w;
    @Inject X x;
  }

  interface Unresolved {
  }
}
