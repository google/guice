/*
 * Copyright (C) 2015 Google Inc.
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

package com.google.inject.spi;

import static com.google.common.truth.Truth.assertThat;
import static com.google.inject.name.Names.named;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.ConfigurationException;
import com.google.inject.CreationException;
import com.google.inject.Exposed;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.PrivateModule;
import com.google.inject.Provides;
import com.google.inject.internal.ProviderMethodsModule;
import com.google.inject.internal.util.StackTraceElements;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Set;
import javax.inject.Qualifier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ModuleAnnotatedMethodScanner} usage. */
@RunWith(JUnit4.class)
public class ModuleAnnotatedMethodScannerTest {

  @Test
  public void scanning() throws Exception {
    Module module =
        new AbstractModule() {

          @TestProvides
          @Named("foo")
          String foo() {
            return "foo";
          }

          @TestProvides
          @Named("foo2")
          String foo2() {
            return "foo2";
          }
        };
    Injector injector = Guice.createInjector(module, scannerModule(new NamedMunger()));

    // assert no bindings named "foo" or "foo2" exist -- they were munged.
    assertMungedBinding(injector, String.class, "foo", "foo");
    assertMungedBinding(injector, String.class, "foo2", "foo2");

    Binding<String> fooBinding = injector.getBinding(Key.get(String.class, named("foo-munged")));
    Binding<String> foo2Binding = injector.getBinding(Key.get(String.class, named("foo2-munged")));
    // Validate the provider has a sane toString
    assertThat(methodName(TestProvides.class, "foo", module))
        .isEqualTo(fooBinding.getProvider().toString());
    assertThat(methodName(TestProvides.class, "foo2", module))
        .isEqualTo(foo2Binding.getProvider().toString());
  }

