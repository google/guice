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

package com.google.inject;

import junit.framework.TestCase;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class CircularDependencyTest extends TestCase {

  @Override protected void setUp() throws Exception {
    super.setUp();
    Chicken.nextInstanceId = 0;
    Egg.nextInstanceId = 0;
  }

  public void testCircularlyDependentConstructors()
      throws CreationException {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(A.class).to(AImpl.class);
        bind(B.class).to(BImpl.class);
      }
    });

    A a = injector.getInstance(A.class);
    assertNotNull(a.getB().getA());
  }

  interface A {
    B getB();
  }

  @Singleton
  static class AImpl implements A {
    final B b;
    @Inject public AImpl(B b) {
      this.b = b;
    }
    public B getB() {
      return b;
    }
  }

  interface B {
    A getA();
  }

  static class BImpl implements B {
    final A a;
    @Inject public BImpl(A a) {
      this.a = a;
    }
    public A getA() {
      return a;
    }
  }

  public void testUnresolvableCircularDependency() {
    try {
      Guice.createInjector().getInstance(C.class);
      fail();
    } catch (ProvisionException expected) {
      Asserts.assertContains(expected.getMessage(),
          "Tried proxying " + C.class.getName() + " to support a circular dependency, ",
          "but it is not an interface.");
    }
  }

  static class C {
    @Inject C(D d) {}
  }

  static class D {
    @Inject D(C c) {}
  }

  /**
   * As reported by issue 349, we give a lousy trace when a class is circularly
   * dependent on itself in multiple ways.
   */
  public void testCircularlyDependentMultipleWays() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        binder.bind(A.class).to(E.class);
        binder.bind(B.class).to(E.class);
      }
    });
    injector.getInstance(A.class);
  }

  @Singleton
  static class E implements A, B {
    @Inject
    public E(A a, B b) {}

    public B getB() {
      return this;
    }

    public A getA() {
      return this;
    }
  }

  static class Chicken {
    static int nextInstanceId;
    final int instanceId = nextInstanceId++;
    @Inject Egg source;
  }

  static class Egg {
    static int nextInstanceId;
    final int instanceId = nextInstanceId++;
    @Inject Chicken source;
  }

  public void testCircularlyDependentSingletonsWithProviders() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(Chicken.class).in(Singleton.class);
      }

      @Provides @Singleton Egg provideEgg(Chicken chicken) {
        Egg egg = new Egg();
        egg.source = chicken;
        return egg;
      }
    });

    injector.getInstance(Egg.class);
    injector.getInstance(Chicken.class);

    assertEquals(1, Chicken.nextInstanceId);
    assertEquals(1, Egg.nextInstanceId);
  }
}
