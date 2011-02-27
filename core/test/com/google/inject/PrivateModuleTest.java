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

package com.google.inject;

import static com.google.inject.Asserts.assertContains;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.inject.internal.util.ImmutableSet;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import static com.google.inject.name.Names.named;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.ExposedBinding;
import com.google.inject.spi.PrivateElements;
import com.google.inject.util.Types;

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
          public void configure() {
            bind(String.class).annotatedWith(named("b")).toInstance("i");

            bind(AB.class).annotatedWith(named("one")).to(AB.class);
            expose(AB.class).annotatedWith(named("one"));
          }
        });

        install(new PrivateModule() {
          public void configure() {
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
  
  public void testWithoutPrivateModules() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        PrivateBinder bindA = binder().newPrivateBinder();
        bindA.bind(String.class).annotatedWith(named("a")).toInstance("i");
        bindA.expose(String.class).annotatedWith(named("a"));
        bindA.bind(String.class).annotatedWith(named("c")).toInstance("private to A");

        PrivateBinder bindB = binder().newPrivateBinder();
        bindB.bind(String.class).annotatedWith(named("b")).toInstance("ii");
        bindB.expose(String.class).annotatedWith(named("b"));
        bindB.bind(String.class).annotatedWith(named("c")).toInstance("private to B");
      }
    });

    assertEquals("i", injector.getInstance(Key.get(String.class, named("a"))));
    assertEquals("ii", injector.getInstance(Key.get(String.class, named("b"))));
  }

  public void testMisplacedExposedAnnotation() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {}

        @Provides @Exposed
        String provideString() {
          return "i";
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(), "Cannot expose java.lang.String on a standard binder. ",
          "Exposed bindings are only applicable to private binders.",
          " at " + PrivateModuleTest.class.getName(), "provideString(PrivateModuleTest.java:");
    }
  }

  public void testMisplacedExposeStatement() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          ((PrivateBinder) binder()).expose(String.class).annotatedWith(named("a"));
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(), "Cannot expose java.lang.String on a standard binder. ",
          "Exposed bindings are only applicable to private binders.",
          " at " + PrivateModuleTest.class.getName(), "configure(PrivateModuleTest.java:");
    }
  }

  public void testPrivateModulesAndProvidesMethods() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        install(new PrivateModule() {
          public void configure() {
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
          public void configure() {}

          @Provides @Named("c") String providePrivateC() {
            return "private";
          }

          @Provides @Exposed @Named("d") String providePublicD() {
            return "ii";
          }
        });
      }
    });

    assertEquals("i", injector.getInstance(Key.get(String.class, named("a"))));

    try {
      injector.getInstance(Key.get(String.class, named("b")));
      fail();
    } catch(ConfigurationException expected) {
    }

    try {
      injector.getInstance(Key.get(String.class, named("c")));
      fail();
    } catch(ConfigurationException expected) {
    }

    assertEquals("ii", injector.getInstance(Key.get(String.class, named("d"))));
  }

  public void testCannotBindAKeyExportedByASibling() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          install(new PrivateModule() {
            public void configure() {
              bind(String.class).toInstance("public");
              expose(String.class);
            }
          });

          install(new PrivateModule() {
            public void configure() {
              bind(String.class).toInstance("private");
            }
          });
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "A binding to java.lang.String was already configured at ",
          getClass().getName(), ".configure(PrivateModuleTest.java:",
          " at " + getClass().getName(), ".configure(PrivateModuleTest.java:");
    }
  }

  public void testExposeButNoBind() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(String.class).annotatedWith(named("a")).toInstance("a");
          bind(String.class).annotatedWith(named("b")).toInstance("b");

          install(new PrivateModule() {
            public void configure() {
              expose(AB.class);
            }
          });
        }
      });
      fail("AB was exposed but not bound");
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "Could not expose() " + AB.class.getName() + ", it must be explicitly bound",
          ".configure(PrivateModuleTest.java:");
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
            public void configure() {
              bind(C.class);
            }
          },
          new PrivateModule() {
            public void configure() {
              bind(AB.class);
            }
          }
      );
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "1) No implementation for " + C.class.getName() + " was bound.",
          "at " + getClass().getName(), ".configure(PrivateModuleTest.java:",
          "2) No implementation for " + String.class.getName(), "Named(value=a) was bound.",
          "for field at " + AB.class.getName() + ".a(PrivateModuleTest.java:",
          "3) No implementation for " + String.class.getName(), "Named(value=b) was bound.",
          "for field at " + AB.class.getName() + ".b(PrivateModuleTest.java:",
          "3 errors");
    }
  }

  public void testNestedPrivateInjectors() {
    Injector injector = Guice.createInjector(new PrivateModule() {
      public void configure() {
        expose(String.class);

        install(new PrivateModule() {
          public void configure() {
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
      public void configure() {
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
      public void configure() {
        bind(String.class).annotatedWith(named("bound outer, exposed outer")).toInstance("boeo");
        expose(String.class).annotatedWith(named("bound outer, exposed outer"));
        bind(String.class).annotatedWith(named("bound outer, exposed none")).toInstance("boen");
        expose(String.class).annotatedWith(named("bound inner, exposed both"));

        install(new PrivateModule() {
          public void configure() {
            bind(String.class).annotatedWith(named("bound inner, exposed both")).toInstance("bieb");
            expose(String.class).annotatedWith(named("bound inner, exposed both"));
            bind(String.class).annotatedWith(named("bound inner, exposed none")).toInstance("bien");
          }
        });
      }
    });

    assertEquals("boeo",
        injector.getInstance(Key.get(String.class, named("bound outer, exposed outer"))));
    assertEquals("bieb",
        injector.getInstance(Key.get(String.class, named("bound inner, exposed both"))));

    try {
      injector.getInstance(Key.get(String.class, named("bound outer, exposed none")));
      fail();
    } catch (ConfigurationException expected) {
    }

    try {
      injector.getInstance(Key.get(String.class, named("bound inner, exposed none")));
      fail();
    } catch (ConfigurationException expected) {
    }
  }

  public void testDependenciesBetweenPrivateAndPublic() {
    Injector injector = Guice.createInjector(
        new PrivateModule() {
          protected void configure() {}

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
          protected void configure() {}

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
          protected void configure() {
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

  public void testSpiAccess() {
    Injector injector = Guice.createInjector(new PrivateModule() {
          public void configure() {
            bind(String.class).annotatedWith(named("a")).toInstance("private");
            bind(String.class).annotatedWith(named("b")).toInstance("exposed");
            expose(String.class).annotatedWith(named("b"));
          }
        });

    ExposedBinding<?> binding
        = (ExposedBinding<?>) injector.getBinding(Key.get(String.class, Names.named("b")));
    assertEquals(ImmutableSet.<Dependency<?>>of(Dependency.get(Key.get(Injector.class))),
        binding.getDependencies());
    PrivateElements privateElements = binding.getPrivateElements();
    assertEquals(ImmutableSet.<Key<?>>of(Key.get(String.class, named("b"))),
        privateElements.getExposedKeys());
    assertContains(privateElements.getExposedSource(Key.get(String.class, named("b"))).toString(),
        PrivateModuleTest.class.getName(), ".configure(PrivateModuleTest.java:");
    Injector privateInjector = privateElements.getInjector();
    assertEquals("private", privateInjector.getInstance(Key.get(String.class, Names.named("a"))));
  }
  
  public void testParentBindsSomethingInPrivate() {
    try {
      Guice.createInjector(new FailingModule());
      fail();
    } catch(CreationException expected) {
      assertEquals(1, expected.getErrorMessages().size());
      assertContains(expected.toString(),
          "Unable to create binding for java.util.List.",
          "It was already configured on one or more child injectors or private modules",
          "bound at " + FailingPrivateModule.class.getName() + ".configure(",
          "bound at " + SecondFailingPrivateModule.class.getName() + ".configure(",
          "If it was in a PrivateModule, did you forget to expose the binding?",
          "at " + FailingModule.class.getName() + ".configure(");
    }
  }
  
  public void testParentBindingToPrivateLinkedJitBinding() {
    Injector injector = Guice.createInjector(new ManyPrivateModules());
    try {
      injector.getBinding(Key.get(Types.providerOf(List.class)));
      fail();
    } catch(ConfigurationException expected) {
      assertEquals(1, expected.getErrorMessages().size());
      assertContains(expected.toString(),
          "Unable to create binding for com.google.inject.Provider<java.util.List>.",
          "It was already configured on one or more child injectors or private modules",
          "bound at " + FailingPrivateModule.class.getName() + ".configure(",
          "bound at " + SecondFailingPrivateModule.class.getName() + ".configure(",
          "If it was in a PrivateModule, did you forget to expose the binding?",
          "while locating com.google.inject.Provider<java.util.List>");
    }
  }
  
  public void testParentBindingToPrivateJitBinding() {
    Injector injector = Guice.createInjector(new ManyPrivateModules());
    try {
      injector.getBinding(PrivateFoo.class);
      fail();
    } catch(ConfigurationException expected) {
      assertEquals(1, expected.getErrorMessages().size());
      assertContains(expected.toString(),
          "Unable to create binding for " + PrivateFoo.class.getName(),
          "It was already configured on one or more child injectors or private modules",
          "(bound by a just-in-time binding)",
          "If it was in a PrivateModule, did you forget to expose the binding?",
          "while locating " + PrivateFoo.class.getName());
    }
  }
  
  private static class FailingModule extends AbstractModule {
    @Override
    protected void configure() {
      bind(Collection.class).to(List.class);
      install(new ManyPrivateModules());
    }
  }

  private static class ManyPrivateModules extends AbstractModule {
    @Override
    protected void configure() {
      // make sure duplicate sources are collapsed
      install(new FailingPrivateModule());
      install(new FailingPrivateModule());
      // but additional sources are listed
      install(new SecondFailingPrivateModule());
    }
  }

  private static class FailingPrivateModule extends PrivateModule {
    @Override
    protected void configure() {
      bind(List.class).toInstance(new ArrayList());
      
      // Add the Provider<List> binding, created just-in-time,
      // to make sure our linked JIT bindings have the correct source.
      getProvider(Key.get(Types.providerOf(List.class)));
      
      // Request a JIT binding for PrivateFoo, which can only
      // be created in the private module because it depends
      // on List.
      getProvider(PrivateFoo.class);
    }
  }
  
  /** A second class, so we can see another name in the source list. */
  private static class SecondFailingPrivateModule extends PrivateModule {
    @Override
    protected void configure() {
      bind(List.class).toInstance(new ArrayList());
      
      // Add the Provider<List> binding, created just-in-time,
      // to make sure our linked JIT bindings have the correct source.
      getProvider(Key.get(Types.providerOf(List.class)));
      
      // Request a JIT binding for PrivateFoo, which can only
      // be created in the private module because it depends
      // on List.
      getProvider(PrivateFoo.class);
    }
  }
  
  private static class PrivateFoo {
    @Inject List list;
  } 
}