  @Test
  public void skipSources() throws Exception {
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            binder()
                .skipSources(getClass())
                .install(
                    new AbstractModule() {

                      @TestProvides
                      @Named("foo")
                      String foo() {
                        return "foo";
                      }
                    });
          }
        };
    Injector injector = Guice.createInjector(module, scannerModule(new NamedMunger()));
    assertMungedBinding(injector, String.class, "foo", "foo");
  }

  @Test
  public void withSource() throws Exception {
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            binder()
                .withSource("source")
                .install(
                    new AbstractModule() {

                      @TestProvides
                      @Named("foo")
                      String foo() {
                        return "foo";
                      }
                    });
          }
        };
    Injector injector = Guice.createInjector(module, scannerModule(new NamedMunger()));
    assertMungedBinding(injector, String.class, "foo", "foo");
  }

  @Test
  public void moreThanOneClaimedAnnotationFails() throws Exception {
    Module module =
        new AbstractModule() {

          @TestProvides
          @TestProvides2
          String foo() {
            return "foo";
          }
        };

    CreationException creationException =
        assertThatInjectorCreationFails(module, scannerModule(new NamedMunger()));

    assertThat(creationException.getErrorMessages()).hasSize(1);
    assertThat(creationException)
        .hasMessageThat()
        .contains(
            "More than one annotation claimed by NamedMunger on method "
                + module.getClass().getName()
                + ".foo(). Methods can only have "
                + "one annotation claimed per scanner.");
  }

  private String methodName(Class<? extends Annotation> annotation, String method, Object container)
      throws Exception {
    return "@"
        + annotation.getName()
        + " "
        + StackTraceElements.forMember(container.getClass().getDeclaredMethod(method));
  }

  @Documented
  @Target(METHOD)
  @Retention(RUNTIME)
  private @interface TestProvides {}

  @Documented
  @Target(METHOD)
  @Retention(RUNTIME)
  private @interface TestProvides2 {}

  private static class NamedMunger extends ModuleAnnotatedMethodScanner {
    @Override
    public String toString() {
      return "NamedMunger";
    }

    @Override
    public Set<? extends Class<? extends Annotation>> annotationClasses() {
      return ImmutableSet.of(TestProvides.class, TestProvides2.class);
    }

    @Override
    public <T> Key<T> prepareMethod(
        Binder binder, Annotation annotation, Key<T> key, InjectionPoint injectionPoint) {
      return key.withAnnotation(Names.named(((Named) key.getAnnotation()).value() + "-munged"));
    }
  }

  private void assertMungedBinding(
      Injector injector, Class<?> clazz, String originalName, Object expectedValue) {
    assertThat(injector.getExistingBinding(Key.get(clazz, named(originalName)))).isNull();
    Binding<?> fooBinding = injector.getBinding(Key.get(clazz, named(originalName + "-munged")));
    assertThat(fooBinding.getProvider().get()).isEqualTo(expectedValue);
  }

  @Test
  public void failingScanner() {
    CreationException creationException =
        assertThatInjectorCreationFails(new SomeModule(), scannerModule(new FailingScanner()));
    Message m = Iterables.getOnlyElement(creationException.getErrorMessages());
    assertThat(m.getMessage())
        .isEqualTo("An exception was caught and reported. Message: Failing in the scanner.");
    assertThat(creationException).hasCauseThat().isInstanceOf(IllegalStateException.class);
    ElementSource source = (ElementSource) Iterables.getOnlyElement(m.getSources());
    assertThat(SomeModule.class.getName())
        .isEqualTo(Iterables.getOnlyElement(source.getModuleClassNames()));
    assertThat(String.class.getName() + " " + SomeModule.class.getName() + ".aString()")
        .isEqualTo(source.toString());
  }

  public static class FailingScanner extends ModuleAnnotatedMethodScanner {
    @Override
    public Set<? extends Class<? extends Annotation>> annotationClasses() {
      return ImmutableSet.of(TestProvides.class);
    }

    @Override
    public <T> Key<T> prepareMethod(
        Binder binder, Annotation rawAnnotation, Key<T> key, InjectionPoint injectionPoint) {
      throw new IllegalStateException("Failing in the scanner.");
    }
  }

  static class SomeModule extends AbstractModule {
    @TestProvides
    String aString() {
      return "Foo";
    }
  }

  @Test
  public void childInjectorInheritsScanner() {
    Injector parent = Guice.createInjector(scannerModule(new NamedMunger()));
    Injector child =
        parent.createChildInjector(
            new AbstractModule() {

              @TestProvides
              @Named("foo")
              String foo() {
                return "foo";
              }
            });
    assertMungedBinding(child, String.class, "foo", "foo");
  }

  @Test
  public void childInjectorScannersDontImpactSiblings() {
    Module module =
        new AbstractModule() {

          @TestProvides
          @Named("foo")
          String foo() {
            return "foo";
          }
        };
    Injector parent = Guice.createInjector();
    Injector child = parent.createChildInjector(scannerModule(new NamedMunger()), module);
    assertMungedBinding(child, String.class, "foo", "foo");

    // no foo nor foo-munged in sibling, since scanner never saw it.
    Injector sibling = parent.createChildInjector(module);
    assertThat(sibling.getExistingBinding(Key.get(String.class, named("foo")))).isNull();
    assertThat(sibling.getExistingBinding(Key.get(String.class, named("foo-munged")))).isNull();
  }

  @Test
  public void privateModuleInheritScanner_usingPrivateModule() {
    Injector injector =
        Guice.createInjector(
            scannerModule(new NamedMunger()),
            new PrivateModule() {
              @Override
              protected void configure() {}

              @Exposed
              @TestProvides
              @Named("foo")
              String foo() {
                return "foo";
              }
            });
    assertMungedBinding(injector, String.class, "foo", "foo");
  }

  @Test
  public void privateModuleInheritsScanner_scannerInstalledAfterPrivateModule() {
    Injector injector =
        Guice.createInjector(
            new PrivateModule() {
              @Override
              protected void configure() {}

              @Exposed
              @TestProvides
              @Named("foo")
              String foo() {
                return "foo";
              }
            },
            // Scanner installed after private module.
            scannerModule(new NamedMunger()));
    assertMungedBinding(injector, String.class, "foo", "foo");
  }

  @Test
  public void privateModule_skipSourcesWithinPrivateModule() {
    Injector injector =
        Guice.createInjector(
            scannerModule(new NamedMunger()),
            new PrivateModule() {
              @Override
              protected void configure() {
                binder()
                    .skipSources(getClass())
                    .install(
                        new AbstractModule() {

                          @Exposed
                          @TestProvides
                          @Named("foo")
                          String foo() {
                            return "foo";
                          }
                        });
              }
            });
    assertMungedBinding(injector, String.class, "foo", "foo");
  }

  @Test
  public void privateModule_skipSourcesForPrivateModule() {
    Injector injector =
        Guice.createInjector(
            scannerModule(new NamedMunger()),
            new AbstractModule() {
              @Override
              protected void configure() {
                binder()
                    .skipSources(getClass())
                    .install(
                        new PrivateModule() {
                          @Override
                          protected void configure() {}

                          @Exposed
                          @TestProvides
                          @Named("foo")
                          String foo() {
                            return "foo";
                          }
                        });
              }
            });
    assertMungedBinding(injector, String.class, "foo", "foo");
  }

  @Test
  public void privateModuleInheritScanner_usingPrivateBinder() {
    Injector injector =
        Guice.createInjector(
            scannerModule(new NamedMunger()),
            new AbstractModule() {
              @Override
              protected void configure() {
                binder()
                    .newPrivateBinder()
                    .install(
                        new AbstractModule() {

                          @Exposed
                          @TestProvides
                          @Named("foo")
                          String foo() {
                            return "foo";
                          }
                        });
              }
            });
    assertMungedBinding(injector, String.class, "foo", "foo");
  }

  @Test
  public void privateModuleInheritScanner_skipSourcesFromPrivateBinder() {
    Injector injector =
        Guice.createInjector(
            scannerModule(new NamedMunger()),
            new AbstractModule() {
              @Override
              protected void configure() {
                binder()
                    .newPrivateBinder()
                    .skipSources(getClass())
                    .install(
                        new AbstractModule() {

                          @Exposed
                          @TestProvides
                          @Named("foo")
                          String foo() {
                            return "foo";
                          }
                        });
              }
            });
    assertMungedBinding(injector, String.class, "foo", "foo");
  }

  @Test
  public void privateModuleInheritScanner_skipSourcesFromPrivateBinder2() {
    Injector injector =
        Guice.createInjector(
            scannerModule(new NamedMunger()),
            new AbstractModule() {
              @Override
              protected void configure() {
                binder()
                    .skipSources(getClass())
                    .newPrivateBinder()
                    .install(
                        new AbstractModule() {

                          @Exposed
                          @TestProvides
                          @Named("foo")
                          String foo() {
                            return "foo";
                          }
                        });
              }
            });
    assertMungedBinding(injector, String.class, "foo", "foo");
  }

  @Test
  public void privateModuleScannersDontImpactSiblings_usingPrivateModule() {
    Injector injector =
        Guice.createInjector(
            new PrivateModule() {
              @Override
              protected void configure() {
                install(scannerModule(new NamedMunger()));
              }

              @Exposed
              @TestProvides
              @Named("foo")
              String foo() {
                return "foo";
              }
            },
            new PrivateModule() {
              @Override
              protected void configure() {}

              // ignored! (because the scanner doesn't run over this module)
              @Exposed
              @TestProvides
              @Named("foo")
              String foo() {
                return "foo";
              }
            });
    assertMungedBinding(injector, String.class, "foo", "foo");
  }

  @Test
  public void privateModuleScannersDontImpactSiblings_usingPrivateBinder() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                binder()
                    .newPrivateBinder()
                    .install(
                        new AbstractModule() {
                          @Override
                          protected void configure() {
                            install(scannerModule(new NamedMunger()));
                          }

                          @Exposed
                          @TestProvides
                          @Named("foo")
                          String foo() {
                            return "foo";
                          }
                        });
              }
            },
            new AbstractModule() {
              @Override
              protected void configure() {
                binder()
                    .newPrivateBinder()
                    .install(
                        new AbstractModule() {

                          // ignored! (because the scanner doesn't run over this module)
                          @Exposed
                          @TestProvides
                          @Named("foo")
                          String foo() {
                            return "foo";
                          }
                        });
              }
            });
    assertMungedBinding(injector, String.class, "foo", "foo");
  }

  @Test
  public void privateModuleWithinPrivateModule() {
    Injector injector =
        Guice.createInjector(
            scannerModule(new NamedMunger()),
            new PrivateModule() {
              @Override
              protected void configure() {
                expose(Key.get(String.class, named("foo-munged")));
                install(
                    new PrivateModule() {
                      @Override
                      protected void configure() {}

                      @Exposed
                      @TestProvides
                      @Named("foo")
                      String foo() {
                        return "foo";
                      }
                    });
              }
            });
    assertMungedBinding(injector, String.class, "foo", "foo");
  }

  @Test
  public void privateModuleWithinPrivateModule_parentScannerInheritedIfInstalledAfter() {
    Injector injector =
        Guice.createInjector(
            new PrivateModule() {
              @Override
              protected void configure() {
                expose(Key.get(String.class, named("foo-munged")));
                install(
                    new PrivateModule() {
                      @Override
                      protected void configure() {}

                      @Exposed
                      @TestProvides
                      @Named("foo")
                      String foo() {
                        return "foo";
                      }
                    });
              }
            },
            scannerModule(new NamedMunger()));
    assertMungedBinding(injector, String.class, "foo", "foo");
  }

  @Test
  public void abstractMethodsAreScannedForOverrides() {
    abstract class Superclass {
      @TestProvides
      abstract boolean abstractTest();
    }

    abstract class Subclass extends Superclass {
      @TestProvides
      @Override
      abstract boolean abstractTest();
    }

    ModuleAnnotatedMethodScanner testScanner =
        new ModuleAnnotatedMethodScanner() {
          @Override
          public Set<? extends Class<? extends Annotation>> annotationClasses() {
            return ImmutableSet.of(TestProvides.class);
          }

          @Override
          public <T> Key<T> prepareMethod(
              Binder binder, Annotation annotation, Key<T> key, InjectionPoint injectionPoint) {
            return null;
          }
        };
    CreationException creationException =
        assertThatInjectorCreationFails(
            ProviderMethodsModule.forModule(Subclass.class, testScanner));
    assertThat(creationException)
        .hasMessageThat()
        .contains(
            String.format(
                "Overriding @%s methods is not allowed", TestProvides.class.getCanonicalName()));
  }

  static class Superclass {
    @TestProvides
    boolean booleanTest() {
      return true;
    }
  }

  static class Subclass extends Superclass {
    @TestProvides
    @Override
    boolean booleanTest() {
      return true;
    }
  }

  static class IgnoringScanner extends ModuleAnnotatedMethodScanner {
    private final Class<?> classToIgnore;
    private int ignoredCounter = 0;

    IgnoringScanner(Class<?> classToIgnore) {
      this.classToIgnore = classToIgnore;
    }

    @Override
    public Set<? extends Class<? extends Annotation>> annotationClasses() {
      return ImmutableSet.of(TestProvides.class);
    }

    @Override
    public <T> Key<T> prepareMethod(
        Binder binder, Annotation annotation, Key<T> key, InjectionPoint injectionPoint) {
      Method method = (Method) injectionPoint.getMember();
      if (method.getDeclaringClass().equals(classToIgnore)) {
        ignoredCounter++;
        return null;
      }
      return key;
    }

    int ignoredCounter() {
      return ignoredCounter;
    }
  }

  @Test
  public void ignoreMethodsScannedForOverridesSubclass() {
    IgnoringScanner scanner = new IgnoringScanner(Subclass.class);

    CreationException creationException =
        assertThatInjectorCreationFails(ProviderMethodsModule.forModule(new Subclass(), scanner));

    assertThat(creationException)
        .hasMessageThat()
        .contains(
            String.format(
                "Overriding @%s methods is not allowed", TestProvides.class.getCanonicalName()));
    assertThat(scanner.ignoredCounter()).isEqualTo(1); // checking that there was a method ignored.
  }

  @Test
  public void ignoreMethodsScannedForOverridesSuperclass() {
    IgnoringScanner scanner = new IgnoringScanner(Superclass.class);

    CreationException creationException =
        assertThatInjectorCreationFails(ProviderMethodsModule.forModule(new Subclass(), scanner));

    assertThat(creationException)
        .hasMessageThat()
        .contains(
            String.format(
                "Overriding @%s methods is not allowed", TestProvides.class.getCanonicalName()));
    assertThat(scanner.ignoredCounter()).isEqualTo(1); // checking that there was a method ignored.
  }

  static class TestScanner extends ModuleAnnotatedMethodScanner {
    ImmutableSet<Class<? extends Annotation>> annotations;

    TestScanner(Class<? extends Annotation>... annotations) {
      this.annotations = ImmutableSet.copyOf(annotations);
    }

    @Override
    public Set<? extends Class<? extends Annotation>> annotationClasses() {
      return annotations;
    }

    @Override
    public <T> Key<T> prepareMethod(
        Binder binder, Annotation annotation, Key<T> key, InjectionPoint injectionPoint) {
      return key;
    }
  }

  @Test
  public void ignoreMethods() {
    class ModuleWithMethodsToIgnore {
      @TestProvides
      boolean booleanTest() {
        return true;
      }

      @TestProvides
      int ignore() {
        return 0;
      }
    }
    ModuleAnnotatedMethodScanner filteringScanner =
        new TestScanner(TestProvides.class) {
          @Override
          public <T> Key<T> prepareMethod(
              Binder binder, Annotation annotation, Key<T> key, InjectionPoint injectionPoint) {
            Method method = (Method) injectionPoint.getMember();
            if (method.getName().equals("ignore")) {
              return null;
            }
            return key;
          }
        };
    Injector filteredInjector =
        Guice.createInjector(
            ProviderMethodsModule.forModule(new ModuleWithMethodsToIgnore(), filteringScanner));
    assertThat(filteredInjector.getInstance(Key.get(Boolean.class))).isTrue();
    assertThrows(ConfigurationException.class, () -> filteredInjector.getInstance(Integer.class));
    Injector unfilteredInjector =
        Guice.createInjector(
            ProviderMethodsModule.forModule(
                new ModuleWithMethodsToIgnore(), new TestScanner(TestProvides.class)));
    assertThat(unfilteredInjector.getInstance(Key.get(Boolean.class))).isTrue();
    assertThat(unfilteredInjector.getInstance(Integer.class)).isEqualTo(0);
  }

  @Test
  public void scannerCantRegisterScanner() {
    ModuleAnnotatedMethodScanner scannerRegisteringScanner =
        new TestScanner(TestProvides.class) {
          @Override
          public <T> Key<T> prepareMethod(
              Binder binder, Annotation annotation, Key<T> key, InjectionPoint injectionPoint) {
            binder.scanModulesForAnnotatedMethods(new TestScanner(TestProvides2.class));
            return key;
          }
        };

    CreationException creationException =
        assertThatInjectorCreationFails(
            scannerModule(scannerRegisteringScanner),
            new AbstractModule() {
              @TestProvides
              boolean bogus() {
                return true;
              }
            });

    assertThat(creationException)
        .hasMessageThat()
        .contains("Scanners are not allowed to register other scanners");
  }

  @Test
  public void scannerCantInstallModuleWithCustomProvidesMethods() {
    ModuleAnnotatedMethodScanner scannerInstallingScannableModule =
        new TestScanner(TestProvides.class) {
          @Override
          public <T> Key<T> prepareMethod(
              Binder binder, Annotation annotation, Key<T> key, InjectionPoint injectionPoint) {
            binder.install(
                new AbstractModule() {
                  @TestProvides2
                  int bogus() {
                    return 0;
                  }
                });
            return key;
          }
        };

    CreationException creationException =
        assertThatInjectorCreationFails(
            scannerModule(scannerInstallingScannableModule),
            scannerModule(new TestScanner(TestProvides2.class)),
            new AbstractModule() {
              @TestProvides
              boolean bogus() {
                return true;
              }
            });

    assertThat(creationException)
        .hasMessageThat()
        .contains(
            "Installing modules with custom provides methods from a ModuleAnnotatedMethodScanner"
                + " is not supported");
  }

  @Test
  public void scannerCantInstallPrivateModuleWithCustomProvidesMethods() {
    ModuleAnnotatedMethodScanner scannerInstallingScannablePrivateModule =
        new TestScanner(TestProvides.class) {
          @Override
          public <T> Key<T> prepareMethod(
              Binder binder, Annotation annotation, Key<T> key, InjectionPoint injectionPoint) {
            binder.install(
                new PrivateModule() {
                  @Override
                  protected void configure() {}

                  @TestProvides2
                  int bogus() {
                    return 0;
                  }
                });
            return key;
          }
        };

    CreationException creationException =
        assertThatInjectorCreationFails(
            scannerModule(scannerInstallingScannablePrivateModule),
            scannerModule(new TestScanner(TestProvides2.class)),
            new AbstractModule() {
              @TestProvides
              boolean bogus() {
                return true;
              }
            });

    assertThat(creationException)
        .hasMessageThat()
        .contains(
            "Installing modules with custom provides methods from a ModuleAnnotatedMethodScanner"
                + " is not supported");
  }

  @Test
  public void scannerCanInstallModuleWithRegularProvidesMethods() {
    ModuleAnnotatedMethodScanner scanner =
        new TestScanner(TestProvides.class) {
          @Override
          public <T> Key<T> prepareMethod(
              Binder binder, Annotation annotation, Key<T> key, InjectionPoint injectionPoint) {
            binder.install(
                new AbstractModule() {
                  @Provides
                  int provideAnswer() {
                    return 42;
                  }
                });
            return key;
          }
        };

    Injector injector =
        Guice.createInjector(
            scannerModule(scanner),
            new AbstractModule() {
              @TestProvides
              boolean bogus() {
                return true;
              }
            });

    assertThat(injector.getInstance(Integer.class)).isEqualTo(42);
  }

  CreationException assertThatInjectorCreationFails(Module... modules) {
    return assertThrows(CreationException.class, () -> Guice.createInjector(modules));
  }

  @Test
  public void scannerSourceCorrectForNonGuiceModule() {
    class NonGuiceModule {
      @TestProvides
      boolean booleanTest() {
        return true;
      }
    }
    TestScanner testScanner = new TestScanner(TestProvides.class);

    Injector injector =
        Guice.createInjector(ProviderMethodsModule.forModule(new NonGuiceModule(), testScanner));

    assertThat(getSourceScanner(injector.getBinding(Boolean.class))).isEqualTo(testScanner);
  }

  @Qualifier
  @Retention(RUNTIME)
  @interface Foo {}

  @Test
  public void scannerSourceCorrectForGuiceModule() {
    Module module =
        new AbstractModule() {
          @TestProvides
          @Foo
          boolean booleanTest() {
            return true;
          }

          @Provides
          String stringTest() {
            return "";
          }

          @Override
          protected void configure() {
            bind(Long.class).toInstance(1L);
          }
        };
    TestScanner testScanner = new TestScanner(TestProvides.class);

    Injector injector = Guice.createInjector(module, scannerModule(testScanner));

    assertThat(getSourceScanner(injector.getBinding(Key.get(Boolean.class, Foo.class))))
        .isEqualTo(testScanner);
    assertThat(getSourceScanner(injector.getBinding(String.class))).isNotEqualTo(testScanner);
    assertThat(getSourceScanner(injector.getBinding(Long.class))).isNull();
  }

  @Test
  public void scannerSourceCorrectForBindingsCreatedByTheScannerDirectly() {
    ModuleAnnotatedMethodScanner scanner =
        new TestScanner(TestProvides.class) {
          @Override
          public <T> Key<T> prepareMethod(
              Binder binder, Annotation annotation, Key<T> key, InjectionPoint injectionPoint) {
            binder.bind(key.ofType(String.class)).toInstance("bla");
            return null;
          }
        };

    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @TestProvides
              @Foo
              Long discardedLong() {
                return 1L;
              }
            },
            scannerModule(scanner));

    assertThat(getSourceScanner(injector.getBinding(Key.get(String.class, Foo.class))))
        .isEqualTo(scanner);
  }

  @Test
  public void scannerSourceOfProvidesMethodBindingInsideCustomScannerIsCustomScanner() {
    ModuleAnnotatedMethodScanner scanner =
        new TestScanner(TestProvides.class) {
          @Override
          public <T> Key<T> prepareMethod(
              Binder binder, Annotation annotation, Key<T> key, InjectionPoint injectionPoint) {
            binder.install(
                new AbstractModule() {
                  // All bindings inside custom scanner should have it as their source scanner -
                  // including those created by a nested built-in @Provides* scanner.
                  @Provides
                  String provideString() {
                    return "bla";
                  }
                });
            return null;
          }
        };

    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @TestProvides
              @Foo
              Long discardedLong() {
                return 1L;
              }
            },
            scannerModule(scanner));

    assertThat(getSourceScanner(injector.getBinding(String.class))).isEqualTo(scanner);
  }

  @Test
  public void scannerSourceForPrivateModule() {
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            install(
                new PrivateModule() {
                  @Override
                  protected void configure() {}

                  @Exposed
                  @TestProvides
                  @Foo
                  String privateString() {
                    return "bar";
                  }
                });
          }
        };
    TestScanner scanner = new TestScanner(TestProvides.class);

    Injector injector = Guice.createInjector(module, scannerModule(scanner));

    assertThat(getSourceScanner(injector.getBinding(Key.get(String.class, Foo.class))))
        .isEqualTo(scanner);
  }

  ModuleAnnotatedMethodScanner getSourceScanner(Binding<?> binding) {
    return ((ElementSource) binding.getSource()).scanner;
  }

  private static Module scannerModule(ModuleAnnotatedMethodScanner scanner) {
    return new AbstractModule() {
      @Override
      protected void configure() {
        binder().scanModulesForAnnotatedMethods(scanner);
      }
    };
  }
}
