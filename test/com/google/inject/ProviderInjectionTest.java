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
import java.util.List;
import java.util.Arrays;
import static java.util.Collections.emptySet;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class ProviderInjectionTest extends TestCase {

  public void testProviderInjection() throws CreationException {
    BinderImpl builder = new BinderImpl();

    builder.bind(Bar.class);
    builder.bind(SampleSingleton.class).in(Scopes.SINGLETON);

    Injector injector = builder.createInjector();

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
    Injector injector = Guice.createInjector(new Module() {
      public void configure(Binder binder) {
        // should bind String to "[true]"
        binder.bind(String.class).toProvider(new Provider<String>() {
          private String value;
          @Inject void initialize(List list) {
            value = list.toString();
          }
          public String get() {
            return value;
          }
        });

        // should bind List to [true]
        binder.bind(List.class).toProvider(new Provider<List>() {
          @Inject Boolean injectedYet = Boolean.FALSE;
          public List get() {
            return Arrays.asList(injectedYet);
          }
        });

        // should bind Boolean to true
        binder.bind(Boolean.class).toInstance(Boolean.TRUE);
      }
    });

    assertEquals("Providers not injected before use",
        "[true]",
        injector.getInstance(String.class));
  }

  static class Foo {
    @Inject Provider<Bar> barProvider;
    @Inject Provider<SampleSingleton> singletonProvider;
  }

  static class Bar {}

  static class SampleSingleton {}

  interface Baz { }

}
