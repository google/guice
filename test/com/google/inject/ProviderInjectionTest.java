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
import static com.google.inject.name.Names.named;
import junit.framework.TestCase;

import java.util.Arrays;
import java.util.List;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class ProviderInjectionTest extends TestCase {

  public void testProviderInjection() throws CreationException {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(Bar.class);
        bind(SampleSingleton.class).in(Scopes.SINGLETON);
      }
    });

    Foo foo = injector.getInstance(Foo.class);

    Bar bar = foo.barProvider.get();
    assertNotNull(bar);
    assertNotSame(bar, foo.barProvider.get());

    SampleSingleton singleton = foo.singletonProvider.get();
    assertNotNull(singleton);
    assertSame(singleton, foo.singletonProvider.get());
  }

  /** Test for bug 155. */
  public void testProvidersAreInjectedWhenBound() {
    Module m = new AbstractModule() {
      @Override
      protected void configure() {
        bind(Bar.class).toProvider(new Provider<Bar>() {
          @SuppressWarnings("unused")
          @Inject void cantBeCalled(Baz baz) {
            fail("Can't have called this method since Baz is not bound.");
          }
          public Bar get() {
            return new Bar() {};
          }
        });
      }
    };

    try {
      Guice.createInjector(m);
      fail("Should have thrown a CreationException");
    }
    catch (CreationException expected) {
    }
  }

  /**
   * When custom providers are used at injector creation time, they should be
   * injected before use. In this testcase, we verify that a provider for
   * List.class is injected before it is used.
   */
  public void testProvidersAreInjectedBeforeTheyAreUsed() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      public void configure() {
        // should bind String to "[true]"
        bind(String.class).toProvider(new Provider<String>() {
          private String value;
          @Inject void initialize(List list) {
            value = list.toString();
          }
          public String get() {
            return value;
          }
        });

        // should bind List to [true]
        bind(List.class).toProvider(new Provider<List>() {
          @Inject Boolean injectedYet = Boolean.FALSE;
          public List get() {
            return Arrays.asList(injectedYet);
          }
        });

        // should bind Boolean to true
        bind(Boolean.class).toInstance(Boolean.TRUE);
      }
    });

    assertEquals("Providers not injected before use",
        "[true]",
        injector.getInstance(String.class));
  }

  /**
   * This test ensures that regardless of binding order, instances are injected
   * before they are used. It injects mutable Count objects and records their
   * value at the time that they're injected.
   */
  public void testCreationTimeInjectionOrdering() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        // instance injection
        bind(Count.class).annotatedWith(named("a")).toInstance(new Count(0) {
          @Inject void initialize(@Named("b") Count bCount) {
            value = bCount.value + 1;
          }
        });

        // provider injection
        bind(Count.class).annotatedWith(named("b")).toProvider(new Provider<Count>() {
          Count count;
          @Inject void initialize(@Named("c") Count cCount) {
            count = new Count(cCount.value + 2);
          }
          public Count get() {
            return count;
          }
        });

        // field and method injection, fields first
        bind(Count.class).annotatedWith(named("c")).toInstance(new Count(0) {
          @Inject @Named("d") Count dCount;
          @Inject void initialize(@Named("e") Count eCount) {
            value = dCount.value + eCount.value + 4;
          }
        });

        // static injection
        requestStaticInjection(StaticallyInjectable.class);

        bind(Count.class).annotatedWith(named("d")).toInstance(new Count(8));
        bind(Count.class).annotatedWith(named("e")).toInstance(new Count(16));
      }
    });

    assertEquals(28, injector.getInstance(Key.get(Count.class, named("c"))).value);
    assertEquals(30, injector.getInstance(Key.get(Count.class, named("b"))).value);
    assertEquals(31, injector.getInstance(Key.get(Count.class, named("a"))).value);
    assertEquals(28, StaticallyInjectable.cCountAtInjectionTime);
  }

  static class Count {
    int value;
    Count(int value) {
      this.value = value;
    }
  }

  static class StaticallyInjectable {
    static int cCountAtInjectionTime;
    @Inject static void initialize(@Named("c") Count cCount) {
      cCountAtInjectionTime = cCount.value;
    }
  }

  static class Foo {
    @Inject Provider<Bar> barProvider;
    @Inject Provider<SampleSingleton> singletonProvider;
  }

  static class Bar {}

  static class SampleSingleton {}

  interface Baz { }

}
