/**
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

import static com.google.inject.Asserts.assertContains;
import static com.google.inject.name.Names.named;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.CreationException;
import com.google.inject.Exposed;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.PrivateModule;
import com.google.inject.internal.util.StackTraceElements;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

import junit.framework.TestCase;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Set;

/** Tests for {@link ModuleAnnotatedMethodScanner} usage. */
public class ModuleAnnotatedMethodScannerTest extends TestCase {

  public void testScanning() throws Exception {
    Module module = new AbstractModule() {
      @Override protected void configure() {}

      @TestProvides @Named("foo") String foo() {
        return "foo";
      }

      @TestProvides @Named("foo2") String foo2() {
        return "foo2";
      }
    };
    Injector injector = Guice.createInjector(module, NamedMunger.module());

    // assert no bindings named "foo" or "foo2" exist -- they were munged.
    assertMungedBinding(injector, String.class, "foo", "foo");
    assertMungedBinding(injector, String.class, "foo2", "foo2");

    Binding<String> fooBinding = injector.getBinding(Key.get(String.class, named("foo-munged")));
    Binding<String> foo2Binding = injector.getBinding(Key.get(String.class, named("foo2-munged")));
    // Validate the provider has a sane toString
    assertEquals(methodName(TestProvides.class, "foo", module),
        fooBinding.getProvider().toString());
    assertEquals(methodName(TestProvides.class, "foo2", module),
        foo2Binding.getProvider().toString());
  }

  public void testSkipSources() throws Exception {
    Module module = new AbstractModule() {
      @Override protected void configure() {
        binder().skipSources(getClass()).install(new AbstractModule() {
          @Override protected void configure() {}

          @TestProvides @Named("foo") String foo() { return "foo"; }
        });
      }
    };
    Injector injector = Guice.createInjector(module, NamedMunger.module());
    assertMungedBinding(injector, String.class, "foo", "foo");
  }
  
  public void testWithSource() throws Exception {
    Module module = new AbstractModule() {
      @Override protected void configure() {
        binder().withSource("source").install(new AbstractModule() {
          @Override protected void configure() {}

          @TestProvides @Named("foo") String foo() { return "foo"; }
        });
      }
    };
    Injector injector = Guice.createInjector(module, NamedMunger.module());
    assertMungedBinding(injector, String.class, "foo", "foo");
  }

  public void testMoreThanOneClaimedAnnotationFails() throws Exception {
    Module module = new AbstractModule() {
      @Override protected void configure() {}

      @TestProvides @TestProvides2 String foo() {
        return "foo";
      }
    };
    try {
      Guice.createInjector(module, NamedMunger.module());
      fail();
    } catch(CreationException expected) {
      assertEquals(1, expected.getErrorMessages().size());
      assertContains(expected.getMessage(),
          "More than one annotation claimed by NamedMunger on method "
              + module.getClass().getName() + ".foo(). Methods can only have "
              + "one annotation claimed per scanner.");
    }
  }

  private String methodName(Class<? extends Annotation> annotation, String method, Object container)
      throws Exception {
    return "@" + annotation.getName() + " "
        + StackTraceElements.forMember(container.getClass().getDeclaredMethod(method));
  }

  @Documented @Target(METHOD) @Retention(RUNTIME)
  private @interface TestProvides {}

  @Documented @Target(METHOD) @Retention(RUNTIME)
  private @interface TestProvides2 {}

  private static class NamedMunger extends ModuleAnnotatedMethodScanner {
    static Module module() {
      return new AbstractModule() {
        @Override protected void configure() {
          binder().scanModulesForAnnotatedMethods(new NamedMunger());
        }
      };
    }

    @Override
    public String toString() {
      return "NamedMunger";
    }

    @Override
    public Set<? extends Class<? extends Annotation>> annotationClasses() {
      return ImmutableSet.of(TestProvides.class, TestProvides2.class);
    }

