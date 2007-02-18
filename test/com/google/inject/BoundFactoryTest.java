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
public class BoundFactoryTest extends TestCase {

  public void testFooFactory() throws CreationException {
    ContainerBuilder cb = new ContainerBuilder();
    cb.bind(Foo.class).toFactory(FooFactory.class);
    Container c = cb.create();

    Foo a = c.getInstance(Foo.class);
    Foo b = c.getInstance(Foo.class);

    assertEquals(0, a.i);
    assertEquals(0, b.i);
    assertNotNull(a.bar);
    assertNotNull(b.bar);
    assertNotSame(a.bar, b.bar);
  }

  public void testContainerScopedFooFactory()
      throws CreationException {
    ContainerBuilder cb = new ContainerBuilder();
    cb.bind(Foo.class).toFactory(ContainerScopedFooFactory.class);
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

  static class FooFactory implements Factory<Foo> {

    final Bar bar;
    int count = 0;

    @Inject
    public FooFactory(Bar bar) {
      this.bar = bar;
    }

    public Foo get(Context context) {
      assertNull(context.getMember());
      assertEquals(-1, context.getParameterIndex());
      assertEquals(Foo.class, context.getKey().getRawType());
      return new Foo(this.bar, count++);
    }
  }

  @ContainerScoped
  static class ContainerScopedFooFactory implements Factory<Foo> {

    final Bar bar;
    int count = 0;

    @Inject
    public ContainerScopedFooFactory(Bar bar) {
      this.bar = bar;
    }

    public Foo get(Context context) {
      assertNull(context.getMember());
      assertEquals(-1, context.getParameterIndex());
      assertEquals(Foo.class, context.getKey().getRawType());
      return new Foo(this.bar, count++);
    }
  }
}
