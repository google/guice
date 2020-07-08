/*
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

package com.google.inject.util;

import static com.google.inject.Asserts.asModuleChain;
import static com.google.inject.Asserts.assertContains;
import static com.google.inject.Guice.createInjector;
import static com.google.inject.name.Names.named;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
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
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Scope;
import com.google.inject.ScopeAnnotation;
import com.google.inject.Stage;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.ModuleAnnotatedMethodScanner;
import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import junit.framework.TestCase;

/** @author sberlin@gmail.com (Sam Berlin) */
public class OverrideModuleTest extends TestCase {

  private static final Key<String> key2 = Key.get(String.class, named("2"));
  private static final Key<String> key3 = Key.get(String.class, named("3"));

  private static final Module EMPTY_MODULE =
      new Module() {
        @Override
        public void configure(Binder binder) {}
      };

  public void testOverride() {
    Injector injector = createInjector(Modules.override(newModule("A")).with(newModule("B")));
    assertEquals("B", injector.getInstance(String.class));
  }

  public void testOverrideMultiple() {
    Module module =
        Modules.override(newModule("A"), newModule(1), newModule(0.5f))
            .with(newModule("B"), newModule(2), newModule(1.5d));
    Injector injector = createInjector(module);
    assertEquals("B", injector.getInstance(String.class));
    assertEquals(2, injector.getInstance(Integer.class).intValue());
    assertEquals(0.5f, injector.getInstance(Float.class), 0.0f);
    assertEquals(1.5d, injector.getInstance(Double.class), 0.0);
  }

  public void testOverrideUnmatchedTolerated() {
    Injector injector = createInjector(Modules.override(EMPTY_MODULE).with(newModule("B")));
    assertEquals("B", injector.getInstance(String.class));
  }

  public void testOverrideConstant() {
    Module original =
        new AbstractModule() {
          @Override
          protected void configure() {
            bindConstant().annotatedWith(named("Test")).to("A");
          }
        };

    Module replacements =
        new AbstractModule() {
          @Override
          protected void configure() {
            bindConstant().annotatedWith(named("Test")).to("B");
          }
        };

    Injector injector = createInjector(Modules.override(original).with(replacements));
    assertEquals("B", injector.getInstance(Key.get(String.class, named("Test"))));
  }