    @Override
    public <T> Key<T> prepareMethod(Binder binder, Annotation annotation, Key<T> key,
        InjectionPoint injectionPoint) {
      return Key.get(key.getTypeLiteral(),
          Names.named(((Named) key.getAnnotation()).value() + "-munged"));
    }
  }

  private void assertMungedBinding(Injector injector, Class<?> clazz, String originalName,
      Object expectedValue) {
    assertNull(injector.getExistingBinding(Key.get(clazz, named(originalName))));
    Binding<?> fooBinding = injector.getBinding(Key.get(clazz, named(originalName + "-munged")));
    assertEquals(expectedValue, fooBinding.getProvider().get());
  }

  public void testFailingScanner() {
    try {
      Guice.createInjector(new SomeModule(), FailingScanner.module());
      fail();
    } catch (CreationException expected) {
      Message m = Iterables.getOnlyElement(expected.getErrorMessages());
      assertEquals(
          "An exception was caught and reported. Message: Failing in the scanner.",
          m.getMessage());
      assertEquals(IllegalStateException.class, m.getCause().getClass());
      ElementSource source = (ElementSource) Iterables.getOnlyElement(m.getSources());
      assertEquals(SomeModule.class.getName(),
          Iterables.getOnlyElement(source.getModuleClassNames()));
      assertEquals(String.class.getName() + " " + SomeModule.class.getName() + ".aString()",
          source.toString());
    }
  }

  public static class FailingScanner extends ModuleAnnotatedMethodScanner {
    static Module module() {
      return new AbstractModule() {
        @Override protected void configure() {
          binder().scanModulesForAnnotatedMethods(new FailingScanner());
        }
      };
    }

    @Override public Set<? extends Class<? extends Annotation>> annotationClasses() {
      return ImmutableSet.of(TestProvides.class);
    }

    @Override public <T> Key<T> prepareMethod(
        Binder binder, Annotation rawAnnotation, Key<T> key, InjectionPoint injectionPoint) {
      throw new IllegalStateException("Failing in the scanner.");
    }
  }

  static class SomeModule extends AbstractModule {
    @TestProvides String aString() {
      return "Foo";
    }

    @Override protected void configure() {}
  }

  public void testChildInjectorInheritsScanner() {
    Injector parent = Guice.createInjector(NamedMunger.module());
    Injector child = parent.createChildInjector(new AbstractModule() {
      @Override protected void configure() {}

      @TestProvides @Named("foo") String foo() {
        return "foo";
      }
    });
    assertMungedBinding(child, String.class, "foo", "foo");
  }

  public void testChildInjectorScannersDontImpactSiblings() {
    Module module = new AbstractModule() {
      @Override
      protected void configure() {}

      @TestProvides @Named("foo") String foo() {
        return "foo";
      }
    };
    Injector parent = Guice.createInjector();
    Injector child = parent.createChildInjector(NamedMunger.module(), module);
    assertMungedBinding(child, String.class, "foo", "foo");

    // no foo nor foo-munged in sibling, since scanner never saw it.
    Injector sibling = parent.createChildInjector(module);
    assertNull(sibling.getExistingBinding(Key.get(String.class, named("foo"))));
    assertNull(sibling.getExistingBinding(Key.get(String.class, named("foo-munged"))));
  }

  public void testPrivateModuleInheritScanner_usingPrivateModule() {
    Injector injector = Guice.createInjector(NamedMunger.module(), new PrivateModule() {
      @Override protected void configure() {}

      @Exposed @TestProvides @Named("foo") String foo() {
        return "foo";
      }
    });
    assertMungedBinding(injector, String.class, "foo", "foo");
  }

  public void testPrivateModule_skipSourcesWithinPrivateModule() {
    Injector injector = Guice.createInjector(NamedMunger.module(), new PrivateModule() {
      @Override protected void configure() {
        binder().skipSources(getClass()).install(new AbstractModule() {
          @Override protected void configure() {}
          @Exposed @TestProvides @Named("foo") String foo() {
            return "foo";
          }
        });
      }
    });
    assertMungedBinding(injector, String.class, "foo", "foo");
  }

