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

import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import junit.framework.TestCase;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class ContainerTest extends TestCase {

  @Retention(RUNTIME)
  @BindingAnnotation @interface Other {}

  @Retention(RUNTIME)
  @BindingAnnotation @interface S {}

  @Retention(RUNTIME)
  @BindingAnnotation @interface I {}

  public void testLocatorMethods() throws CreationException {
    Singleton singleton = new Singleton();
    Singleton other = new Singleton();

    BinderImpl builder = new BinderImpl();
    builder.bind(Singleton.class).toInstance(singleton);
    builder.bind(Singleton.class)
        .annotatedWith(Other.class)
        .toInstance(other);
    Container container = builder.createContainer();

    assertSame(singleton,
        container.getLocator(Key.get(Singleton.class)).get());
    assertSame(singleton, container.getLocator(Singleton.class).get());
//    assertSame(singleton,
//        container.getLocator(new TypeLiteral<Singleton>() {}).get());
//    assertSame(singleton, container.getInstance(Key.get(Singleton.class)));
//    assertSame(singleton, container.getInstance(Singleton.class));
//    assertSame(singleton,
//        container.getInstance(new TypeLiteral<Singleton>() {}));

    assertSame(other,
        container.getLocator(Key.get(Singleton.class, Other.class)).get());
//    assertSame(other, container.getLocator(Singleton.class, Other.class).get());
//    assertSame(other,
//        container.getLocator(new TypeLiteral<Singleton>() {}, Other.class).get());
//    assertSame(other, container.getInstance(Key.get(Singleton.class, Other.class)));
//    assertSame(other, container.getInstance(Singleton.class, Other.class));
//    assertSame(other,
//        container.getInstance(new TypeLiteral<Singleton>() {}, Other.class));
  }

  static class Singleton {}

  public void testInjection() throws CreationException {
    Container container = createFooContainer();
    Foo foo = container.getLocator(Foo.class).get();

    assertEquals("test", foo.s);
    assertEquals("test", foo.bar.getTee().getS());
    assertSame(foo.bar, foo.copy);
    assertEquals(5, foo.i);
    assertEquals(5, foo.bar.getI());

    // Test circular dependency.
    assertSame(foo.bar, foo.bar.getTee().getBar());
  }

  private Container createFooContainer() throws CreationException {
    return Guice.createContainer(new AbstractModule() {
      protected void configure() {
        bind(Bar.class).to(BarImpl.class);
        bind(Tee.class).to(TeeImpl.class);
        bindConstant(S.class).to("test");
        bindConstant(I.class).to(5);
      }
    });
  }

  public void testGetInstance() throws CreationException {
    Container container = createFooContainer();

    Bar bar = container.getLocator(Key.get(Bar.class)).get();
    assertEquals("test", bar.getTee().getS());
    assertEquals(5, bar.getI());
  }

  public void testIntAndIntegerAreInterchangeable()
      throws CreationException {
    BinderImpl builder = new BinderImpl();
    builder.bindConstant(I.class).to(5);
    Container container = builder.createContainer();
    IntegerWrapper iw = container.getLocator(IntegerWrapper.class).get();
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
      throws CreationException {
    BinderImpl builder = new BinderImpl();
    builder.bind(A.class).to(AImpl.class);
    builder.bind(B.class).to(BImpl.class);

    Container container = builder.createContainer();
    A a = container.getLocator(AImpl.class).get();
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

  public void testInjectStatics() throws CreationException {
    BinderImpl builder = new BinderImpl();
    builder.bindConstant(S.class).to("test");
    builder.bindConstant(I.class).to(5);
    builder.requestStaticInjection(Static.class);
    builder.createContainer();

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

  public void testPrivateInjection() throws CreationException {
    Container container = Guice.createContainer(new AbstractModule() {
      protected void configure() {
        bind(String.class).toInstance("foo");
        bind(int.class).toInstance(5);
      }
    });

    Private p = container.getLocator(Private.class).get();
    assertEquals("foo", p.fromConstructor);
    assertEquals(5, p.fromMethod);
  }

  static class Private {
    String fromConstructor;
    int fromMethod;

    @Inject
    private Private(String fromConstructor) {
      this.fromConstructor = fromConstructor;
    }

    @Inject
    private void setInt(int i) {
      this.fromMethod = i;
    }
  }
}