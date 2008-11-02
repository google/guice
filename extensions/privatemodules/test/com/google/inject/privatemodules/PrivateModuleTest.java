/**
 * Copyright (C) 2008 Google Inc.
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

package com.google.inject.privatemodules;

import com.google.inject.AbstractModule;
import static com.google.inject.Asserts.assertContains;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import com.google.inject.name.Named;
import static com.google.inject.name.Names.named;
import junit.framework.TestCase;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class PrivateModuleTest extends TestCase {

  public void testBasicUsage() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(String.class).annotatedWith(named("a")).toInstance("public");

        install(new PrivateModule() {
          public void configurePrivateBindings() {
            bind(String.class).annotatedWith(named("b")).toInstance("i");

            bind(AB.class).annotatedWith(named("one")).to(AB.class);
            expose(AB.class).annotatedWith(named("one"));
          }
        });

        install(new PrivateModule() {
          public void configurePrivateBindings() {
            bind(String.class).annotatedWith(named("b")).toInstance("ii");

            bind(AB.class).annotatedWith(named("two")).to(AB.class);
            expose(AB.class).annotatedWith(named("two"));
          }
        });
      }
    });

    AB ab1 = injector.getInstance(Key.get(AB.class, named("one")));
    assertEquals("public", ab1.a);
    assertEquals("i", ab1.b);

    AB ab2 = injector.getInstance(Key.get(AB.class, named("two")));
    assertEquals("public", ab2.a);
    assertEquals("ii", ab2.b);
  }

  public void testPrivateModulesAndProvidesMethods() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        install(new PrivateModule() {
          public void configurePrivateBindings() {
            expose(String.class).annotatedWith(named("a"));
          }

          @Provides @Named("a") String providePublicA() {
            return "i";
          }

          @Provides @Named("b") String providePrivateB() {
            return "private";
          }
        });

        install(new PrivateModule() {
          public void configurePrivateBindings() {}

          @Provides @Named("a") String providePrivateA() {
            return "private";
          }

          @Provides @Exposed @Named("b") String providePublicB() {
            return "ii";
          }
        });
      }
    });

    assertEquals("i", injector.getInstance(Key.get(String.class, named("a"))));
    assertEquals("ii", injector.getInstance(Key.get(String.class, named("b"))));
  }

  public void testCannotBindAKeyExportedByASibling() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          install(new PrivateModule() {
            public void configurePrivateBindings() {
              bind(String.class).toInstance("public");
              expose(String.class);
            }
          });

          install(new PrivateModule() {
            public void configurePrivateBindings() {
              bind(String.class).toInstance("private");
            }
          });
        }
      });
      fail("KNOWN ISSUE: Binding to 'private' should conflict with binding to 'public'");
    } catch (CreationException expected) {
      assertContains(expected.getMessage(), "Cannot bind String");
    }
  }

  public void testExposeButNoBind() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(String.class).annotatedWith(named("a")).toInstance("a");
          bind(String.class).annotatedWith(named("b")).toInstance("b");

          install(new PrivateModule() {
            public void configurePrivateBindings() {
              expose(AB.class);
            }
          });
        }
      });
      fail("AB was exposed but not bound");
    } catch (CreationException expected) {
      assertContains(expected.getMessage(), "Could not expose() at ",
          PrivateModuleTest.class.getName(), ".configurePrivateBindings(PrivateModuleTest.java:",
          Key.get(AB.class).toString(), " must be explicitly bound.");
    }
  }

  /**
   * Ensure that when we've got errors in different private modules, Guice presents all errors
   * in a unified message.
   */
  public void testMessagesFromPrivateModulesAreNicelyIntegrated() {
    try {
      Guice.createInjector(
          new PrivateModule() {
            public void configurePrivateBindings() {
              bind(C.class);
            }
          },
          new PrivateModule() {
            public void configurePrivateBindings() {
              bind(AB.class);
            }
          }
      );
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "1) No implementation for " + C.class.getName() + " was bound.",
          "at " + getClass().getName(), ".configurePrivateBindings(PrivateModuleTest.java:",
          "2) No implementation for " + String.class.getName(), "Named(value=a) was bound.",
          "for field at " + AB.class.getName() + ".a(PrivateModuleTest.java:",
          "3) No implementation for " + String.class.getName(), "Named(value=b) was bound.",
          "for field at " + AB.class.getName() + ".b(PrivateModuleTest.java:",
          "3 errors");
    }
  }

  public void testNestedPrivateInjectors() {
    Injector injector = Guice.createInjector(new PrivateModule() {
      public void configurePrivateBindings() {
        expose(String.class);

        install(new PrivateModule() {
          public void configurePrivateBindings() {
            bind(String.class).toInstance("nested");
            expose(String.class);
          }
        });
      }
    });

    assertEquals("nested", injector.getInstance(String.class));
  }

  public void testInstallingRegularModulesFromPrivateModules() {
    Injector injector = Guice.createInjector(new PrivateModule() {
      public void configurePrivateBindings() {
        expose(String.class);

        install(new AbstractModule() {
          protected void configure() {
            bind(String.class).toInstance("nested");
          }
        });
      }
    });

    assertEquals("nested", injector.getInstance(String.class));
  }

  public void testNestedPrivateModulesWithSomeKeysUnexposed() {
    Injector injector = Guice.createInjector(new PrivateModule() {
      public void configurePrivateBindings() {
        bind(String.class).annotatedWith(named("bound outer, exposed outer")).toInstance("boeo");
        expose(String.class).annotatedWith(named("bound outer, exposed outer"));
        bind(String.class).annotatedWith(named("bound outer, exposed none")).toInstance("boen");
        expose(String.class).annotatedWith(named("bound inner, exposed both"));

        install(new PrivateModule() {
          public void configurePrivateBindings() {
            bind(String.class).annotatedWith(named("bound inner, exposed both")).toInstance("bieb");
            expose(String.class).annotatedWith(named("bound inner, exposed both"));
            bind(String.class).annotatedWith(named("bound inner, exposed none")).toInstance("bien");
          }
        });
      }
    });

    assertEquals("boeo", injector.getInstance(
        Key.get(String.class, named("bound outer, exposed outer"))));
    assertEquals("bieb", injector.getInstance(
        Key.get(String.class, named("bound inner, exposed both"))));

    try {
      injector.getInstance(Key.get(String.class, named("bound outer, exposed none")));
      fail();
    } catch (ProvisionException expected) {
    }

    try {
      injector.getInstance(Key.get(String.class, named("bound inner, exposed none")));
      fail();
    } catch (ProvisionException expected) {
    }
  }

  public void testDependenciesBetweenPrivateAndPublic() {
    Injector injector = Guice.createInjector(
        new PrivateModule() {
          protected void configurePrivateBindings() {}

          @Provides @Exposed @Named("a") String provideA() {
            return "A";
          }

          @Provides @Exposed @Named("abc") String provideAbc(@Named("ab") String ab) {
            return ab + "C";
          }
        },
        new AbstractModule() {
          protected void configure() {}

          @Provides @Named("ab") String provideAb(@Named("a") String a) {
            return a + "B";
          }

          @Provides @Named("abcd") String provideAbcd(@Named("abc") String abc) {
            return abc + "D";
          }
        }
    );

    assertEquals("ABCD", injector.getInstance(Key.get(String.class, named("abcd"))));
  }

  public void testDependenciesBetweenPrivateAndPublicWithPublicEagerSingleton() {
    Injector injector = Guice.createInjector(
        new PrivateModule() {
          protected void configurePrivateBindings() {}

          @Provides @Exposed @Named("a") String provideA() {
            return "A";
          }

          @Provides @Exposed @Named("abc") String provideAbc(@Named("ab") String ab) {
            return ab + "C";
          }
        },
        new AbstractModule() {
          protected void configure() {
            bind(String.class).annotatedWith(named("abcde")).toProvider(new Provider<String>() {
              @Inject @Named("abcd") String abcd;

              public String get() {
                return abcd + "E";
              }
            }).asEagerSingleton();
          }

          @Provides @Named("ab") String provideAb(@Named("a") String a) {
            return a + "B";
          }

          @Provides @Named("abcd") String provideAbcd(@Named("abc") String abc) {
            return abc + "D";
          }
        }
    );

    assertEquals("ABCDE", injector.getInstance(Key.get(String.class, named("abcde"))));
  }

  public void testDependenciesBetweenPrivateAndPublicWithPrivateEagerSingleton() {
    Injector injector = Guice.createInjector(
        new AbstractModule() {
          protected void configure() {}

          @Provides @Named("ab") String provideAb(@Named("a") String a) {
            return a + "B";
          }

          @Provides @Named("abcd") String provideAbcd(@Named("abc") String abc) {
            return abc + "D";
          }
        },
        new PrivateModule() {
          protected void configurePrivateBindings() {
            bind(String.class).annotatedWith(named("abcde")).toProvider(new Provider<String>() {
              @Inject @Named("abcd") String abcd;

              public String get() {
                return abcd + "E";
              }
            }).asEagerSingleton();
            expose(String.class).annotatedWith(named("abcde"));
          }

          @Provides @Exposed @Named("a") String provideA() {
            return "A";
          }

          @Provides @Exposed @Named("abc") String provideAbc(@Named("ab") String ab) {
            return ab + "C";
          }
        }
    );

    assertEquals("ABCDE", injector.getInstance(Key.get(String.class, named("abcde"))));
  }

  static class AB {
    @Inject @Named("a") String a;
    @Inject @Named("b") String b;
  }

  interface C {}
}
