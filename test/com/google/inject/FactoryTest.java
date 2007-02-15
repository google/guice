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

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.annotation.Retention;
import java.lang.annotation.Annotation;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class FactoryTest extends TestCase {

  public void testParameterIndex() throws ContainerCreationException {
    ContainerBuilder cb = new ContainerBuilder();

    cb.bind(Zero.class).to(new ContextualFactory<Zero>() {
      public Zero get(Context context) {
        assertEquals(0, context.getParameterIndex());
        return new Zero();
      }
    });

    cb.bind(One.class).to(new ContextualFactory<One>() {
      public One get(Context context) {
        assertEquals(1, context.getParameterIndex());
        return new One();
      }
    });

    cb.bind(NegativeOne.class).to(new ContextualFactory<NegativeOne>() {
      public NegativeOne get(Context context) {
        assertEquals(-1, context.getParameterIndex());
        return new NegativeOne();
      }
    });

    Container c = cb.create(false);

    A a = c.getFactory(A.class).get();

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
  @ForBinding
  @interface FooAnnotation {}

  @Retention(RUNTIME)
  @ForBinding
  @interface BarAnnotation {}

  @Retention(RUNTIME)
  @ForBinding
  @interface TeeAnnotation1 {}

  @Retention(RUNTIME)
  @ForBinding
  @interface TeeAnnotation2 {}

  @Retention(RUNTIME)
  @ForBinding
  @interface BobAnnotation1 {}

  @Retention(RUNTIME)
  @ForBinding
  @interface BobAnnotation2 {}

  public void testInjection() throws Exception {
    ContainerBuilder cb = new ContainerBuilder();

    // Called from getInstance().
    cb.bind(Foo.class)
        .annotatedWith(FooAnnotation.class)
        .to(createFactory(Foo.class, FooAnnotation.class, null));

    // Called during preloading.
    cb.bind(Bar.class)
        .annotatedWith(BarAnnotation.class)
        .to(createFactory(Bar.class, BarAnnotation.class, null))
        .in(Scopes.CONTAINER);

    cb.bind(Tee.class)
        .annotatedWith(TeeAnnotation1.class)
        .to(createFactory(Tee.class, TeeAnnotation1.class,
            Bar.class.getDeclaredConstructor(Tee.class)));

    cb.bind(Tee.class)
        .annotatedWith(TeeAnnotation2.class)
        .to(createFactory(Tee.class, TeeAnnotation2.class,
            Bar.class.getDeclaredField("tee2")));

    final Method execute = Tee.class.getDeclaredMethod(
        "execute", Bob.class, Bob.class);
    cb.bind(Bob.class).annotatedWith(BobAnnotation1.class).to(
        createFactory(Bob.class, BobAnnotation1.class, execute));
    cb.bind(Bob.class).annotatedWith(BobAnnotation2.class).to(
        createFactory(Bob.class, BobAnnotation2.class, execute));

    Container c = cb.create(true);

    Foo foo = c.getFactory(Key.get(Foo.class, FooAnnotation.class)).get();

    assertNotNull(foo.bar);
    assertNotNull(foo.bar.tee1);
    assertNotNull(foo.bar.tee2);
    assertNotNull(foo.bar.tee1.bob1);
    assertNotNull(foo.bar.tee1.bob2);
    assertNotNull(foo.bar.tee2.bob1);
    assertNotNull(foo.bar.tee2.bob2);
  }

  <T> ContextualFactory<T> createFactory(
      final Class<T> type, final Class<? extends Annotation> annotationType,
      final Member expectedMember) {
    return new ContextualFactory<T>() {
      public T get(Context context) {
        assertEquals(expectedMember, context.getMember());
        assertEquals(type, context.getKey().getType().getType());
        return context.getContainer().getFactory(type).get();
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
