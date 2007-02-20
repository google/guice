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

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import junit.framework.TestCase;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class FactoryTest extends TestCase {

  public void testParameterIndex() throws CreationException {
    BinderImpl cb = new BinderImpl();

    cb.bind(Zero.class).toFactory(new Factory<Zero>() {
      public Zero get(Context context) {
        assertEquals(0, context.getParameterIndex());
        return new Zero();
      }
    });

    cb.bind(One.class).toFactory(new Factory<One>() {
      public One get(Context context) {
        assertEquals(1, context.getParameterIndex());
        return new One();
      }
    });

    cb.bind(NegativeOne.class).toFactory(new Factory<NegativeOne>() {
      public NegativeOne get(Context context) {
        assertEquals(-1, context.getParameterIndex());
        return new NegativeOne();
      }
    });

    Container c = cb.createContainer();

    A a = c.getLocator(A.class).get();

    assertNotNull(a.negativeOne);
    assertTrue(a.initCalled);
  }

  static class A {

    @Inject
    NegativeOne negativeOne;

    boolean initCalled;

    @Inject void init(Zero zero, One one) {
      assertNotNull(zero);
      assertNotNull(one);
      initCalled = true;
    }
  }

  static class Zero {}
  static class One {}
  static class NegativeOne {}

  @Retention(RUNTIME)
  @BindingAnnotation @interface FooAnnotation {}

  @Retention(RUNTIME)
  @BindingAnnotation @interface BarAnnotation {}

  @Retention(RUNTIME)
  @BindingAnnotation @interface TeeAnnotation1 {}

  @Retention(RUNTIME)
  @BindingAnnotation @interface TeeAnnotation2 {}

  @Retention(RUNTIME)
  @BindingAnnotation @interface BobAnnotation1 {}

  @Retention(RUNTIME)
  @BindingAnnotation @interface BobAnnotation2 {}

  public void testInjection() throws Exception {
    BinderImpl cb = new BinderImpl(Stage.PRODUCTION);

    // Called from getInstance().
    cb.bind(Foo.class)
        .annotatedWith(FooAnnotation.class)
        .toFactory(createFactory(Foo.class, FooAnnotation.class, null));

    // Called during preloading.
    cb.bind(Bar.class)
        .annotatedWith(BarAnnotation.class)
        .toFactory(createFactory(Bar.class, BarAnnotation.class, null))
        .in(Scopes.CONTAINER);

    cb.bind(Tee.class)
        .annotatedWith(TeeAnnotation1.class)
        .toFactory(createFactory(Tee.class, TeeAnnotation1.class,
            Bar.class.getDeclaredConstructor(Tee.class)));

    cb.bind(Tee.class)
        .annotatedWith(TeeAnnotation2.class)
        .toFactory(createFactory(Tee.class, TeeAnnotation2.class,
            Bar.class.getDeclaredField("tee2")));

    final Method execute = Tee.class.getDeclaredMethod(
        "execute", Bob.class, Bob.class);
    cb.bind(Bob.class).annotatedWith(BobAnnotation1.class).toFactory(
        createFactory(Bob.class, BobAnnotation1.class, execute));
    cb.bind(Bob.class).annotatedWith(BobAnnotation2.class).toFactory(
        createFactory(Bob.class, BobAnnotation2.class, execute));

    Container c = cb.createContainer();

    Foo foo = c.getLocator(Key.get(Foo.class, FooAnnotation.class)).get();

    assertNotNull(foo.bar);
    assertNotNull(foo.bar.tee1);
    assertNotNull(foo.bar.tee2);
    assertNotNull(foo.bar.tee1.bob1);
    assertNotNull(foo.bar.tee1.bob2);
    assertNotNull(foo.bar.tee2.bob1);
    assertNotNull(foo.bar.tee2.bob2);
  }

  <T> Factory<T> createFactory(
      final Class<T> type, final Class<? extends Annotation> annotationType,
      final Member expectedMember) {
    return new Factory<T>() {
      public T get(Context context) {
        assertEquals(expectedMember, context.getMember());
        assertEquals(type, context.getKey().getType().getType());
        return context.getContainer().getLocator(type).get();
      }
    };
  }

  static class Foo {
    final Bar bar;
    @Inject Foo(@BarAnnotation Bar bar) {
      this.bar = bar;
    }
  }

  static class Bar {
    @Inject @TeeAnnotation2 Tee tee2;
    final Tee tee1;
    @Inject Bar(@TeeAnnotation1 Tee tee1) {
      this.tee1 = tee1;
    }
  }

  static class Tee {
    Bob bob1, bob2;
    @Inject void execute(
        @BobAnnotation1 Bob bob1,
        @BobAnnotation2 Bob bob2) {
      this.bob1 = bob1;
      this.bob2 = bob2;
    }
  }

  static class Bob {}
}
