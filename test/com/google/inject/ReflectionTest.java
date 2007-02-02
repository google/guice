// Copyright 2006 Google Inc. All Rights Reserved.

package com.google.inject;

import junit.framework.TestCase;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class ReflectionTest extends TestCase {

  public void testNormalBinding() throws ContainerCreationException {
    ContainerBuilder builder = new ContainerBuilder();
    Foo foo = new Foo();
    builder.bind(Foo.class).to(foo);
    Container container = builder.create(false);
    Binding<Foo> fooBinding = container.getBinding(Key.get(Foo.class));
    assertSame(foo, fooBinding.getFactory().get());
    assertNotNull(fooBinding.getSource());
    assertEquals(Key.get(Foo.class), fooBinding.getKey());
    assertFalse(fooBinding.isConstant());
  }

  public void testConstantBinding() throws ContainerCreationException {
    ContainerBuilder builder = new ContainerBuilder();
    builder.bind("i").to(5);
    Container container = builder.create(false);
    Binding<?> i = container.getBinding(Key.get(int.class, "i"));
    assertEquals(5, i.getFactory().get());
    assertNotNull(i.getSource());
    assertEquals(Key.get(int.class, "i"), i.getKey());
    assertTrue(i.isConstant());
  }

  public void testLinkedBinding() throws ContainerCreationException {
    ContainerBuilder builder = new ContainerBuilder();
    Bar bar = new Bar();
    builder.bind(Bar.class).to(bar);
    builder.link(Key.get(Foo.class)).to(Key.get(Bar.class));
    Container container = builder.create(false);
    Binding<Foo> fooBinding = container.getBinding(Key.get(Foo.class));
    assertSame(bar, fooBinding.getFactory().get());
    assertNotNull(fooBinding.getSource());
    assertEquals(Key.get(Foo.class), fooBinding.getKey());
    assertFalse(fooBinding.isConstant());
  }

  static class Foo {}

  static class Bar extends Foo {}
}
