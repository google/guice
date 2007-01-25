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

import static com.google.inject.Scopes.SINGLETON;

import junit.framework.TestCase;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class ContainerTest extends TestCase {

  public void testInjection() {
    Container container = createFooContainer();
    Foo foo = container.newInstance(Foo.class);

    assertEquals("test", foo.s);
    assertEquals("test", foo.bar.getTee().getS());
    assertSame(foo.bar, foo.copy);
    assertEquals(5, foo.i);
    assertEquals(5, foo.bar.getI());

    // Test circular dependency.
    assertSame(foo.bar, foo.bar.getTee().getBar());
  }

  private Container createFooContainer() {
    ContainerBuilder builder = new ContainerBuilder();
    builder.bind(Bar.class).to(BarImpl.class);
    builder.bind(Tee.class).to(TeeImpl.class);
    builder.bind("s").to("test");
    builder.bind("i").to(5);

    return builder.create(false);
  }

  public void testGetInstance() {
    Container container = createFooContainer();

    Bar bar = container.getFactory(Key.get(Bar.class)).get();
    assertEquals("test", bar.getTee().getS());
    assertEquals(5, bar.getI());
  }

  static class Foo {

    @Inject Bar bar;
    @Inject Bar copy;

    @Inject("s") String s;

    int i;

    @Inject("i")
    void setI(int i) {
      this.i = i;
    }
  }

  interface Bar {

    Tee getTee();
    int getI();
  }

  @Scoped(SINGLETON)
  static class BarImpl implements Bar {

    @Inject("i") int i;

    Tee tee;

    @Inject
    void initialize(Tee tee) {
      this.tee = tee;
    }

    public Tee getTee() {
      return tee;
    }

    public int getI() {
      return i;
    }
  }

  interface Tee {

    String getS();
    Bar getBar();
  }

  static class TeeImpl implements Tee {

    final String s;
    @Inject Bar bar;

    @Inject
    TeeImpl(@Inject("s") String s) {
      this.s = s;
    }

    public String getS() {
      return s;
    }

    public Bar getBar() {
      return bar;
    }
  }

  public void testCircularlyDependentConstructors() {
    ContainerBuilder builder = new ContainerBuilder();
    builder.bind(A.class).to(AImpl.class);
    builder.bind(B.class).to(BImpl.class);

    Container container = builder.create(false);
    A a = container.newInstance(AImpl.class);
    assertNotNull(a.getB().getA());
  }

  interface A {
    B getB();
  }

  @Scoped(SINGLETON)
  static class AImpl implements A {
    final B b;
    @Inject public AImpl(B b) {
      this.b = b;
    }
    public B getB() {
      return b;
    }
  }

  interface B {
    A getA();
  }

  static class BImpl implements B {
    final A a;
    @Inject public BImpl(A a) {
      this.a = a;
    }
    public A getA() {
      return a;
    }
  }

  public void testInjectStatics() {
    ContainerBuilder builder = new ContainerBuilder();
    builder.bind("s").to("test");
    builder.bind("i").to(5);
    builder.injectStatics(Static.class);
    builder.create(false);

    assertEquals("test", Static.s);
    assertEquals(5, Static.i);
  }

  static class Static {

    @Inject("i") static int i;

    static String s;

    @Inject("s") static void setS(String s) {
      Static.s = s;
    }
  }
}