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
public class ContainerTest extends TestCase {

  @Retention(RUNTIME)
  @Binder @interface Other {}

  @Retention(RUNTIME)
  @Binder @interface S {}

  @Retention(RUNTIME)
  @Binder @interface I {}

  public void testFactoryMethods() throws ContainerCreationException {
    Singleton singleton = new Singleton();
    Singleton other = new Singleton();

    ContainerBuilder builder = new ContainerBuilder();
    builder.bind(Singleton.class).to(singleton);
    builder.bind(Singleton.class)
        .annotatedWith(Other.class)
        .to(other);
    Container container = builder.create();

    assertSame(singleton,
        container.getFactory(Key.get(Singleton.class)).get());
    assertSame(singleton, container.getFactory(Singleton.class).get());
    assertSame(singleton,
        container.getFactory(new TypeLiteral<Singleton>() {}).get());
    assertSame(singleton, container.getInstance(Key.get(Singleton.class)));
    assertSame(singleton, container.getInstance(Singleton.class));
    assertSame(singleton,
        container.getInstance(new TypeLiteral<Singleton>() {}));

    assertSame(other,
        container.getFactory(Key.get(Singleton.class, Other.class)).get());
    assertSame(other, container.getFactory(Singleton.class, Other.class).get());
    assertSame(other,
        container.getFactory(new TypeLiteral<Singleton>() {}, Other.class).get());
    assertSame(other, container.getInstance(Key.get(Singleton.class, Other.class)));
    assertSame(other, container.getInstance(Singleton.class, Other.class));
    assertSame(other,
        container.getInstance(new TypeLiteral<Singleton>() {}, Other.class));
  }

  static class Singleton {}

  public void testInjection() throws ContainerCreationException {
    Container container = createFooContainer();
    Foo foo = container.getFactory(Foo.class).get();

    assertEquals("test", foo.s);
    assertEquals("test", foo.bar.getTee().getS());
    assertSame(foo.bar, foo.copy);
    assertEquals(5, foo.i);
    assertEquals(5, foo.bar.getI());

    // Test circular dependency.
    assertSame(foo.bar, foo.bar.getTee().getBar());
  }

  private Container createFooContainer() throws ContainerCreationException {
    ContainerBuilder builder = new ContainerBuilder();

    builder.install(new AbstractModule() {
      protected void configure() {
        bind(Bar.class).to(BarImpl.class);
        bind(Tee.class).to(TeeImpl.class);
        bindConstant(S.class).to("test");
        bindConstant(I.class).to(5);
      }
    });

    return builder.create();
  }

  public void testGetInstance() throws ContainerCreationException {
    Container container = createFooContainer();

    Bar bar = container.getFactory(Key.get(Bar.class)).get();
    assertEquals("test", bar.getTee().getS());
    assertEquals(5, bar.getI());
  }

  public void testIntAndIntegerAreInterchangeable()
      throws ContainerCreationException {
    ContainerBuilder builder = new ContainerBuilder();
    builder.bindConstant(I.class).to(5);
    Container container = builder.create();
    IntegerWrapper iw = container.getFactory(IntegerWrapper.class).get();
    assertEquals(5, (int) iw.i);
  }

  static class IntegerWrapper {
    @Inject @I Integer i;
  }

  static class Foo {

    @Inject Bar bar;
    @Inject Bar copy;

    @Inject @S String s;

    int i;

    @Inject
    void setI(@I int i) {
      this.i = i;
    }
  }

  interface Bar {

    Tee getTee();
    int getI();
  }

  @ContainerScoped
  static class BarImpl implements Bar {

    @Inject @I int i;

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
    TeeImpl(@S String s) {
      this.s = s;
    }

    public String getS() {
      return s;
    }

    public Bar getBar() {
      return bar;
    }
  }

  public void testCircularlyDependentConstructors()
      throws ContainerCreationException {
    ContainerBuilder builder = new ContainerBuilder();
    builder.bind(A.class).to(AImpl.class);
    builder.bind(B.class).to(BImpl.class);

    Container container = builder.create();
    A a = container.getFactory(AImpl.class).get();
    assertNotNull(a.getB().getA());
  }

  interface A {
    B getB();
  }

  @ContainerScoped
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

  public void testInjectStatics() throws ContainerCreationException {
    ContainerBuilder builder = new ContainerBuilder();
    builder.bindConstant(S.class).to("test");
    builder.bindConstant(I.class).to(5);
    builder.requestStaticInjection(Static.class);
    builder.create();

    assertEquals("test", Static.s);
    assertEquals(5, Static.i);
  }

  static class Static {

    @Inject @I static int i;

    static String s;

    @Inject static void setS(@S String s) {
      Static.s = s;
    }
  }
}