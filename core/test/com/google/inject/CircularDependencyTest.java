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
import static com.google.inject.Asserts.assertContains;

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
      assertContains(expected.getMessage(),
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
  
  public void testDisablingCircularProxies() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        binder().disableCircularProxies();
        binder.bind(A.class).to(E.class);
        binder.bind(B.class).to(E.class);
      }
    });
    
    try {
      injector.getInstance(A.class);
      fail("expected exception");
    } catch(ProvisionException expected) {
      assertContains(expected.getMessage(),
          "Tried proxying " + A.class.getName() + " to support a circular dependency, but circular proxies are disabled", 
          "Tried proxying " + B.class.getName() + " to support a circular dependency, but circular proxies are disabled");
    }
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

    try {
      injector.getInstance(Egg.class);
      fail();
    } catch (ProvisionException e) {
      assertContains(e.getMessage(),
          "Provider was reentrant while creating a singleton",
          " at " + CircularDependencyTest.class.getName(), "provideEgg(",
          " while locating " + Egg.class.getName());
    }
  }

  public void testCircularDependencyProxyDelegateNeverInitialized() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(F.class).to(RealF.class);
        bind(G.class).to(RealG.class);
      }
    });
    F f = injector.getInstance(F.class);
    assertEquals("F", f.g().f().toString());
    assertEquals("G", f.g().f().g().toString());

  }

  public interface F {
    G g();
  }

  @Singleton
  public static class RealF implements F {
    private final G g;
    @Inject RealF(G g) {
      this.g = g;
    }

    public G g() {
      return g;
    }

    @Override public String toString() {
      return "F";
    }
  }

  public interface G {
    F f();
  }

  @Singleton
  public static class RealG implements G {
    private final F f;
    @Inject RealG(F f) {
      this.f = f;
    }

    public F f() {
      return f;
    }

    @Override public String toString() {
      return "G";
    }
  }

}
