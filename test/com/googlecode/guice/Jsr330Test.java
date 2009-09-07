/**
 * Copyright (C) 2009 Google Inc.
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

package com.googlecode.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Jsr330;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Qualifier;
import junit.framework.TestCase;

public class Jsr330Test extends TestCase {

  private final B b = new B();
  private final C c = new C();
  private final D d = new D();
  private final E e = new E();

  public void testInject() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(B.class).toInstance(b);
        bind(C.class).toInstance(c);
        bind(D.class).toInstance(d);
        bind(E.class).toInstance(e);
        bind(A.class);
      }
    });

    A a = injector.getInstance(A.class);
    assertSame(b, a.b);
    assertSame(c, a.c);
    assertSame(d, a.d);
    assertSame(e, a.e);
  }

  public void testQualifiedInject() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(B.class).annotatedWith(Jsr330.named("jodie")).toInstance(b);
        bind(C.class).annotatedWith(Red.class).toInstance(c);
        bind(D.class).annotatedWith(RED).toInstance(d);
        bind(E.class).annotatedWith(Jsr330.named("jesse")).toInstance(e);
        bind(F.class);
      }
    });

    F f = injector.getInstance(F.class);
    assertSame(b, f.b);
    assertSame(c, f.c);
    assertSame(d, f.d);
    assertSame(e, f.e);
  }

  public void testProviderInject() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(B.class).annotatedWith(Jsr330.named("jodie")).toInstance(b);
        bind(C.class).toInstance(c);
        bind(D.class).annotatedWith(RED).toInstance(d);
        bind(E.class).toInstance(e);
        bind(G.class);
      }
    });

    G g = injector.getInstance(G.class);
    assertSame(b, g.bProvider.get());
    assertSame(c, g.cProvider.get());
    assertSame(d, g.dProvider.get());
    assertSame(e, g.eProvider.get());
  }

  static class A {
    final B b;
    @Inject C c;
    D d;
    E e;

    @Inject A(B b) {
      this.b = b;
    }

    @Inject void injectD(D d, E e) {
      this.d = d;
      this.e = e;
    }
  }

  static class B {}
  static class C {}
  static class D {}
  static class E {}

  static class F {
    final B b;
    @Inject @Red C c;
    D d;
    E e;

    @Inject F(@Named("jodie") B b) {
      this.b = b;
    }

    @Inject void injectD(@Red D d, @Named("jesse") E e) {
      this.d = d;
      this.e = e;
    }
  }

  @Qualifier @Retention(RUNTIME)
  @interface Red {}

  public static final Red RED = new Red() {
    public Class<? extends Annotation> annotationType() {
      return Red.class;
    }

    @Override public boolean equals(Object obj) {
      return obj instanceof Red;
    }

    @Override public int hashCode() {
      return 0;
    }
  };

  static class G {
    final Provider<B> bProvider;
    @Inject Provider<C> cProvider;
    Provider<D> dProvider;
    Provider<E> eProvider;

    @Inject G(@Named("jodie") Provider<B> bProvider) {
      this.bProvider = bProvider;
    }

    @Inject void injectD(@Red Provider<D> dProvider, Provider<E> eProvider) {
      this.dProvider = dProvider;
      this.eProvider = eProvider;
    }
  }
}
