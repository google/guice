// Copyright 2006 Google Inc. All Rights Reserved.

package com.google.inject;

import junit.framework.TestCase;

import java.util.List;
import java.util.Arrays;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class GenericInjectionTest extends TestCase {

  public void testGenericInjection() {
    List<String> names = Arrays.asList("foo", "bar", "bob");
    ContainerBuilder builder = new ContainerBuilder();
    builder.bind(new TypeToken<List<String>>() {}).to(names);
    Container container = builder.create(false);
    Foo foo = container.getCreator(Foo.class).get();
    assertEquals(names, foo.names);
  }

  static class Foo {
    @Inject List<String> names;
  }
}
