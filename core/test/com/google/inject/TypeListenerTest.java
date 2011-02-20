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

package com.google.inject;

import static com.google.inject.Asserts.assertContains;
import com.google.inject.internal.util.ImmutableList;
import com.google.inject.internal.util.Lists;
import com.google.inject.matcher.Matcher;
import com.google.inject.matcher.Matchers;
import static com.google.inject.matcher.Matchers.any;
import static com.google.inject.matcher.Matchers.only;
import static com.google.inject.name.Names.named;
import com.google.inject.spi.InjectionListener;
import com.google.inject.spi.Message;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import junit.framework.TestCase;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class TypeListenerTest extends TestCase {

  private final Matcher<Object> onlyAbcd = Matchers.only(new TypeLiteral<A>() {})
      .or(only(new TypeLiteral<B>() {}))
      .or(only(new TypeLiteral<C>() {}))
      .or(only(new TypeLiteral<D>() {}));

  final TypeListener failingTypeListener = new TypeListener() {
    int failures = 0;

    public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
      throw new ClassCastException("whoops, failure #" + (++failures));
    }

    @Override public String toString() {
      return "clumsy";
    }
  };

  final InjectionListener<Object> failingInjectionListener = new InjectionListener<Object>() {
    int failures = 0;

    public void afterInjection(Object injectee) {
      throw new ClassCastException("whoops, failure #" + (++failures));
    }

    @Override public String toString() {
      return "goofy";
    }
  };

  final MembersInjector<Object> failingMembersInjector = new MembersInjector<Object>() {
    int failures = 0;

    public void injectMembers(Object instance) {
      throw new ClassCastException("whoops, failure #" + (++failures));
    }

    @Override public String toString() {
      return "awkward";
    }
  };

  public void testTypeListenersAreFired() throws NoSuchMethodException {
    final AtomicInteger firedCount = new AtomicInteger();

    final TypeListener typeListener = new TypeListener() {
      public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
        assertEquals(new TypeLiteral<A>() {}, type);
        firedCount.incrementAndGet();
      }
    };

    Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindListener(onlyAbcd, typeListener);
        bind(A.class);
      }
    });

    assertEquals(1, firedCount.get());
  }

  public void testInstallingInjectionListener() {
    final List<Object> injectees = Lists.newArrayList();
    final InjectionListener<Object> injectionListener = new InjectionListener<Object>() {
      public void afterInjection(Object injectee) {
        injectees.add(injectee);
      }
    };

    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindListener(onlyAbcd, new TypeListener() {
          public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
            encounter.register(injectionListener);
          }
        });
        bind(A.class);
      }
    });

    assertEquals(ImmutableList.of(), injectees);

    Object a1 = injector.getInstance(A.class);
    assertEquals(ImmutableList.of(a1), injectees);

    Object a2 = injector.getInstance(A.class);
    assertEquals(ImmutableList.of(a1, a2), injectees);

    Object b1 = injector.getInstance(B.class);
    assertEquals(ImmutableList.of(a1, a2, b1), injectees);

    Provider<A> aProvider = injector.getProvider(A.class);
    assertEquals(ImmutableList.of(a1, a2, b1), injectees);
    A a3 = aProvider.get();
    A a4 = aProvider.get();
    assertEquals(ImmutableList.of(a1, a2, b1, a3, a4), injectees);
  }
  
  /*if[AOP]*/
  private static org.aopalliance.intercept.MethodInterceptor prefixInterceptor(
      final String prefix) {
    return new org.aopalliance.intercept.MethodInterceptor() {
      public Object invoke(org.aopalliance.intercept.MethodInvocation methodInvocation)
          throws Throwable {
        return prefix + methodInvocation.proceed();
      }
    };
  }

  public void testAddingInterceptors() throws NoSuchMethodException {
    final Matcher<Object> buzz = only(C.class.getMethod("buzz"));

    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindInterceptor(any(), buzz, prefixInterceptor("ka"));
        bindInterceptor(any(), any(), prefixInterceptor("fe"));

        bindListener(onlyAbcd, new TypeListener() {
          public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
            encounter.bindInterceptor(any(), prefixInterceptor("li"));
            encounter.bindInterceptor(buzz, prefixInterceptor("no"));
          }
        });
      }
    });

    // interceptors must be invoked in the order they're bound.
    C c = injector.getInstance(C.class);
    assertEquals("kafelinobuzz", c.buzz());
    assertEquals("felibeep", c.beep());
  }
  /*end[AOP]*/

  public void testTypeListenerThrows() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bindListener(onlyAbcd, failingTypeListener);
          bind(B.class);
          bind(C.class);
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "1) Error notifying TypeListener clumsy (bound at " + getClass().getName(),
          ".configure(TypeListenerTest.java:",
          "of " + B.class.getName(), 
          "Reason: java.lang.ClassCastException: whoops, failure #1",
          "2) Error notifying TypeListener clumsy (bound at " + getClass().getName(),
          ".configure(TypeListenerTest.java:",
          "of " + C.class.getName(),
          "Reason: java.lang.ClassCastException: whoops, failure #2");
    }
    
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindListener(onlyAbcd, failingTypeListener);
      }
    });
    try {
      injector.getProvider(B.class);
      fail();
    } catch (ConfigurationException expected) {
      assertContains(expected.getMessage(),
          "1) Error notifying TypeListener clumsy (bound at " + getClass().getName(),
          ".configure(TypeListenerTest.java:",
          "of " + B.class.getName(),
          "Reason: java.lang.ClassCastException: whoops, failure #3");
    }

    // getting it again should yield the same exception #3
    try {
      injector.getInstance(B.class);
      fail();
    } catch (ConfigurationException expected) {
      assertContains(expected.getMessage(),
          "1) Error notifying TypeListener clumsy (bound at " + getClass().getName(),
          ".configure(TypeListenerTest.java:",
          "of " + B.class.getName(),
          "Reason: java.lang.ClassCastException: whoops, failure #3");
    }

    // non-injected types do not participate
    assertSame(Stage.DEVELOPMENT, injector.getInstance(Stage.class));
  }

  public void testInjectionListenerThrows() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindListener(onlyAbcd, new TypeListener() {
          public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
            encounter.register(failingInjectionListener);
          }
        });
        bind(B.class);
      }
    });

    try {
      injector.getInstance(A.class);
      fail();
    } catch (ProvisionException e) {
      assertContains(e.getMessage(),
          "1) Error notifying InjectionListener goofy of " + A.class.getName(),
          " Reason: java.lang.ClassCastException: whoops, failure #1");
    }

    // second time through should be a new cause (#2)
    try {
      injector.getInstance(A.class);
      fail();
    } catch (ProvisionException e) {
      assertContains(e.getMessage(),
          "1) Error notifying InjectionListener goofy of " + A.class.getName(),
          " Reason: java.lang.ClassCastException: whoops, failure #2");
    }

    // we should get errors for all types, but only on getInstance()
    Provider<B> bProvider = injector.getProvider(B.class);
    try {
      bProvider.get();
      fail();
    } catch (ProvisionException e) {
      assertContains(e.getMessage(),
          "1) Error notifying InjectionListener goofy of " + B.class.getName(),
          " Reason: java.lang.ClassCastException: whoops, failure #3");
    }

    // non-injected types do not participate
    assertSame(Stage.DEVELOPMENT, injector.getInstance(Stage.class));
  }

  public void testInjectMembersTypeListenerFails() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          getMembersInjector(A.class);
          bindListener(onlyAbcd, failingTypeListener);
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "1) Error notifying TypeListener clumsy (bound at ",
          TypeListenerTest.class.getName(), ".configure(TypeListenerTest.java:",
          "of " + A.class.getName(),
          " Reason: java.lang.ClassCastException: whoops, failure #1");
    }
  }

  public void testConstructedTypeListenerIsTheSameAsMembersInjectorListener() {
    final AtomicInteger typeEncounters = new AtomicInteger();
    final AtomicInteger injections = new AtomicInteger();

    final InjectionListener<A> listener = new InjectionListener<A>() {
      public void afterInjection(A injectee) {
        injections.incrementAndGet();
        assertNotNull(injectee.injector);
      }
    };

    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindListener(onlyAbcd, new TypeListener() {
          public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
            typeEncounters.incrementAndGet();
            encounter.register((InjectionListener) listener);
          }
        });

        bind(A.class);
        getMembersInjector(A.class);
      }
    });

    // creating the injector shouldn't trigger injections
    assertEquals(0, injections.getAndSet(0));

    // constructing an A should trigger an injection
    injector.getInstance(A.class);
    assertEquals(1, injections.getAndSet(0));

    // injecting an A should trigger an injection
    injector.injectMembers(new A());
    assertEquals(1, injections.getAndSet(0));

    // getting a provider shouldn't
    Provider<A> aProvider = injector.getProvider(A.class);
    MembersInjector<A> aMembersInjector = injector.getMembersInjector(A.class);
    assertEquals(0, injections.getAndSet(0));

    // exercise the provider
    aProvider.get();
    aProvider.get();
    assertEquals(2, injections.getAndSet(0));

    // exercise the members injector
    aMembersInjector.injectMembers(new A());
    aMembersInjector.injectMembers(new A());
    assertEquals(2, injections.getAndSet(0));

    // we should only have encountered one type
    assertEquals(1, typeEncounters.getAndSet(0));
  }

  public void testLookupsAtInjectorCreateTime() {
    final AtomicReference<Provider<B>> bProviderReference = new AtomicReference<Provider<B>>();
    final AtomicReference<MembersInjector<A>> aMembersInjectorReference
        = new AtomicReference<MembersInjector<A>>();

    final InjectionListener<Object> lookupsTester = new InjectionListener<Object>() {
      public void afterInjection(Object injectee) {
        assertNotNull(bProviderReference.get().get());

        A a = new A();
        aMembersInjectorReference.get().injectMembers(a);
        assertNotNull(a.injector);
      }
    };

    Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindListener(only(TypeLiteral.get(C.class)), new TypeListener() {
          public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
            Provider<B> bProvider = encounter.getProvider(B.class);
            try {
              bProvider.get();
              fail();
            } catch (IllegalStateException expected) {
              assertEquals("This Provider cannot be used until the Injector has been created.",
                  expected.getMessage());
            }
            bProviderReference.set(bProvider);

            MembersInjector<A> aMembersInjector = encounter.getMembersInjector(A.class);
            try {
              aMembersInjector.injectMembers(new A());
              fail();
            } catch (IllegalStateException expected) {
              assertEquals(
                  "This MembersInjector cannot be used until the Injector has been created.",
                  expected.getMessage());
            }
            aMembersInjectorReference.set(aMembersInjector);

            encounter.register(lookupsTester);
          }
        });

        // this ensures the type listener fires, and also the afterInjection() listener
        bind(C.class).asEagerSingleton();
      }
    });

    lookupsTester.afterInjection(null);
  }

  public void testLookupsPostCreate() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindListener(only(TypeLiteral.get(C.class)), new TypeListener() {
          public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
            assertNotNull(encounter.getProvider(B.class).get());

            A a = new A();
            encounter.getMembersInjector(A.class).injectMembers(a);
            assertNotNull(a.injector);
          }
        });
      }
    });
    
    injector.getInstance(C.class);
  }

  public void testMembersInjector() {
    final MembersInjector<D> membersInjector = new MembersInjector<D>() {
      public void injectMembers(D instance) {
        instance.userInjected++;
        assertEquals(instance.guiceInjected, instance.userInjected);
      }
    };

    final InjectionListener<D> injectionListener = new InjectionListener<D>() {
      public void afterInjection(D injectee) {
        assertTrue(injectee.userInjected > 0);
        injectee.listenersNotified++;
        assertEquals(injectee.guiceInjected, injectee.listenersNotified);
      }
    };

    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindListener(onlyAbcd, new TypeListener() {
          public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
            encounter.register((MembersInjector) membersInjector);
            encounter.register((InjectionListener) injectionListener);
          }
        });

        D boundThreeTimes = new D();
        bind(D.class).annotatedWith(named("i")).toInstance(boundThreeTimes);
        bind(D.class).annotatedWith(named("ii")).toInstance(boundThreeTimes);
        bind(D.class).annotatedWith(named("iii")).toInstance(boundThreeTimes);
      }
    });

    D boundThreeTimes = injector.getInstance(Key.get(D.class, named("iii")));
    boundThreeTimes.assertAllCounts(1);

    D getInstance = injector.getInstance(D.class);
    getInstance.assertAllCounts(1);

    D memberInjection = new D();
    injector.injectMembers(memberInjection);
    memberInjection.assertAllCounts(1);

    injector.injectMembers(memberInjection);
    injector.injectMembers(memberInjection);
    memberInjection.assertAllCounts(3);

    injector.getMembersInjector(D.class).injectMembers(memberInjection);
    memberInjection.assertAllCounts(4);
  }

  public void testMembersInjectorThrows() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindListener(onlyAbcd, new TypeListener() {
          public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
            encounter.register(failingMembersInjector);
          }
        });
        bind(B.class);
      }
    });

    try {
      injector.getInstance(A.class);
      fail();
    } catch (ProvisionException e) {
      assertContains(e.getMessage(),
          "1) Error injecting " + A.class.getName() + " using awkward.",
          "Reason: java.lang.ClassCastException: whoops, failure #1");
    }

    // second time through should be a new cause (#2)
    try {
      injector.getInstance(A.class);
      fail();
    } catch (ProvisionException e) {
      assertContains(e.getMessage(),
          "1) Error injecting " + A.class.getName() + " using awkward.",
          "Reason: java.lang.ClassCastException: whoops, failure #2");
    }

    // we should get errors for all types, but only on getInstance()
    Provider<B> bProvider = injector.getProvider(B.class);
    try {
      bProvider.get();
      fail();
    } catch (ProvisionException e) {
      assertContains(e.getMessage(),
          "1) Error injecting " + B.class.getName() + " using awkward.",
          "Reason: java.lang.ClassCastException: whoops, failure #3");
    }

    // non-injected types do not participate
    assertSame(Stage.DEVELOPMENT, injector.getInstance(Stage.class));
  }

  /**
   * We had a bug where we weren't notifying of types encountered for member injection when those
   * types had no members to be injected. Constructed types are always injected because they always
   * have at least one injection point: the class constructor.
   */
  public void testTypesWithNoInjectableMembersAreNotified() {
    final AtomicInteger notificationCount = new AtomicInteger();

    Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindListener(onlyAbcd, new TypeListener() {
          public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
            notificationCount.incrementAndGet();
          }
        });

        bind(C.class).toInstance(new C());
      }
    });

    assertEquals(1, notificationCount.get());
  }

  public void testEncounterCannotBeUsedAfterHearReturns() {
    final AtomicReference<TypeEncounter<?>> encounterReference = new AtomicReference<TypeEncounter<?>>();

    Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindListener(any(), new TypeListener() {
          public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
            encounterReference.set(encounter);
          }
        });

        bind(C.class);
      }
    });
    TypeEncounter<?> encounter = encounterReference.get();

    try {
      encounter.register(new InjectionListener<Object>() {
        public void afterInjection(Object injectee) {}
      });
      fail();
    } catch (IllegalStateException expected) {
    }

    /*if[AOP]*/
    try {
      encounter.bindInterceptor(any(), new org.aopalliance.intercept.MethodInterceptor() {
        public Object invoke(org.aopalliance.intercept.MethodInvocation methodInvocation)
            throws Throwable {
          return methodInvocation.proceed();
        }
      });
      fail();
    } catch (IllegalStateException expected) {
    }
    /*end[AOP]*/

    try {
      encounter.addError(new Exception());
      fail();
    } catch (IllegalStateException expected) {
    }

    try {
      encounter.getMembersInjector(A.class);
      fail();
    } catch (IllegalStateException expected) {
    }

    try {
      encounter.getProvider(B.class);
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  public void testAddErrors() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bindListener(Matchers.only(new TypeLiteral<Stage>() {}), new TypeListener() {
            public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
              encounter.addError("There was an error on %s", type);
              encounter.addError(new IllegalArgumentException("whoops!"));
              encounter.addError(new Message("And another problem"));
              encounter.addError(new IllegalStateException());
            }
          });
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "1) There was an error on com.google.inject.Stage",
          "2) An exception was caught and reported. Message: whoops!",
          "3) And another problem",
          "4) An exception was caught and reported. Message: null",
          "4 errors");
    }
  }

  // TODO: recursively accessing a lookup should fail

  static class A {
    @Inject Injector injector;
    @Inject Stage stage;
  }

  static class B {}

  public static class C {
    public String buzz() {
      return "buzz";
    }

    public String beep() {
      return "beep";
    }
  }

  static class D {
    int guiceInjected = 0;
    int userInjected = 0;
    int listenersNotified = 0;

    @Inject void guiceInjected() {
      guiceInjected++;
    }

    void assertAllCounts(int expected) {
      assertEquals(expected, guiceInjected);
      assertEquals(expected, userInjected);
      assertEquals(expected, listenersNotified);
    }
  }
}
