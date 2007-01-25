// Copyright 2006 Google Inc. All Rights Reserved.

package com.google.inject;

import junit.framework.TestCase;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class FactoryInjectionTest extends TestCase {

  public void testFactoryInjection() {
    ContainerBuilder builder = new ContainerBuilder();

    builder.bind(Bar.class);
    builder.bind(Singleton.class).in(Scopes.SINGLETON);

    Container container = builder.create(false);

    Foo foo = container.newInstance(Foo.class);

    Bar bar = foo.barFactory.get();
    assertNotNull(bar);
    assertNotSame(bar, foo.barFactory.get());

    Singleton singleton = foo.singletonFactory.get();
    assertNotNull(singleton);
    assertSame(singleton, foo.singletonFactory.get());
  }

  static class Foo {
    @Inject Factory<Bar> barFactory;
    @Inject Factory<Singleton> singletonFactory;
  }

  static class Bar {}

  static class Singleton {}
}
