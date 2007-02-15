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
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class ReflectionTest extends TestCase {

  @Retention(RUNTIME)
  @Binder @interface I {}

  public void testNormalBinding() throws ContainerCreationException {
    ContainerBuilder builder = new ContainerBuilder();
    Foo foo = new Foo();
    builder.bind(Foo.class).to(foo);
    Container container = builder.create();
    Binding<Foo> fooBinding = container.getBinding(Key.get(Foo.class));
    assertSame(foo, fooBinding.getFactory().get());
    assertNotNull(fooBinding.getSource());
    assertEquals(Key.get(Foo.class), fooBinding.getKey());
    assertFalse(fooBinding.isConstant());
  }

  public void testConstantBinding() throws ContainerCreationException {
    ContainerBuilder builder = new ContainerBuilder();
    builder.bindConstant(I.class).to(5);
    Container container = builder.create();
    Binding<?> i = container.getBinding(Key.get(int.class, I.class));
    assertEquals(5, i.getFactory().get());
    assertNotNull(i.getSource());
    assertEquals(Key.get(int.class, I.class), i.getKey());
    assertTrue(i.isConstant());
  }

  public void testLinkedBinding() throws ContainerCreationException {
    ContainerBuilder builder = new ContainerBuilder();
    Bar bar = new Bar();
    builder.bind(Bar.class).to(bar);
    builder.link(Key.get(Foo.class)).to(Key.get(Bar.class));
    Container container = builder.create();
    Binding<Foo> fooBinding = container.getBinding(Key.get(Foo.class));
    assertSame(bar, fooBinding.getFactory().get());
    assertNotNull(fooBinding.getSource());
    assertEquals(Key.get(Foo.class), fooBinding.getKey());
    assertFalse(fooBinding.isConstant());
  }

  static class Foo {}

  static class Bar extends Foo {}
}
