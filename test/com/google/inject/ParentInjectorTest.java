/*
Copyright (C) 2007 Google Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.google.inject;

import junit.framework.TestCase;

/**
 *
 */
public class ParentInjectorTest extends TestCase {

  Module baseModule = new AbstractModule() {
    protected void configure() {
      bind(Foo.class).to(FooImpl.class).in(Scopes.SINGLETON);
      bind(Bar.class).to(BarOne.class);
    }
  };

  Injector injector = Guice.createInjector(baseModule);
  Injector childInjector = Guice.createInjector(injector,
      new AbstractModule() {
        protected void configure() {
          bind(Bar.class).to(BarTwo.class);
          bind(Bus.class).to(BusImpl.class);
        }
      });

  /** Make sure that singletons are properly handled **/
  public void testExplicitSingleton() throws Exception {
    Foo fooParent = injector.getInstance(Foo.class);
    Foo fooChild = childInjector.getInstance(Foo.class);

    assertTrue(fooChild instanceof FooImpl);
    assertSame(fooParent, fooChild);
  }

  /**
   * Make sure that when there are non scoped bindings in the parent,
   * they are not used.
   */
  public void testNonSingletons() throws Exception {
    Bar barParent = injector.getInstance(Bar.class);
    Bar barChild = childInjector.getInstance(Bar.class);

    assertNotSame(barParent, barChild);
    assertTrue(barParent instanceof BarOne);
    assertTrue(barChild instanceof BarTwo);
  }

  public void testImplicitSingleton() throws Exception {
    Car carParent = injector.getInstance(Car.class);
    Car carChild = childInjector.getInstance(Car.class);

    assertNotNull(carParent);
    assertNotNull(carChild);
    assertSame(carParent, carChild);
  }

  public void testImplicitSingletonFromChild() throws Exception {
    Truck truck = childInjector.getInstance(Truck.class);
    assertNotNull(truck);
  }

  private interface Foo {}
  @Singleton
  private static class FooImpl implements Foo {}

  private interface Bar {}
  private static class BarOne implements Bar {}
  private static class BarTwo implements Bar {}

  @Singleton
  private static class Car {}

  private interface Bus {}
  private static class BusImpl implements Bus {}

  @Singleton
  private static class Truck {
    @Inject
    Truck(Bus bus) {}
  }

}
