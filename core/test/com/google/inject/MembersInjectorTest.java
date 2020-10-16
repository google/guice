/*
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

package com.google.inject;

import static com.google.inject.Asserts.assertContains;

import com.google.inject.internal.Annotations;
import com.google.inject.name.Names;
import com.google.inject.util.Providers;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

/** @author jessewilson@google.com (Jesse Wilson) */
public class MembersInjectorTest extends TestCase {

  private static final long DEADLOCK_TIMEOUT_SECONDS = 1;

  private static final A<C> uninjectableA =
      new A<C>() {
        @Inject
        @Override
        void doNothing() {
          throw new AssertionFailedError();
        }
      };

  private static final B uninjectableB =
      new B() {
        @Inject
        @Override
        void doNothing() {
          throw new AssertionFailedError();
        }
      };

  private static final C myFavouriteC = new C();

  public void testMembersInjectorFromBinder() {
    final AtomicReference<MembersInjector<A<C>>> aMembersInjectorReference =
        new AtomicReference<MembersInjector<A<C>>>();
    final AtomicReference<MembersInjector<B>> bMembersInjectorReference =
        new AtomicReference<MembersInjector<B>>();

    Guice.createInjector(
        new AbstractModule() {
          @Override
          protected void configure() {
            MembersInjector<A<C>> aMembersInjector = getMembersInjector(new TypeLiteral<A<C>>() {});
            try {
              aMembersInjector.injectMembers(uninjectableA);
              fail();
            } catch (IllegalStateException expected) {
              assertContains(
                  expected.getMessage(),
                  "This MembersInjector cannot be used until the Injector has been created.");
            }

            MembersInjector<B> bMembersInjector = getMembersInjector(B.class);
            try {
              bMembersInjector.injectMembers(uninjectableB);
              fail();
            } catch (IllegalStateException expected) {
              assertContains(
                  expected.getMessage(),
                  "This MembersInjector cannot be used until the Injector has been created.");
            }

            aMembersInjectorReference.set(aMembersInjector);
            bMembersInjectorReference.set(bMembersInjector);

            assertEquals(
                "MembersInjector<java.lang.String>", getMembersInjector(String.class).toString());

            bind(C.class).toInstance(myFavouriteC);
          }
        });

    A<C> injectableA = new A<>();
    aMembersInjectorReference.get().injectMembers(injectableA);
    assertSame(myFavouriteC, injectableA.t);
    assertSame(myFavouriteC, injectableA.b.c);

    B injectableB = new B();
    bMembersInjectorReference.get().injectMembers(injectableB);
    assertSame(myFavouriteC, injectableB.c);

    B anotherInjectableB = new B();
    bMembersInjectorReference.get().injectMembers(anotherInjectableB);
    assertSame(myFavouriteC, anotherInjectableB.c);
  }

