/*
 * Copyright (C) 2010 Google Inc.
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
package com.google.inject.mini;

import com.google.inject.Provides;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import junit.framework.TestCase;

public final class MiniGuiceTest extends TestCase {

  public void testBasicInjection() {
    G g = MiniGuice.inject(G.class, new Object() {
      @Provides E provideE(F f) {
        return new E(f);
      }
      @Provides F provideF() {
        return new F();
      }
    });

    assertNotNull(g.a);
    assertNotNull(g.b);
    assertNotNull(g.c);
    assertNotNull(g.d);
    assertNotNull(g.e);
    assertNotNull(g.e.f);
  }

  static class A {
    @Inject A() {}
  }

  static class B {
    @Inject B() {}
  }

  @Singleton
  static class C {
    @Inject C() {}
  }

  @Singleton
  static class D {
    @Inject D() {}
  }

  static class E {
    F f;
    E(F f) {
      this.f = f;
    }
  }

  static class F {}

  static class G {
    @Inject A a;
    @Inject B b;
    C c;
    D d;
    @Inject E e;
    @Inject G(C c, D d) {
      this.c = c;
      this.d = d;
    }
  }
  
  public void testProviderInjection() {
    H h = MiniGuice.inject(H.class);
    assertNotNull(h.aProvider.get());
    assertNotNull(h.aProvider.get());
    assertNotSame(h.aProvider.get(), h.aProvider.get());
  }

  static class H {
    @Inject Provider<A> aProvider;
    @Inject H() {}
  }
  
  public void testSingletons() {
    J j = MiniGuice.inject(J.class, new Object() {
      @Provides @Singleton F provideK() {
        return new F();
      }
    });
    assertSame(j.fProvider.get(), j.fProvider.get());
    assertSame(j.iProvider.get(), j.iProvider.get());
  }

  @Singleton
  static class I {
    @Inject I() {}
  }

  static class J {
    @Inject Provider<F> fProvider;
    @Inject Provider<I> iProvider;
    @Inject J() {}
  }

  public void testBindingAnnotations() {
    final A one = new A();
    final A two = new A();

    K k = MiniGuice.inject(K.class, new Object() {
      @Provides @Named("one") A getOne() {
        return one;
      }
      @Provides @Named("two") A getTwo() {
        return two;
      }
    });

    assertNotNull(k.a);
    assertSame(one, k.aOne);
    assertSame(two, k.aTwo);
  }

  public static class K {
    @Inject A a;
    @Inject @Named("one") A aOne;
    @Inject @Named("two") A aTwo;
  }
  
  public void testSingletonBindingAnnotationAndProvider() {
    final AtomicReference<A> a1 = new AtomicReference<A>();
    final AtomicReference<A> a2 = new AtomicReference<A>();

    L l = MiniGuice.inject(L.class, new Object() {
      @Provides @Singleton @Named("one") F provideF(Provider<A> aProvider) {
        a1.set(aProvider.get());
        a2.set(aProvider.get());
        return new F();
      }
    });

    assertNotNull(a1.get());
    assertNotNull(a2.get());
    assertNotSame(a1.get(), a2.get());
    assertSame(l, l.lProvider.get());
  }

  @Singleton
  public static class L {
    @Inject @Named("one") F f;
    @Inject Provider<L> lProvider;
  }

  public void testSingletonInGraph() {
    M m = MiniGuice.inject(M.class, new Object() {
      @Provides @Singleton F provideF() {
        return new F();
      }
    });

    assertSame(m.f1, m.f2);
    assertSame(m.f1, m.n1.f1);
    assertSame(m.f1, m.n1.f2);
    assertSame(m.f1, m.n2.f1);
    assertSame(m.f1, m.n2.f2);
    assertSame(m.f1, m.n1.fProvider.get());
    assertSame(m.f1, m.n2.fProvider.get());
  }

  public static class M {
    @Inject N n1;
    @Inject N n2;
    @Inject F f1;
    @Inject F f2;
  }

  public static class N {
    @Inject F f1;
    @Inject F f2;
    @Inject Provider<F> fProvider;
  }

  public void testNoJitBindingsForAnnotations() {
    try {
      MiniGuice.inject(O.class);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  public static class O {
    @Inject @Named("a") A a;
  }

  public void testSubclasses() {
    Q q = MiniGuice.inject(Q.class, new Object() {
      @Provides F provideF() {
        return new F();
      }
    });

    assertNotNull(q.f);
  }
  
  public static class P {
    @Inject F f;
  }

  public static class Q extends P {
    @Inject Q() {}
  }
  
  public void testSingletonsAreEager() {
    final AtomicBoolean sInjected = new AtomicBoolean();

    R.injected = false;
    MiniGuice.inject(A.class, new Object() {
      @Provides F provideF(R r) {
        return new F();
      }

      @Provides @Singleton S provideS() {
        sInjected.set(true);
        return new S();
      }
    });

    assertTrue(R.injected);
    assertTrue(sInjected.get());
  }

  @Singleton
  static class R {
    static boolean injected = false;
    @Inject R() {
      injected = true;
    }
  }

  static class S {}
}
