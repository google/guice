/*
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

import static com.google.inject.Asserts.assertContains;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.Assert.assertThrows;

import com.google.inject.matcher.Matchers;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import java.lang.annotation.Retention;
import junit.framework.TestCase;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class RequestInjectionTest extends TestCase {

  @Retention(RUNTIME)
  @BindingAnnotation
  @interface ForField {}

  @Retention(RUNTIME)
  @BindingAnnotation
  @interface ForMethod {}

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    HasInjections.staticField = 0;
    HasInjections.staticMethod = null;
    NeedsThing.injectedCount = 0;
  }

  public void testInjectMembers() {
    final HasInjections hi = new HasInjections();

    Guice.createInjector(
        new AbstractModule() {
          @Override
          protected void configure() {
            bindConstant().annotatedWith(ForMethod.class).to("test");
            bindConstant().annotatedWith(ForField.class).to(5);
            requestInjection(hi);
          }
        });

    assertEquals("test", hi.instanceMethod);
    assertEquals(5, hi.instanceField);
    assertNull(HasInjections.staticMethod);
    assertEquals(0, HasInjections.staticField);
  }

  public void testInjectStatics() throws CreationException {
    Guice.createInjector(
        new AbstractModule() {
          @Override
          protected void configure() {
            bindConstant().annotatedWith(ForMethod.class).to("test");
            bindConstant().annotatedWith(ForField.class).to(5);
            requestStaticInjection(HasInjections.class);
          }
        });

    assertEquals("test", HasInjections.staticMethod);
    assertEquals(5, HasInjections.staticField);
  }

  public void testInjectMembersAndStatics() {
    final HasInjections hi = new HasInjections();

    Guice.createInjector(
        new AbstractModule() {
          @Override
          protected void configure() {
            bindConstant().annotatedWith(ForMethod.class).to("test");
            bindConstant().annotatedWith(ForField.class).to(5);
            requestStaticInjection(HasInjections.class);
            requestInjection(hi);
          }
        });

    assertEquals("test", hi.instanceMethod);
    assertEquals(5, hi.instanceField);
    assertEquals("test", HasInjections.staticMethod);
    assertEquals(5, HasInjections.staticField);
  }

  public void testValidationErrorOnInjectedMembers() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              requestInjection(new NeedsRunnable());
            }
          });
      fail("Expected CreationException");
    } catch (CreationException expected) {
      assertContains(
          expected.getMessage(),
          "No implementation for Runnable was bound",
          "RequestInjectionTest$NeedsRunnable.runnable(RequestInjectionTest.java:");
    }
  }

  public void testInjectionErrorOnInjectedMembers() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              bind(Runnable.class)
                  .toProvider(
                      new Provider<Runnable>() {
                        @Override
                        public Runnable get() {
                          throw new UnsupportedOperationException();
                        }
                      });
              requestInjection(new NeedsRunnable());
            }
          });
    } catch (CreationException expected) {
      assertContains(
          expected.getMessage(),
          "[Guice/ErrorInCustomProvider]: UnsupportedOperationException",
          "at RequestInjectionTest$NeedsRunnable.runnable(RequestInjectionTest.java:",
          "for field runnable");
    }
  }

  public void testUserExceptionWhileInjectingInstance() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              requestInjection(new BlowsUpOnInject());
            }
          });
      fail();
    } catch (CreationException expected) {
      assertContains(
          expected.getMessage(),
          "1) [Guice/ErrorInjectingMethod]: UnsupportedOperationException: Pop",
          "at RequestInjectionTest$BlowsUpOnInject.injectInstance(RequestInjectionTest.java:");
    }
  }

  public void testUserExceptionWhileInjectingStatically() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              requestStaticInjection(BlowsUpOnInject.class);
            }
          });
      fail();
    } catch (CreationException expected) {
      assertContains(
          expected.getMessage(),
          "1) [Guice/ErrorInjectingMethod]: UnsupportedOperationException: Snap",
          "at RequestInjectionTest$BlowsUpOnInject.injectStatically(RequestInjectionTest.java:");
    }
  }

  static class NeedsRunnable {
    @Inject Runnable runnable;
  }

  static class HasInjections {

    @Inject @ForField static int staticField;
    @Inject @ForField int instanceField;

    static String staticMethod;
    String instanceMethod;

    @Inject
    static void setStaticMethod(@ForMethod String staticMethod) {
      HasInjections.staticMethod = staticMethod;
    }

    @Inject
    void setInstanceS(@ForMethod String instanceS) {
      this.instanceMethod = instanceS;
    }
  }

  static class BlowsUpOnInject {
    @Inject
    void injectInstance() {
      throw new UnsupportedOperationException("Pop");
    }

    @Inject
    static void injectStatically() {
      throw new UnsupportedOperationException("Snap");
    }
  }

  /*
   * Tests that initializables of the same instance don't clobber
   * membersInjectors in InitializableReference, so that they ultimately
   * can be requested in any order.
   */
  public void testEarlyInjectableReferencesWithSameIdentity() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                // Add a listener to trigger all toInstance bindings to get an Initializable.
                bindListener(
                    Matchers.any(),
                    new TypeListener() {
                      @Override
                      public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {}
                    });

                // Bind two different Keys to the IDENTITICAL object
                // ORDER MATTERS! We want the String binding to push out the Object one
                String fail = new String("better not fail!");
                bind(Object.class).toInstance(fail);
                bind(String.class).toInstance(fail);

                // Then try to inject those objects in a requestInjection,
                // letting us get into InjectableReference.get before it has
                // finished running through all its injections.
                // Each of these technically has its own InjectableReference internally.
                // ORDER MATTERS!.. because Object is injected first, that InjectableReference
                // attempts to process its members injector, but it wasn't initialized,
                // because String pushed it out of the queue!
                requestInjection(
                    new Object() {
                      @SuppressWarnings("unused")
                      @Inject
                      Object obj;

                      @SuppressWarnings("unused")
                      @Inject
                      String str;
                    });
              }
            });
  }

  static class NeedsThing<T> {
    static int injectedCount = 0;

    T thing;

    @Inject
    void register(T thing) {
      this.thing = thing;
      injectedCount++;
    }
  }

  private static class Type1Module extends AbstractModule {
    final NeedsThing<Integer> needer;

    Type1Module(NeedsThing<Integer> needer) {
      this.needer = needer;
    }

    @Override
    protected void configure() {
      requestInjection(new TypeLiteral<NeedsThing<Integer>>() {}, needer);
    }

    @Provides
    Integer provideInt() {
      return 5;
    }
  }

  private static class Type2Module extends AbstractModule {
    final NeedsThing<Integer> needer;

    Type2Module(NeedsThing<Integer> needer) {
      this.needer = needer;
    }

    @Override
    protected void configure() {
      requestInjection(new TypeLiteral<NeedsThing<? extends Number>>() {}, needer);
    }
  }

  public void testRequestInjectionWithParameterizedType() {
    NeedsThing<String> thing = new NeedsThing<>();
    Guice.createInjector(
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(String.class).toInstance("hi");
            requestInjection(new TypeLiteral<NeedsThing<String>>() {}, thing);
          }
        });

    assertEquals("hi", thing.thing);
    assertEquals(1, NeedsThing.injectedCount);
  }

  public void testMultipleInjectionRequestsWithDifferentTypes() {
    NeedsThing<Integer> needer = new NeedsThing<>();
    CreationException ex =
        assertThrows(
            CreationException.class,
            () -> Guice.createInjector(new Type1Module(needer), new Type2Module(needer)));
    assertContains(
        ex.getMessage(),
        "Cannot request injection on one instance with two different types. requestInjection was"
            + " already called for instance RequestInjectionTest$NeedsThing@"
            + System.identityHashCode(needer)
            + " at RequestInjectionTest$Type1Module.configure(",
        " (with type RequestInjectionTest$NeedsThing<Integer>),"
            + " which is different than type RequestInjectionTest$NeedsThing<? extends Number>",
        "at RequestInjectionTest$Type2Module.configure(");
  }

  public void testMultipleRequestInjectionsForSameTypeInjectsOnce() {
    NeedsThing<String> thing = new NeedsThing<>();
    Guice.createInjector(
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(String.class).toInstance("hi");
            requestInjection(new TypeLiteral<NeedsThing<String>>() {}, thing);
            requestInjection(new TypeLiteral<NeedsThing<String>>() {}, thing);
          }
        });

    assertEquals("hi", thing.thing);
    assertEquals(1, NeedsThing.injectedCount);
  }
}
