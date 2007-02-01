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
