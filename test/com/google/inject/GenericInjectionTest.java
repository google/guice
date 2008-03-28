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

import java.util.Arrays;
import java.util.List;
import junit.framework.TestCase;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class GenericInjectionTest extends TestCase {

  public void testGenericInjection() throws CreationException {
    final List<String> names = Arrays.asList("foo", "bar", "bob");

    Injector injector = Guice.createInjector((Module) new AbstractModule() {
      protected void configure() {
        bind(new TypeLiteral<List<String>>() {}).toInstance(names);
      }
    });

    Foo foo = injector.getInstance(Foo.class);
    assertEquals(names, foo.names);
  }

  static class Foo {
    @Inject List<String> names;
  }

  /**
   * Although we may not have intended to support this behaviour, this test
   * passes under Guice 1.0. The workaround is to add an explicit binding for
   * the parameterized type. See {@link #testExplicitBindingOfGenericType()}.
   */
  public void testImplicitBindingOfGenericType() {
    Parameterized<String> parameterized
        = Guice.createInjector().getInstance(Key.get(new TypeLiteral<Parameterized<String>>() {}));
    assertNotNull(parameterized);
  }

  public void testExplicitBindingOfGenericType() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(Key.get(new TypeLiteral<Parameterized<String>>() {}))
            .to((Class) Parameterized.class);
      }
    });

    Parameterized<String> parameterized
        = injector.getInstance(Key.get(new TypeLiteral<Parameterized<String>>() { }));
    assertNotNull(parameterized);
  }

  static class Parameterized<T> {
    @Inject
    Parameterized() { }
  }
}
