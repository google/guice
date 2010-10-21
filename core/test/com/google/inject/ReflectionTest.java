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

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;

import junit.framework.TestCase;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class ReflectionTest extends TestCase {

  @Retention(RUNTIME)
  @BindingAnnotation @interface I {}

  public void testNormalBinding() throws CreationException {
    final Foo foo = new Foo();

    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(Foo.class).toInstance(foo);
      }
    });

    Binding<Foo> fooBinding = injector.getBinding(Key.get(Foo.class));
    assertSame(foo, fooBinding.getProvider().get());
    assertNotNull(fooBinding.getSource());
    assertEquals(Key.get(Foo.class), fooBinding.getKey());
  }

  public void testConstantBinding() throws CreationException {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindConstant().annotatedWith(I.class).to(5);
      }
    });

    Binding<?> i = injector.getBinding(Key.get(int.class, I.class));
    assertEquals(5, i.getProvider().get());
    assertNotNull(i.getSource());
    assertEquals(Key.get(int.class, I.class), i.getKey());
  }

  public void testLinkedBinding() throws CreationException {
    final Bar bar = new Bar();

    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(Bar.class).toInstance(bar);
        bind(Key.get(Foo.class)).to(Key.get(Bar.class));
      }
    });

    Binding<Foo> fooBinding = injector.getBinding(Key.get(Foo.class));
    assertSame(bar, fooBinding.getProvider().get());
    assertNotNull(fooBinding.getSource());
    assertEquals(Key.get(Foo.class), fooBinding.getKey());
  }

  static class Foo {}

  static class Bar extends Foo {}
}
