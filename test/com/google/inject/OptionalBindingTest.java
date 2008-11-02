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

import static com.google.inject.Asserts.assertContains;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import junit.framework.TestCase;

/**
 * This test verifies the ways things are injected (ie. getInstance(),
 * injectMembers(), bind to instance, and bind to provider instance) for all
 * states of optional bindings (fields, methods, multiple-argument methods,
 * provider fields, provider methods, constructors).
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class OptionalBindingTest extends TestCase {

  private static final A injectA = new A() {};
  private static final B injectB = new B() {};
  private static final C injectC = new C() {};
  private static final D injectD = new D() {};
  private static final E injectE = new E() {};
  private static final F injectF = new F() {};
  private static final G injectG = new G() {};

  private Module everythingModule = new AbstractModule() {
    protected void configure() {
      bind(A.class).toInstance(injectA);
      bind(B.class).toInstance(injectB);
      bind(C.class).toInstance(injectC);
      bind(D.class).toInstance(injectD);
      bind(E.class).annotatedWith(Names.named("e")).toInstance(injectE);
      bind(F.class).toInstance(injectF);
      bind(G.class).toInstance(injectG);
    }
  };

  private Module partialModule = new AbstractModule() {
    protected void configure() {
      bind(C.class).toInstance(new C() {});
    }
  };

  private Module toInstanceModule = new AbstractModule() {
    protected void configure() {
      bind(HasOptionalInjections.class)
          .toInstance(new HasOptionalInjections());
    }
  };

  private Module toProviderInstanceModule = new AbstractModule() {
    protected void configure() {
      bind(HasOptionalInjections.class)
          .toProvider(new HasOptionalInjectionsProvider());
    }
  };

  private Module toProviderModule = new AbstractModule() {
    protected void configure() {
      bind(HasOptionalInjections.class)
          .toProvider(HasOptionalInjectionsProvider.class);
    }
  };

  public void testEverythingInjectorGetInstance() {
    Guice.createInjector(everythingModule)
        .getInstance(HasOptionalInjections.class)
        .assertEverythingInjected();
  }

  public void testPartialInjectorGetInstance() {
    Guice.createInjector(partialModule)
        .getInstance(HasOptionalInjections.class)
        .assertNothingInjected();
  }

  public void testNothingInjectorGetInstance() {
    Guice.createInjector()
        .getInstance(HasOptionalInjections.class)
        .assertNothingInjected();
  }

  public void testEverythingInjectorInjectMembers() {
    HasOptionalInjections instance = new HasOptionalInjections();
    Guice.createInjector(everythingModule).injectMembers(instance);
    instance.assertEverythingInjected();
  }

  public void testPartialInjectorInjectMembers() {
    HasOptionalInjections instance = new HasOptionalInjections();
    Guice.createInjector(partialModule).injectMembers(instance);
    instance.assertNothingInjected();
  }

  public void testNothingInjectorInjectMembers() {
    HasOptionalInjections instance = new HasOptionalInjections();
    Guice.createInjector().injectMembers(instance);
    instance.assertNothingInjected();
  }

  public void testEverythingInjectorToInstance() {
    Guice.createInjector(everythingModule, toInstanceModule)
        .getInstance(HasOptionalInjections.class)
        .assertEverythingInjected();
  }

  public void testPartialInjectorToInstance() {
    Guice.createInjector(partialModule, toInstanceModule)
        .getInstance(HasOptionalInjections.class)
        .assertNothingInjected();
  }

  public void testNothingInjectorToInstance() {
    Guice.createInjector(toInstanceModule)
        .getInstance(HasOptionalInjections.class)
        .assertNothingInjected();
  }
  
  public void testEverythingInjectorToProviderInstance() {
    Guice.createInjector(everythingModule, toProviderInstanceModule)
        .getInstance(HasOptionalInjections.class)
        .assertEverythingInjected();
  }

  public void testPartialInjectorToProviderInstance() {
    Guice.createInjector(partialModule, toProviderInstanceModule)
        .getInstance(HasOptionalInjections.class)
        .assertNothingInjected();
  }

  public void testNothingInjectorToProviderInstance() {
    Guice.createInjector(toProviderInstanceModule)
        .getInstance(HasOptionalInjections.class)
        .assertNothingInjected();
  }

  public void testEverythingInjectorToProvider() {
    Guice.createInjector(everythingModule, toProviderModule)
        .getInstance(HasOptionalInjections.class)
        .assertEverythingInjected();
  }

  public void testPartialInjectorToProvider() {
    Guice.createInjector(partialModule, toProviderModule)
        .getInstance(HasOptionalInjections.class)
        .assertNothingInjected();
  }

  public void testNothingInjectorToProvider() {
    Guice.createInjector(toProviderModule)
        .getInstance(HasOptionalInjections.class)
        .assertNothingInjected();
  }

  static class HasOptionalInjections {
    A originalA = new A() {};
    @Inject(optional=true) A a = originalA; // field injection
    B b; // method injection with one argument
    C c; // method injection with two arguments
    D d; // method injection with two arguments
    E e; // annotated injection
    @Inject(optional=true) Provider<F> fProvider; // provider
    Provider<G> gProvider; // method injection of provider
    boolean invoked0, invoked1, invoked2, invokedAnnotated, invokeProvider;

    @Inject(optional=true) void methodInjectZeroArguments() {
      invoked0 = true;
    }

    @Inject(optional=true) void methodInjectOneArgument(B b) {
      this.b = b;
      invoked1 = true;
    }

    @Inject(optional=true) void methodInjectTwoArguments(C c, D d) {
      this.c = c;
      this.d = d;
      invoked2 = true;
    }

    @Inject(optional=true) void methodInjectAnnotated(@Named("e") E e) {
      this.e = e;
      invokedAnnotated = true;
    }

    @Inject(optional=true) void methodInjectProvider(Provider<G> gProvider) {
      this.gProvider = gProvider;
      invokeProvider = true;
    }

    void assertNothingInjected() {
      assertSame(originalA, a);
      assertNull(b);
      assertNull(c);
      assertNull(d);
      assertNull(e);
      assertNull(fProvider);
      assertNull(gProvider);
      assertTrue(invoked0);
      assertFalse(invoked1);
      assertFalse(invoked2);
      assertFalse(invokedAnnotated);
    }

    public void assertEverythingInjected() {
      assertNotSame(injectA, originalA);
      assertSame(injectA, a);
      assertSame(injectB, b);
      assertSame(injectC, c);
      assertSame(injectD, d);
      assertSame(injectE, e);
      assertSame(injectF, fProvider.get());
      assertSame(injectG, gProvider.get());
      assertTrue(invoked0);
      assertTrue(invoked1);
      assertTrue(invoked2);
      assertTrue(invokedAnnotated);
    }
  }

  static class HasOptionalInjectionsProvider
      extends HasOptionalInjections implements Provider<HasOptionalInjections> {
    public HasOptionalInjections get() {
      return this;
    }
  }

  public void testOptionalConstructorBlowsUp() {
    try {
      Guice.createInjector().getInstance(HasOptionalConstructor.class);
      fail();
    } catch (ConfigurationException expected) {
      assertContains(expected.getMessage(), "OptionalBindingTest$HasOptionalConstructor.<init>() "
          + "is annotated @Inject(optional=true), but constructors cannot be optional.");
    }
  }

  static class HasOptionalConstructor {
    @Inject(optional=true)
    HasOptionalConstructor() {}
  }

  @Inject(optional=true) static A staticInjectA;

  public void testStaticInjection() {
    staticInjectA = injectA;
    Guice.createInjector(new AbstractModule() {
      protected void configure() {
        requestStaticInjection(OptionalBindingTest.class);
      }
    });
    assertSame(staticInjectA, injectA);
  }

  /**
   * Test for bug 107, where we weren't doing optional injection properly for
   * indirect injections.
   */
  public void testIndirectOptionalInjection() {
    Indirect indirect = Guice.createInjector().getInstance(Indirect.class);
    assertNotNull(indirect.hasOptionalInjections);
    indirect.hasOptionalInjections.assertNothingInjected();
  }

  static class Indirect {
    @Inject HasOptionalInjections hasOptionalInjections;
  }

  interface A {}
  interface B {}
  interface C {}
  interface D {}
  interface E {}
  interface F {}
  interface G {}
}