  public void testMembersInjectorFromInjector() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(C.class).toInstance(myFavouriteC);
              }
            });

    MembersInjector<A<C>> aMembersInjector =
        injector.getMembersInjector(new TypeLiteral<A<C>>() {});
    MembersInjector<B> bMembersInjector = injector.getMembersInjector(B.class);

    A<C> injectableA = new A<>();
    aMembersInjector.injectMembers(injectableA);
    assertSame(myFavouriteC, injectableA.t);
    assertSame(myFavouriteC, injectableA.b.c);

    B injectableB = new B();
    bMembersInjector.injectMembers(injectableB);
    assertSame(myFavouriteC, injectableB.c);

    B anotherInjectableB = new B();
    bMembersInjector.injectMembers(anotherInjectableB);
    assertSame(myFavouriteC, anotherInjectableB.c);

    assertEquals(
        "MembersInjector<java.lang.String>", injector.getMembersInjector(String.class).toString());
  }

  public void testMembersInjectorWithNonInjectedTypes() {
    Injector injector = Guice.createInjector();

    MembersInjector<NoInjectedMembers> membersInjector =
        injector.getMembersInjector(NoInjectedMembers.class);

    membersInjector.injectMembers(new NoInjectedMembers());
    membersInjector.injectMembers(new NoInjectedMembers());
  }

  public void testInjectionFailure() {
    Injector injector = Guice.createInjector();

    MembersInjector<InjectionFailure> membersInjector =
        injector.getMembersInjector(InjectionFailure.class);

    try {
      membersInjector.injectMembers(new InjectionFailure());
      fail();
    } catch (ProvisionException expected) {
      assertContains(
          expected.getMessage(),
          "1) Error injecting method, java.lang.ClassCastException: whoops, failure #1");
    }
  }

  public void testInjectionAppliesToSpecifiedType() {
    Injector injector = Guice.createInjector();

    MembersInjector<Object> membersInjector = injector.getMembersInjector(Object.class);
    membersInjector.injectMembers(new InjectionFailure());
  }

  public void testInjectingMembersInjector() {
    InjectsMembersInjector injectsMembersInjector =
        Guice.createInjector(
                new AbstractModule() {
                  @Override
                  protected void configure() {
                    bind(C.class).toInstance(myFavouriteC);
                  }
                })
            .getInstance(InjectsMembersInjector.class);

    A<C> a = new A<>();
    injectsMembersInjector.aMembersInjector.injectMembers(a);
    assertSame(myFavouriteC, a.t);
    assertSame(myFavouriteC, a.b.c);
  }

  public void testCannotBindMembersInjector() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              bind(MembersInjector.class).toProvider(Providers.of(null));
            }
          });
      fail();
    } catch (CreationException expected) {
      assertContains(
          expected.getMessage(),
          "1) Binding to core guice framework type is not allowed: MembersInjector.");
    }

    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              bind(new TypeLiteral<MembersInjector<A<C>>>() {})
                  .toProvider(Providers.<MembersInjector<A<C>>>of(null));
            }
          });
      fail();
    } catch (CreationException expected) {
      assertContains(
          expected.getMessage(),
          "1) Binding to core guice framework type is not allowed: MembersInjector.");
    }
  }

  public void testInjectingMembersInjectorWithErrorsInDependencies() {
    try {
      Guice.createInjector().getInstance(InjectsBrokenMembersInjector.class);
      fail();
    } catch (ConfigurationException expected) {
      assertContains(
          expected.getMessage(),
          "1) No implementation for " + Unimplemented.class.getName() + " was bound.",
          "while locating " + Unimplemented.class.getName(),
          "for field at " + A.class.getName() + ".t(MembersInjectorTest.java:",
          "while locating com.google.inject.MembersInjector<",
          "for field at " + InjectsBrokenMembersInjector.class.getName() + ".aMembersInjector(",
          "while locating " + InjectsBrokenMembersInjector.class.getName());
    }
  }

  public void testLookupMembersInjectorBinding() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(C.class).toInstance(myFavouriteC);
              }
            });
    MembersInjector<A<C>> membersInjector =
        injector.getInstance(new Key<MembersInjector<A<C>>>() {});

    A<C> a = new A<>();
    membersInjector.injectMembers(a);
    assertSame(myFavouriteC, a.t);
    assertSame(myFavouriteC, a.b.c);

    assertEquals(
        "MembersInjector<java.lang.String>",
        injector.getInstance(new Key<MembersInjector<String>>() {}).toString());
  }

  public void testGettingRawMembersInjector() {
    Injector injector = Guice.createInjector();
    try {
      injector.getInstance(MembersInjector.class);
      fail();
    } catch (ConfigurationException expected) {
      assertContains(
          expected.getMessage(), "Cannot inject a MembersInjector that has no type parameter");
    }
  }

  public void testGettingAnnotatedMembersInjector() {
    Injector injector = Guice.createInjector();
    try {
      injector.getInstance(new Key<MembersInjector<String>>(Names.named("foo")) {});
      fail();
    } catch (ConfigurationException expected) {
      assertContains(
          expected.getMessage(),
          "1) No implementation for com.google.inject.MembersInjector<java.lang.String> "
              + "annotated with @com.google.inject.name.Named(value="
              + Annotations.memberValueString("foo")
              + ") was bound.");
    }
  }

  /** Callback for member injection. Uses a static type to be referable by getInstance(). */
  abstract static class AbstractParallelMemberInjectionCallback {

    volatile boolean called = false;

    private final Thread mainThread;
    private final Class<? extends AbstractParallelMemberInjectionCallback> otherCallbackClass;

    AbstractParallelMemberInjectionCallback(
        Class<? extends AbstractParallelMemberInjectionCallback> otherCallbackClass) {
      this.mainThread = Thread.currentThread();
      this.otherCallbackClass = otherCallbackClass;
    }

    @Inject
    void callback(final Injector injector) throws Exception {
      called = true;
      if (mainThread != Thread.currentThread()) {
        // only execute logic on the main thread
        return;
      }
      // verify that other callback can be finished on a separate thread
      AbstractParallelMemberInjectionCallback otherCallback =
          Executors.newSingleThreadExecutor()
              .submit(() -> injector.getInstance(otherCallbackClass))
              .get(DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      assertTrue(otherCallback.called);

      try {
        // other thread would wait for callback to finish on this thread first
        Executors.newSingleThreadExecutor()
            .submit(
                () -> injector.getInstance(AbstractParallelMemberInjectionCallback.this.getClass()))
            .get(DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        fail();
      } catch (TimeoutException expected) {
        // recursive call from another thread should time out
        // as it would be waiting for this thread to finish
      }
    }
  }

  static class ParallelMemberInjectionCallback1 extends AbstractParallelMemberInjectionCallback {

    ParallelMemberInjectionCallback1() {
      super(ParallelMemberInjectionCallback2.class);
    }
  }

  static class ParallelMemberInjectionCallback2 extends AbstractParallelMemberInjectionCallback {

    ParallelMemberInjectionCallback2() {
      super(ParallelMemberInjectionCallback1.class);
    }
  }

  /**
   * Tests that member injections could happen in parallel.
   *
   * <p>Additional check that when member injection happen other threads would wait for it to finish
   * to provide proper resolution order semantics.
   */

  public void testMemberInjectorParallelization() throws Exception {
    final ParallelMemberInjectionCallback1 c1 = new ParallelMemberInjectionCallback1();
    final ParallelMemberInjectionCallback2 c2 = new ParallelMemberInjectionCallback2();
    Guice.createInjector(
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(ParallelMemberInjectionCallback1.class).toInstance(c1);
            bind(ParallelMemberInjectionCallback2.class).toInstance(c2);
          }
        });
    assertTrue(c1.called);
    assertTrue(c2.called);
  }

  /** Member injection callback that injects itself. */
  static class RecursiveMemberInjection {
    boolean called = false;

    @Inject
    void callback(RecursiveMemberInjection recursiveMemberInjection) {
      if (called) {
        fail("Should not be called twice");
      }
      called = true;
    }
  }

  /** Verifies that member injection injecting itself would get a non initialized instance. */
  public void testRecursiveMemberInjector() throws Exception {
    final RecursiveMemberInjection rmi = new RecursiveMemberInjection();
    Guice.createInjector(
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(RecursiveMemberInjection.class).toInstance(rmi);
          }
        });
    assertTrue("Member injection should happen", rmi.called);
  }

  static class A<T> {
    @Inject B b;
    @Inject T t;

    @Inject
    void doNothing() {}
  }

  static class B {
    @Inject C c;

    @Inject
    void doNothing() {}
  }

  static class C {}

  static class NoInjectedMembers {}

  static class InjectionFailure {
    int failures = 0;

    @Inject
    void fail() {
      throw new ClassCastException("whoops, failure #" + (++failures));
    }
  }

  static class InjectsMembersInjector {
    @Inject MembersInjector<A<C>> aMembersInjector;
    @Inject A<B> ab;
  }

  static class InjectsBrokenMembersInjector {
    @Inject MembersInjector<A<Unimplemented>> aMembersInjector;
  }

  static interface Unimplemented {}
}
