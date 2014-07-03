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

import junit.framework.TestCase;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class EagerSingletonTest extends TestCase {

  @Override public void setUp() {
    A.instanceCount = 0;
    B.instanceCount = 0;
    C.instanceCount = 0;
  }

  public void testJustInTimeEagerSingletons() {
    Guice.createInjector(Stage.PRODUCTION, new AbstractModule() {
      protected void configure() {
        // create a just-in-time binding for A
        getProvider(A.class);

        // create a just-in-time binding for C
        requestInjection(new Object() {
          @Inject void inject(Injector injector) {
            injector.getInstance(C.class);
          }
        });
      }
    });

    assertEquals(1, A.instanceCount);
    assertEquals("Singletons discovered when creating singletons should not be built eagerly",
        0, B.instanceCount);
    assertEquals(1, C.instanceCount);
  }

  public void testJustInTimeSingletonsAreNotEager() {
    Injector injector = Guice.createInjector(Stage.PRODUCTION);
    injector.getProvider(B.class);
    assertEquals(0, B.instanceCount);
  }

  public void testChildEagerSingletons() {
    Injector parent = Guice.createInjector(Stage.PRODUCTION);
    parent.createChildInjector(new AbstractModule() {
      @Override protected void configure() {
        bind(D.class).to(C.class);
      }
    });

    assertEquals(1, C.instanceCount);
  }

  @Singleton
  static class A {
    static int instanceCount = 0;
    int instanceId = instanceCount++;

    @Inject A(Injector injector) {
      injector.getProvider(B.class);
    }
  }

  @Singleton
  static class B {
    static int instanceCount = 0;
    int instanceId = instanceCount++;
  }

  @Singleton
  static class C implements D {
    static int instanceCount = 0;
    int instanceId = instanceCount++;
  }

  private static interface D {}
}
