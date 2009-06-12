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

import com.google.inject.name.Named;
import com.google.inject.name.Names;
import junit.framework.TestCase;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class ImplicitBindingTest extends TestCase {

  public void testCircularDependency() throws CreationException {
    Injector injector = Guice.createInjector();
    Foo foo = injector.getInstance(Foo.class);
    assertSame(foo, foo.bar.foo);
  }

  static class Foo {
    @Inject Bar bar;
  }

  static class Bar {
    final Foo foo;
    @Inject
    public Bar(Foo foo) {
      this.foo = foo;
    }
  }

  public void testDefaultImplementation() {
    Injector injector = Guice.createInjector();
    I i = injector.getInstance(I.class);
    i.go();
  }

  @ImplementedBy(IImpl.class)
  interface I {
    void go();
  }

  static class IImpl implements I {
    public void go() {}
  }

  static class AlternateImpl implements I {
    public void go() {}
  }

  public void testDefaultProvider() {
    Injector injector = Guice.createInjector();
    Provided provided = injector.getInstance(Provided.class);
    provided.go();
  }

  public void testBindingOverridesImplementedBy() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(I.class).to(AlternateImpl.class);
      }
    });
    assertEquals(AlternateImpl.class, injector.getInstance(I.class).getClass());
  }

  @ProvidedBy(ProvidedProvider.class)
  interface Provided {
    void go();
  }

  public void testNoImplicitBindingIsCreatedForAnnotatedKeys() {
    try {
      Guice.createInjector().getInstance(Key.get(I.class, Names.named("i")));
      fail();
    } catch (ConfigurationException expected) {
      Asserts.assertContains(expected.getMessage(),
          "1) No implementation for " + I.class.getName(),
          "annotated with @" + Named.class.getName() + "(value=i) was bound.",
          "while locating " + I.class.getName(),
          " annotated with @" + Named.class.getName() + "(value=i)");
    }
  }

  static class ProvidedProvider implements Provider<Provided> {
    public Provided get() {
      return new Provided() {
        public void go() {}
      };
    }
  }

  /**
   * When we're building the binding for A, we temporarily insert that binding to support circular
   * dependencies. And so we can successfully create a binding for B. But later, when the binding
   * for A ultimately fails, we need to clean up the dependent binding for B.
   */
  public void testCircularJitBindingsLeaveNoResidue() {
    Injector injector = Guice.createInjector();

    try {
      injector.getBinding(A.class);
      fail();
    } catch (ConfigurationException expected) {
    }

    try {
      injector.getBinding(B.class);
      fail();
    } catch (ConfigurationException expected) {
    }
  }

  static class A {
    @Inject B b;
    @Inject D d;
  }

  static class B {
    @Inject A a;
  }

  interface D {}
}
