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

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class ImplicitBindingTest extends TestCase {

  public void testCircularDependency() throws CreationException {
    Injector injector = Guice.createEmptyInjector();
    Foo foo = injector.getInstance(Foo.class);
    assertSame(foo, foo.bar.foo);
  }

  static class Foo {
    @Inject Bar bar;
  }

  static class Bar {
    final Foo foo;
    @Inject
    public Bar(Foo foo) {
      this.foo = foo;
    }
  }

  public void testDefaultImplementation() {
    Injector injector = Guice.createEmptyInjector();
    I i = injector.getInstance(I.class);
    i.go();
  }

  @ImplementedBy(IImpl.class)
  interface I {
    void go();
  }

  static class IImpl implements I {
    public void go() {}
  }

  public void testDefaultProvider() {
    Injector injector = Guice.createEmptyInjector();
    Provided provided = injector.getInstance(Provided.class);
    provided.go();
  }

  @ProvidedBy(ProvidedProvider.class)
  interface Provided {
    void go();
  }

  static class ProvidedProvider implements Provider<Provided> {
    public Provided get() {
      return new Provided() {
        public void go() {}
      };
    }
  }

}
