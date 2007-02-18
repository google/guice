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
import com.google.inject.name.Named;
import com.google.inject.name.Names;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class ErrorHandlingTest {

  public static void main(String[] args) throws CreationException {
    ContainerBuilder builder = new ContainerBuilder();
    builder.install(new MyModule());
    builder.create();
  }

  @Inject @Named("missing")
  static List<String> missing;

  static class Foo {
    @Inject
    public Foo(Runnable r) {}

    @Inject void setNames(List<String> names) {}
  }

  static class Bar {
    // Invalid constructor.
    Bar(String s) {}

    @Inject void setNumbers(@Named("numbers") List<Integer> numbers) {}

    @Inject void bar(@Named("foo") String s) {}
  }

  static class Tee {
    @Inject String s;

    @Inject void tee(String s, int i) {}

    @Inject Invalid invalid;
  }

  static class Invalid {
    Invalid(String s) {}
  }

  static class MyModule extends AbstractModule {
    protected void configure() {
      bind(Runnable.class);
      bind(Foo.class);
      bind(Bar.class);
      bind(Tee.class);
      bind(new TypeLiteral<List<String>>() {});
      bind(String.class).annotatedWith(Names.named("foo")).in(
          Named.class);
      link(Key.get(Runnable.class)).to(Key.get(Runnable.class));
      requestStaticInjection(ErrorHandlingTest.class);
    }
  }
}
