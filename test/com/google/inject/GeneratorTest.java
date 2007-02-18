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
public class GeneratorTest extends TestCase {

  public void testFooGenerator() throws ContainerCreationException {
    ContainerBuilder cb = new ContainerBuilder();
    cb.bind(Foo.class).toGenerator(FooGenerator.class);
    Container c = cb.create();

    Foo a = c.getInstance(Foo.class);
    Foo b = c.getInstance(Foo.class);

    assertEquals(0, a.i);
    assertEquals(0, b.i);
    assertNotNull(a.bar);
    assertNotNull(b.bar);
    assertNotSame(a.bar, b.bar);
  }

  public void testContainerScopedFooGenerator()
      throws ContainerCreationException {
    ContainerBuilder cb = new ContainerBuilder();
    cb.bind(Foo.class).toGenerator(ContainerScopedFooGenerator.class);
    Container c = cb.create();

    Foo a = c.getInstance(Foo.class);
    Foo b = c.getInstance(Foo.class);

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

  static class FooGenerator implements Generator<Foo> {

    final Bar bar;
    int count = 0;

    @Inject
    public FooGenerator(Bar bar) {
      this.bar = bar;
    }

    public Foo generate(Context context) {
      assertNull(context.getMember());
      assertEquals(-1, context.getParameterIndex());
      assertEquals(Foo.class, context.getKey().getRawType());
      return new Foo(this.bar, count++);
    }
  }

  @ContainerScoped
  static class ContainerScopedFooGenerator implements Generator<Foo> {

    final Bar bar;
    int count = 0;

    @Inject
    public ContainerScopedFooGenerator(Bar bar) {
      this.bar = bar;
    }

    public Foo generate(Context context) {
      assertNull(context.getMember());
      assertEquals(-1, context.getParameterIndex());
      assertEquals(Foo.class, context.getKey().getRawType());
      return new Foo(this.bar, count++);
    }
  }
}
