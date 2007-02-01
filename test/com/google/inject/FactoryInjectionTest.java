// Copyright 2006 Google Inc. All Rights Reserved.

package com.google.inject;

import junit.framework.TestCase;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class FactoryInjectionTest extends TestCase {

  public void testFactoryInjection() throws ContainerCreationException {
    ContainerBuilder builder = new ContainerBuilder();

    builder.bind(Bar.class);
    builder.bind(ContainerScoped.class).in(Scopes.CONTAINER);

    Container container = builder.create(false);

    Foo foo = container.getCreator(Foo.class).get();

    Bar bar = foo.barFactory.get();
    assertNotNull(bar);
    assertNotSame(bar, foo.barFactory.get());

    ContainerScoped containerScoped = foo.containerScopedFactory.get();
    assertNotNull(containerScoped);
    assertSame(containerScoped, foo.containerScopedFactory.get());
  }

  static class Foo {
    @Inject Factory<Bar> barFactory;
    @Inject Factory<ContainerScoped> containerScopedFactory;
  }

  static class Bar {}

  static class ContainerScoped {}
}
