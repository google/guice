/*
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/** @author crazybob@google.com (Bob Lee) */
public class BoundProviderTest {

  @Test
  public void testFooProvider() throws CreationException {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(Foo.class).toProvider(FooProvider.class);
              }
            });

    Foo a = injector.getInstance(Foo.class);
    Foo b = injector.getInstance(Foo.class);

    assertEquals(0, a.i);
    assertEquals(0, b.i);
    assertNotNull(a.bar);
    assertNotNull(b.bar);
    assertNotSame(a.bar, b.bar);
  }

  @Test
  public void testSingletonFooProvider() throws CreationException {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(Foo.class).toProvider(SingletonFooProvider.class);
              }
            });

    Foo a = injector.getInstance(Foo.class);
    Foo b = injector.getInstance(Foo.class);

    assertEquals(0, a.i);
    assertEquals(1, b.i);
    assertNotNull(a.bar);
    assertNotNull(b.bar);
    assertSame(a.bar, b.bar);
  }

  static class Bar {}

  static class Foo {
    final Bar bar;
    final int i;

    Foo(Bar bar, int i) {
      this.bar = bar;
      this.i = i;
    }
  }

  static class FooProvider implements Provider<Foo> {

    final Bar bar;
    int count = 0;

    @Inject
    public FooProvider(Bar bar) {
      this.bar = bar;
    }

    @Override
    public Foo get() {
      return new Foo(this.bar, count++);
    }
  }

  @Singleton
  static class SingletonFooProvider implements Provider<Foo> {

    final Bar bar;
    int count = 0;

    @Inject
    public SingletonFooProvider(Bar bar) {
      this.bar = bar;
    }

    @Override
    public Foo get() {
      return new Foo(this.bar, count++);
    }
  }
}
