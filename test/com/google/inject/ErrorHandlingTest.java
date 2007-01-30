// Copyright 2006 Google Inc. All Rights Reserved.

package com.google.inject;

import java.util.List;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class ErrorHandlingTest {

  static class Foo {
    @Inject
    public Foo(Runnable r) {}

    @Inject void setNames(List<String> names) {}
  }

  static class Bar {
    // Invalid constructor.
    Bar(String s) {}

    @Inject void setNumbers(List<Integer> numbers) {}
  }

  static class Tee {
    @Inject String s;
  }

  static class MyModule extends AbstractModule {
    protected void configure() {
      bind(Foo.class);
      bind(Bar.class);
      bind(Tee.class);
    }
  }

  public static void main(String[] args) {
    ContainerBuilder builder = new ContainerBuilder();
    builder.apply(new MyModule());
    builder.create(true);
  }
}
