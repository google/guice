/*
 * Copyright (C) 2007 Google Inc.
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
import com.google.inject.name.Names;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import junit.framework.TestCase;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class BindingTest extends TestCase {

  static class Dependent {
    @Inject A a;
    @Inject Dependent(A a, B b) {}
    @Inject void injectBob(Bob bob) {}
  }

  public void testExplicitCyclicDependency() {
    Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(A.class);
        bind(B.class);
      }
    }).getInstance(A.class);
  }

  static class A { @Inject B b; }
  static class B { @Inject A a; }

  static class Bob {}

  static class MyModule extends AbstractModule {

    protected void configure() {
      // Linked.
      bind(Object.class).to(Runnable.class).in(Scopes.SINGLETON);

      // Instance.
      bind(Runnable.class).toInstance(new Runnable() {
        public void run() {}
      });

      // Provider instance.
      bind(Foo.class).toProvider(new Provider<Foo>() {
        public Foo get() {
          return new Foo();
        }
      }).in(Scopes.SINGLETON);

      // Provider.
      bind(Foo.class)
          .annotatedWith(Names.named("provider"))
          .toProvider(FooProvider.class);

      // Class.
      bind(Bar.class).in(Scopes.SINGLETON);

      // Constant.
      bindConstant().annotatedWith(Names.named("name")).to("Bob");
    }
  }

  static class Foo {}

  public static class FooProvider implements Provider<Foo> {
    public Foo get() {
      throw new UnsupportedOperationException();
    }
  }

  public static class Bar {}

  public void testBindToUnboundLinkedBinding() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(Collection.class).to(List.class);
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(), "No implementation for java.util.List was bound.");
    }
  }

  /**
   * This test ensures that the asEagerSingleton() scoping applies to the key,
   * not to what the key is linked to.
   */
  public void testScopeIsAppliedToKeyNotTarget() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(Integer.class).toProvider(Counter.class).asEagerSingleton();
        bind(Number.class).toProvider(Counter.class).asEagerSingleton();
      }
    });

    assertNotSame(injector.getInstance(Integer.class), injector.getInstance(Number.class));
  }

  static class Counter implements Provider<Integer> {
    static AtomicInteger next = new AtomicInteger(1);
    public Integer get() {
      return next.getAndIncrement();
    }
  }

  public void testAnnotatedNoArgConstructor() {
    assertBindingSucceeds(PublicNoArgAnnotated.class);
    assertBindingSucceeds(ProtectedNoArgAnnotated.class);
    assertBindingSucceeds(PackagePrivateNoArgAnnotated.class);
    assertBindingSucceeds(PrivateNoArgAnnotated.class);
  }

  static class PublicNoArgAnnotated {
    @Inject public PublicNoArgAnnotated() { }
  }

  static class ProtectedNoArgAnnotated {
    @Inject protected ProtectedNoArgAnnotated() { }
  }

  static class PackagePrivateNoArgAnnotated {
    @Inject PackagePrivateNoArgAnnotated() { }
  }

  static class PrivateNoArgAnnotated {
    @Inject private PrivateNoArgAnnotated() { }
  }

  public void testUnannotatedNoArgConstructor() throws Exception{
    assertBindingSucceeds(PublicNoArg.class);
    assertBindingSucceeds(ProtectedNoArg.class);
    assertBindingSucceeds(PackagePrivateNoArg.class);
    assertBindingSucceeds(PrivateNoArgInPrivateClass.class);
    assertBindingFails(PrivateNoArg.class);
  }

  static class PublicNoArg {
    public PublicNoArg() { }
  }

  static class ProtectedNoArg {
    protected ProtectedNoArg() { }
  }

  static class PackagePrivateNoArg {
    PackagePrivateNoArg() { }
  }

  private static class PrivateNoArgInPrivateClass {
    PrivateNoArgInPrivateClass() { }
  }

  static class PrivateNoArg {
    private PrivateNoArg() { }
  }

  private void assertBindingSucceeds(final Class<?> clazz) {
    assertNotNull(Guice.createInjector().getInstance(clazz));
  }

  private void assertBindingFails(final Class<?> clazz) throws NoSuchMethodException {
    try {
      Guice.createInjector().getInstance(clazz);
      fail();
    } catch (ConfigurationException expected) {
      assertContains(expected.getMessage(),
          "Could not find a suitable constructor in " + PrivateNoArg.class.getName(),
          "at " + PrivateNoArg.class.getName() + ".class(BindingTest.java:");
    }
  }

  public void testTooManyConstructors() {
    try {
      Guice.createInjector().getInstance(TooManyConstructors.class);
      fail();
    } catch (ConfigurationException expected) {
      assertContains(expected.getMessage(),
          TooManyConstructors.class.getName() + " has more than one constructor annotated with " 
              + "@Inject. Classes must have either one (and only one) constructor",
          "at " + TooManyConstructors.class.getName() + ".class(BindingTest.java:");
    }
  }

  static class TooManyConstructors {
    @Inject TooManyConstructors(Injector i) {}
    @Inject TooManyConstructors() {}
  }
}
