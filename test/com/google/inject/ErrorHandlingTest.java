// Copyright 2006 Google Inc. All Rights Reserved.

package com.google.inject;

import java.util.List;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class ErrorHandlingTest {

  @Inject("missing")
  static List<String> missing;

  static class Foo {
    @Inject
    public Foo(Runnable r) {}

    @Inject void setNames(List<String> names) {}
  }

  static class Bar {
    // Invalid constructor.
    Bar(String s) {}

    @Inject("numbers") void setNumbers(List<Integer> numbers) {}

    @Inject("foo") void bar(@Inject String s) {}
  }

  static class Tee {
    @Inject String s;

    @Inject("foo") void tee(String s, int i) {}
  }

  static class MyModule extends AbstractModule {
    protected void configure() {
      bind(Runnable.class);
      bind(Foo.class);
      bind(Bar.class);
      bind(Tee.class);
      bind(String.class).named("foo").in("foo");
      link(Key.get(Runnable.class)).to(Key.get(Runnable.class));
      requestStaticInjection(ErrorHandlingTest.class);
    }
  }

  public static void main(String[] args) throws ContainerCreationException {
    ContainerBuilder builder = new ContainerBuilder();
    builder.apply(new MyModule());
    builder.create(true);
  }
}