  public void testGetProviderInModule() {
    Module original =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(String.class).toInstance("A");
            bind(key2).toProvider(getProvider(String.class));
          }
        };

    Injector injector = createInjector(Modules.override(original).with(EMPTY_MODULE));
    assertEquals("A", injector.getInstance(String.class));
    assertEquals("A", injector.getInstance(key2));
  }

  public void testOverrideWhatGetProviderProvided() {
    Module original =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(String.class).toInstance("A");
            bind(key2).toProvider(getProvider(String.class));
          }
        };

    Module replacements = newModule("B");

    Injector injector = createInjector(Modules.override(original).with(replacements));
    assertEquals("B", injector.getInstance(String.class));
    assertEquals("B", injector.getInstance(key2));
  }

  public void testOverrideUsingOriginalsGetProvider() {
    Module original =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(String.class).toInstance("A");
            bind(key2).toInstance("B");
          }
        };

    Module replacements =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(String.class).toProvider(getProvider(key2));
          }
        };

    Injector injector = createInjector(Modules.override(original).with(replacements));
    assertEquals("B", injector.getInstance(String.class));
    assertEquals("B", injector.getInstance(key2));
  }

  public void testOverrideOfOverride() {
    Module original =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(String.class).toInstance("A1");
            bind(key2).toInstance("A2");
            bind(key3).toInstance("A3");
          }
        };

    Module replacements1 =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(String.class).toInstance("B1");
            bind(key2).toInstance("B2");
          }
        };

    Module overrides = Modules.override(original).with(replacements1);

    Module replacements2 =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(String.class).toInstance("C1");
            bind(key3).toInstance("C3");
          }
        };

    Injector injector = createInjector(Modules.override(overrides).with(replacements2));
    assertEquals("C1", injector.getInstance(String.class));
    assertEquals("B2", injector.getInstance(key2));
    assertEquals("C3", injector.getInstance(key3));
  }

  static class OuterReplacementsModule extends AbstractModule {
    @Override
    protected void configure() {
      install(new InnerReplacementsModule());
    }
  }

  static class InnerReplacementsModule extends AbstractModule {
    @Override
    protected void configure() {
      bind(String.class).toInstance("B");
      bind(String.class).toInstance("C");
    }
  }

  public void testOverridesTwiceFails() {
    Module original = newModule("A");
    Module replacements = new OuterReplacementsModule();
    Module module = Modules.override(original).with(replacements);
    try {
      createInjector(module);
      fail();
    } catch (CreationException expected) {
      assertContains(
          expected.getMessage(),
          "A binding to java.lang.String was already configured at "
              + InnerReplacementsModule.class.getName(),
          asModuleChain(
              Modules.OverrideModule.class,
              OuterReplacementsModule.class,
              InnerReplacementsModule.class),
          "at " + InnerReplacementsModule.class.getName(),
          asModuleChain(
              Modules.OverrideModule.class,
              OuterReplacementsModule.class,
              InnerReplacementsModule.class));
    }
  }

  public void testOverridesDoesntFixTwiceBoundInOriginal() {
    Module original =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(String.class).toInstance("A");
            bind(String.class).toInstance("B");
          }
        };

    Module replacements =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(String.class).toInstance("C");
          }
        };

    Module module = Modules.override(original).with(replacements);
    try {
      createInjector(module);
      fail();
    } catch (CreationException expected) {
      // The replacement comes first because we replace A with C,
      // then we encounter B and freak out.
      assertContains(
          expected.getMessage(),
          "1) A binding to java.lang.String was already configured at "
              + replacements.getClass().getName(),
          asModuleChain(Modules.OverrideModule.class, replacements.getClass()),
          "at " + original.getClass().getName(),
          asModuleChain(Modules.OverrideModule.class, original.getClass()));
    }
  }

  public void testStandardScopeAnnotation() {
    final SingleUseScope scope = new SingleUseScope();

    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            bindScope(TestScopeAnnotation.class, scope);
            bind(String.class).in(TestScopeAnnotation.class);
          }
        };
    assertFalse(scope.used);

    Guice.createInjector(module);
    assertTrue(scope.used);
  }

  public void testOverrideUntargettedBinding() {
    Module original =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(Date.class);
          }
        };

    Module replacements =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(Date.class).toInstance(new Date(0));
          }
        };

    Injector injector = createInjector(Modules.override(original).with(replacements));
    assertEquals(0, injector.getInstance(Date.class).getTime());
  }

  public void testOverrideScopeAnnotation() {
    final Scope scope =
        new Scope() {
          @Override
          public <T> Provider<T> scope(Key<T> key, Provider<T> unscoped) {
            throw new AssertionError("Should not be called");
          }
        };

    final SingleUseScope replacementScope = new SingleUseScope();

    Module original =
        new AbstractModule() {
          @Override
          protected void configure() {
            bindScope(TestScopeAnnotation.class, scope);
            bind(Date.class).in(TestScopeAnnotation.class);
          }
        };

    Module replacements =
        new AbstractModule() {
          @Override
          protected void configure() {
            bindScope(TestScopeAnnotation.class, replacementScope);
          }
        };

    Injector injector = createInjector(Modules.override(original).with(replacements));
    injector.getInstance(Date.class);
    assertTrue(replacementScope.used);
  }

  public void testFailsIfOverridenScopeInstanceHasBeenUsed() {
    final Scope scope =
        new Scope() {
          @Override
          public <T> Provider<T> scope(Key<T> key, Provider<T> unscoped) {
            return unscoped;
          }

          @Override
          public String toString() {
            return "ORIGINAL SCOPE";
          }
        };

    final Module original =
        new AbstractModule() {
          @Override
          protected void configure() {
            bindScope(TestScopeAnnotation.class, scope);
            bind(Date.class).in(scope);
            bind(String.class).in(scope);
          }
        };
    Module originalWrapper =
        new AbstractModule() {
          @Override
          protected void configure() {
            install(original);
          }
        };

    Module replacements =
        new AbstractModule() {
          @Override
          protected void configure() {
            bindScope(TestScopeAnnotation.class, new SingleUseScope());
          }
        };

    try {
      createInjector(Modules.override(originalWrapper).with(replacements));
      fail("Exception expected");
    } catch (CreationException e) {
      assertContains(
          e.getMessage(),
          "1) The scope for @TestScopeAnnotation is bound directly and cannot be overridden.",
          "original binding at " + original.getClass().getName() + ".configure(",
          asModuleChain(originalWrapper.getClass(), original.getClass()),
          "bound directly at " + original.getClass().getName() + ".configure(",
          asModuleChain(originalWrapper.getClass(), original.getClass()),
          "bound directly at " + original.getClass().getName() + ".configure(",
          asModuleChain(originalWrapper.getClass(), original.getClass()),
          "at ",
          replacements.getClass().getName() + ".configure(",
          asModuleChain(Modules.OverrideModule.class, replacements.getClass()));
    }
  }

  public void testOverrideIsLazy() {
    final AtomicReference<String> value = new AtomicReference<>("A");
    Module overridden =
        Modules.override(
                new AbstractModule() {
                  @Override
                  protected void configure() {
                    bind(String.class).annotatedWith(named("original")).toInstance(value.get());
                  }
                })
            .with(
                new AbstractModule() {
                  @Override
                  protected void configure() {
                    bind(String.class).annotatedWith(named("override")).toInstance(value.get());
                  }
                });

    // the value.get() call should be deferred until Guice.createInjector
    value.set("B");
    Injector injector = Guice.createInjector(overridden);
    assertEquals("B", injector.getInstance(Key.get(String.class, named("original"))));
    assertEquals("B", injector.getInstance(Key.get(String.class, named("override"))));
  }

  public void testOverridePrivateModuleOverPrivateModule() {
    Module exposes5and6 =
        new AbstractModule() {
          @Override
          protected void configure() {
            install(
                new PrivateModule() {
                  @Override
                  protected void configure() {
                    bind(Integer.class).toInstance(5);
                    expose(Integer.class);

                    bind(Character.class).toInstance('E');
                  }
                });

            install(
                new PrivateModule() {
                  @Override
                  protected void configure() {
                    bind(Long.class).toInstance(6L);
                    expose(Long.class);

                    bind(Character.class).toInstance('F');
                  }
                });
          }
        };

    AbstractModule exposes15 =
        new AbstractModule() {
          @Override
          protected void configure() {
            install(
                new PrivateModule() {
                  @Override
                  protected void configure() {
                    bind(Integer.class).toInstance(15);
                    expose(Integer.class);

                    bind(Character.class).toInstance('G');
                  }
                });

            install(
                new PrivateModule() {
                  @Override
                  protected void configure() {
                    bind(Character.class).toInstance('H');
                  }
                });
          }
        };

    // override forwards
    Injector injector = Guice.createInjector(Modules.override(exposes5and6).with(exposes15));
    assertEquals(15, injector.getInstance(Integer.class).intValue());
    assertEquals(6L, injector.getInstance(Long.class).longValue());

    // and in reverse order
    Injector reverse = Guice.createInjector(Modules.override(exposes15).with(exposes5and6));
    assertEquals(5, reverse.getInstance(Integer.class).intValue());
    assertEquals(6L, reverse.getInstance(Long.class).longValue());
  }

  public void testOverrideModuleAndPrivateModule() {
    Module exposes5 =
        new PrivateModule() {
          @Override
          protected void configure() {
            bind(Integer.class).toInstance(5);
            expose(Integer.class);
          }
        };

    Module binds15 =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(Integer.class).toInstance(15);
          }
        };

    Injector injector = Guice.createInjector(Modules.override(exposes5).with(binds15));
    assertEquals(15, injector.getInstance(Integer.class).intValue());

    Injector reverse = Guice.createInjector(Modules.override(binds15).with(exposes5));
    assertEquals(5, reverse.getInstance(Integer.class).intValue());
  }

  public void testOverrideDeepExpose() {
    final AtomicReference<Provider<Character>> charAProvider =
        new AtomicReference<Provider<Character>>();

    Module exposes5 =
        new PrivateModule() {
          @Override
          protected void configure() {
            install(
                new PrivateModule() {
                  @Override
                  protected void configure() {
                    bind(Integer.class).toInstance(5);
                    expose(Integer.class);
                    charAProvider.set(getProvider(Character.class));
                    bind(Character.class).toInstance('A');
                  }
                });
            expose(Integer.class);
          }
        };

    Injector injector = Guice.createInjector(Modules.override(exposes5).with(EMPTY_MODULE));
    assertEquals(5, injector.getInstance(Integer.class).intValue());
    assertEquals('A', charAProvider.getAndSet(null).get().charValue());

    injector = Guice.createInjector(Modules.override(EMPTY_MODULE).with(exposes5));
    assertEquals(5, injector.getInstance(Integer.class).intValue());
    assertEquals('A', charAProvider.getAndSet(null).get().charValue());

    final AtomicReference<Provider<Character>> charBProvider =
        new AtomicReference<Provider<Character>>();

    Module binds15 =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(Integer.class).toInstance(15);

            install(
                new PrivateModule() {
                  @Override
                  protected void configure() {
                    charBProvider.set(getProvider(Character.class));
                    bind(Character.class).toInstance('B');
                  }
                });
          }
        };

    injector = Guice.createInjector(Modules.override(binds15).with(exposes5));
    assertEquals(5, injector.getInstance(Integer.class).intValue());
    assertEquals('A', charAProvider.getAndSet(null).get().charValue());
    assertEquals('B', charBProvider.getAndSet(null).get().charValue());

    injector = Guice.createInjector(Modules.override(exposes5).with(binds15));
    assertEquals(15, injector.getInstance(Integer.class).intValue());
    assertEquals('A', charAProvider.getAndSet(null).get().charValue());
    assertEquals('B', charBProvider.getAndSet(null).get().charValue());
  }

  @Retention(RUNTIME)
  @Target(TYPE)
  @ScopeAnnotation
  private static @interface TestScopeAnnotation {}

  private static class SingleUseScope implements Scope {
    boolean used = false;

    @Override
    public <T> Provider<T> scope(Key<T> key, Provider<T> unscoped) {
      assertFalse(used);
      used = true;
      return unscoped;
    }
  }

  static class NewModule<T> extends AbstractModule {
    private final T bound;

    NewModule(T bound) {
      this.bound = bound;
    }

    @Override
    protected void configure() {
      @SuppressWarnings("unchecked")
      Class<T> type = (Class<T>) bound.getClass();
      bind(type).toInstance(bound);
    }
  }

  private static <T> Module newModule(final T bound) {
    return new NewModule<T>(bound);
  }

  private static final String RESULT = "RESULT";
  private static final String PRIVATE_INPUT = "PRIVATE_INPUT";
  private static final String OVERRIDDEN_INPUT = "FOO";
  private static final String OVERRIDDEN_RESULT = "Size: 3";
  private static final Key<String> RESULT_KEY = Key.get(String.class, named(RESULT));
  private static final Key<String> INPUT_KEY = Key.get(String.class, named(PRIVATE_INPUT));

  public void testExposedBindingOverride() throws Exception {
    Injector inj =
        Guice.createInjector(
            Modules.override(new ExampleModule())
                .with(
                    new AbstractModule() {
                      @Override
                      protected void configure() {
                        bind(RESULT_KEY).toInstance(OVERRIDDEN_RESULT);
                      }
                    }));
    assertEquals(OVERRIDDEN_RESULT, inj.getInstance(RESULT_KEY));
  }

  public void testPrivateBindingOverride() throws Exception {
    Injector inj =
        Guice.createInjector(
            Modules.override(new ExampleModule())
                .with(
                    new AbstractModule() {
                      @Override
                      protected void configure() {
                        bind(INPUT_KEY).toInstance(OVERRIDDEN_INPUT);
                      }
                    }));
    assertEquals(OVERRIDDEN_RESULT, inj.getInstance(RESULT_KEY));
  }

  public static class ExampleModule extends PrivateModule {
    @Provides
    @Exposed
    @Named(RESULT)
    public String provideResult(@Named(PRIVATE_INPUT) String input) {
      return "Size: " + input.length();
    }

    @Provides
    @Named(PRIVATE_INPUT)
    public String provideInput() {
      return "Hello World";
    }

    @Override
    protected void configure() {}
  }

  public void testEqualsNotCalledByDefaultOnInstance() {
    final HashEqualsTester a = new HashEqualsTester();
    a.throwOnEquals = true;
    Guice.createInjector(
        Modules.override(
                new AbstractModule() {
                  @Override
                  protected void configure() {
                    bind(String.class);
                    bind(HashEqualsTester.class).toInstance(a);
                  }
                })
            .with());
  }

  public void testEqualsNotCalledByDefaultOnProvider() {
    final HashEqualsTester a = new HashEqualsTester();
    a.throwOnEquals = true;
    Guice.createInjector(
        Modules.override(
                new AbstractModule() {
                  @Override
                  protected void configure() {
                    bind(String.class);
                    bind(Object.class).toProvider(a);
                  }
                })
            .with());
  }

  public void testHashcodeNeverCalledOnInstance() {
    final HashEqualsTester a = new HashEqualsTester();
    a.throwOnHashcode = true;
    a.equality = "test";

    final HashEqualsTester b = new HashEqualsTester();
    b.throwOnHashcode = true;
    b.equality = "test";
    Guice.createInjector(
        Modules.override(
                new AbstractModule() {
                  @Override
                  protected void configure() {
                    bind(String.class);
                    bind(HashEqualsTester.class).toInstance(a);
                    bind(HashEqualsTester.class).toInstance(b);
                  }
                })
            .with());
  }

  public void testHashcodeNeverCalledOnProviderInstance() {
    final HashEqualsTester a = new HashEqualsTester();
    a.throwOnHashcode = true;
    a.equality = "test";

    final HashEqualsTester b = new HashEqualsTester();
    b.throwOnHashcode = true;
    b.equality = "test";
    Guice.createInjector(
        Modules.override(
                new AbstractModule() {
                  @Override
                  protected void configure() {
                    bind(String.class);
                    bind(Object.class).toProvider(a);
                    bind(Object.class).toProvider(b);
                  }
                })
            .with());
  }

  private static class HashEqualsTester implements Provider<Object> {
    private String equality;
    private boolean throwOnEquals;
    private boolean throwOnHashcode;

    @Override
    public boolean equals(Object obj) {
      if (throwOnEquals) {
        throw new RuntimeException();
      } else if (obj instanceof HashEqualsTester) {
        HashEqualsTester o = (HashEqualsTester) obj;
        if (o.throwOnEquals) {
          throw new RuntimeException();
        }
        if (equality == null && o.equality == null) {
          return this == o;
        } else {
          return Objects.equal(equality, o.equality);
        }
      } else {
        return false;
      }
    }

    @Override
    public int hashCode() {
      if (throwOnHashcode) {
        throw new RuntimeException();
      } else {
        return super.hashCode();
      }
    }

    @Override
    public Object get() {
      return new Object();
    }
  }

  public void testCorrectStage() {
    final Stage stage = Stage.PRODUCTION;
    Module module =
        Modules.override(
                new AbstractModule() {
                  @Override
                  protected void configure() {
                    if (currentStage() != Stage.PRODUCTION) {
                      addError("Wronge stage in overridden module:" + currentStage());
                    }
                  }
                })
            .with(
                new AbstractModule() {
                  @Override
                  protected void configure() {
                    if (currentStage() != Stage.PRODUCTION) {
                      addError("Wronge stage in overriding module:" + currentStage());
                    }
                  }
                });
    Guice.createInjector(stage, module);
  }

  public void testOverridesApplyOriginalScanners() {
    Injector injector =
        Guice.createInjector(
            Modules.override(NamedMunger.module())
                .with(
                    new AbstractModule() {

                      @TestProvides
                      @Named("test")
                      String provideString() {
                        return "foo";
                      }
                    }));

    assertNull(injector.getExistingBinding(Key.get(String.class, named("test"))));
    Binding<String> binding = injector.getBinding(Key.get(String.class, named("test-munged")));
    assertEquals("foo", binding.getProvider().get());
  }

  @Documented
  @Target(METHOD)
  @Retention(RUNTIME)
  private @interface TestProvides {}

  private static class NamedMunger extends ModuleAnnotatedMethodScanner {
    static Module module() {
      return new AbstractModule() {
        @Override
        protected void configure() {
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
      return ImmutableSet.of(TestProvides.class);
    }

    @Override
    public <T> Key<T> prepareMethod(
        Binder binder, Annotation annotation, Key<T> key, InjectionPoint injectionPoint) {
      return key.withAnnotation(Names.named(((Named) key.getAnnotation()).value() + "-munged"));
    }
  }
}
