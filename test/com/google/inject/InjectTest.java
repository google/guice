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
public class InjectTest extends TestCase {

  public void testSurrogateInjection() throws ContainerCreationException {
    ContainerBuilder builder = new ContainerBuilder();
    Bar bar = new Bar(null);
    builder.bind(Bar.class).named("bar").to(bar);
    Container container = builder.create(false);
    Foo foo = container.getInstance(Foo.class);
    assertSame(bar, foo.field);
    assertSame(bar, foo.fromConstructor);
    assertSame(bar, foo.fromMethod);
    assertSame(bar, foo.fromParameter);
  }

  @Retention(RUNTIME)
  @Inject("bar")
  @interface InjectBar {}

  static class Foo {
    @InjectBar Bar field;

    Bar fromConstructor;
    @InjectBar
    public Foo(Bar fromConstructor) {
      this.fromConstructor = fromConstructor;
    }

    Bar fromMethod;
    @InjectBar
    void setBar(Bar bar) {
      fromMethod = bar;
    }

    Bar fromParameter;
    @Inject
    void setBar2(@InjectBar Bar bar) {
      fromParameter= bar;
    }
  }

  static class Bar {
    // Not injectable.
    Bar(String s) {}
  }
}