  public void testPrivateModule_skipSourcesForPrivateModule() {
    Injector injector = Guice.createInjector(NamedMunger.module(), new AbstractModule() {
      @Override protected void configure() {
        binder().skipSources(getClass()).install(new PrivateModule() {
          @Override protected void configure() {}

          @Exposed @TestProvides @Named("foo") String foo() {
            return "foo";
          }
        });
      }});
    assertMungedBinding(injector, String.class, "foo", "foo");
  }

  public void testPrivateModuleInheritScanner_usingPrivateBinder() {
    Injector injector = Guice.createInjector(NamedMunger.module(), new AbstractModule() {
      @Override protected void configure() {
        binder().newPrivateBinder().install(new AbstractModule() {
          @Override protected void configure() {}

          @Exposed @TestProvides @Named("foo") String foo() {
            return "foo";
          }
        });
      }
    });
    assertMungedBinding(injector, String.class, "foo", "foo");
  }

  public void testPrivateModuleInheritScanner_skipSourcesFromPrivateBinder() {
    Injector injector = Guice.createInjector(NamedMunger.module(), new AbstractModule() {
      @Override protected void configure() {
        binder().newPrivateBinder().skipSources(getClass()).install(new AbstractModule() {
          @Override protected void configure() {}

          @Exposed @TestProvides @Named("foo") String foo() {
            return "foo";
          }
        });
      }
    });
    assertMungedBinding(injector, String.class, "foo", "foo");
  }

  public void testPrivateModuleInheritScanner_skipSourcesFromPrivateBinder2() {
    Injector injector = Guice.createInjector(NamedMunger.module(), new AbstractModule() {
      @Override protected void configure() {
        binder().skipSources(getClass()).newPrivateBinder().install(new AbstractModule() {
          @Override protected void configure() {}

          @Exposed @TestProvides @Named("foo") String foo() {
            return "foo";
          }
        });
      }
    });
    assertMungedBinding(injector, String.class, "foo", "foo");
  }

  public void testPrivateModuleScannersDontImpactSiblings_usingPrivateModule() {
    Injector injector = Guice.createInjector(new PrivateModule() {
      @Override protected void configure() {
        install(NamedMunger.module());
      }

      @Exposed @TestProvides @Named("foo") String foo() {
        return "foo";
      }
    }, new PrivateModule() {
      @Override protected void configure() {}

      // ignored! (because the scanner doesn't run over this module)
      @Exposed @TestProvides @Named("foo") String foo() {
        return "foo";
      }
    });
    assertMungedBinding(injector, String.class, "foo", "foo");
  }

  public void testPrivateModuleScannersDontImpactSiblings_usingPrivateBinder() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override protected void configure() {
        binder().newPrivateBinder().install(new AbstractModule() {
          @Override protected void configure() {
            install(NamedMunger.module());
          }

          @Exposed @TestProvides @Named("foo") String foo() {
            return "foo";
          }
        });
      }
    }, new AbstractModule() {
      @Override protected void configure() {
        binder().newPrivateBinder().install(new AbstractModule() {
          @Override protected void configure() {}

          // ignored! (because the scanner doesn't run over this module)
          @Exposed @TestProvides @Named("foo") String foo() {
            return "foo";
          }
        });
      }});
    assertMungedBinding(injector, String.class, "foo", "foo");
  }

  public void testPrivateModuleWithinPrivateModule() {
    Injector injector = Guice.createInjector(NamedMunger.module(), new PrivateModule() {
      @Override protected void configure() {
        expose(Key.get(String.class, named("foo-munged")));
        install(new PrivateModule() {
          @Override protected void configure() {}

          @Exposed @TestProvides @Named("foo") String foo() {
            return "foo";
          }
        });
      }
    });
    assertMungedBinding(injector, String.class, "foo", "foo");
  }
}
