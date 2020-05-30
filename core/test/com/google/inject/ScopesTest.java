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

import static com.google.inject.Asserts.asModuleChain;
import static com.google.inject.Asserts.assertContains;
import static com.google.inject.Asserts.getDeclaringSourcePart;
import static com.google.inject.name.Names.named;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.inject.matcher.Matchers;
import com.google.inject.name.Named;
import com.google.inject.spi.DefaultBindingScopingVisitor;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.google.inject.spi.Message;
import com.google.inject.spi.PrivateElements;
import com.google.inject.spi.ProvisionListener;
import com.google.inject.util.Providers;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;

/** @author crazybob@google.com (Bob Lee) */
public class ScopesTest extends TestCase {

  static final long DEADLOCK_TIMEOUT_SECONDS = 1;

  private final AbstractModule singletonsModule =
      new AbstractModule() {
        @Override
        protected void configure() {
          bind(BoundAsSingleton.class).in(Scopes.SINGLETON);
          bind(AnnotatedSingleton.class);
          bind(EagerSingleton.class).asEagerSingleton();
          bind(LinkedSingleton.class).to(RealLinkedSingleton.class);
          bind(DependsOnJustInTimeSingleton.class);
          bind(NotASingleton.class);
          bind(ImplementedBySingleton.class).in(Scopes.SINGLETON);
          bind(ProvidedBySingleton.class).in(Scopes.SINGLETON);
        }
      };

  @Override
  protected void setUp() throws Exception {
    AnnotatedSingleton.nextInstanceId = 0;
    BoundAsSingleton.nextInstanceId = 0;
    EagerSingleton.nextInstanceId = 0;
    RealLinkedSingleton.nextInstanceId = 0;
    JustInTimeSingleton.nextInstanceId = 0;
    NotASingleton.nextInstanceId = 0;
    Implementation.nextInstanceId = 0;
    ProvidedBySingleton.nextInstanceId = 0;
    ThrowingSingleton.nextInstanceId = 0;
  }

  public void testSingletons() {
    Injector injector = Guice.createInjector(singletonsModule);

    assertSame(
        injector.getInstance(BoundAsSingleton.class), injector.getInstance(BoundAsSingleton.class));

    assertSame(
        injector.getInstance(AnnotatedSingleton.class),
        injector.getInstance(AnnotatedSingleton.class));

    assertSame(
        injector.getInstance(EagerSingleton.class), injector.getInstance(EagerSingleton.class));

    assertSame(
        injector.getInstance(LinkedSingleton.class), injector.getInstance(LinkedSingleton.class));

    assertSame(
        injector.getInstance(JustInTimeSingleton.class),
        injector.getInstance(JustInTimeSingleton.class));

    assertNotSame(
        injector.getInstance(NotASingleton.class), injector.getInstance(NotASingleton.class));

    assertSame(
        injector.getInstance(ImplementedBySingleton.class),
        injector.getInstance(ImplementedBySingleton.class));

    assertSame(
        injector.getInstance(ProvidedBySingleton.class),
        injector.getInstance(ProvidedBySingleton.class));
  }

  public void testJustInTimeAnnotatedSingleton() {
    Injector injector = Guice.createInjector();

    assertSame(
        injector.getInstance(AnnotatedSingleton.class),
        injector.getInstance(AnnotatedSingleton.class));
  }

  public void testSingletonIsPerInjector() {
    assertNotSame(
        Guice.createInjector().getInstance(AnnotatedSingleton.class),
        Guice.createInjector().getInstance(AnnotatedSingleton.class));
  }

  public void testOverriddingAnnotation() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(AnnotatedSingleton.class).in(Scopes.NO_SCOPE);
              }
            });

    assertNotSame(
        injector.getInstance(AnnotatedSingleton.class),
        injector.getInstance(AnnotatedSingleton.class));
  }

  public void testScopingAnnotationsOnAbstractTypeViaBind() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              bind(A.class).to(AImpl.class);
            }
          });
      fail();
    } catch (CreationException expected) {
      assertContains(
          expected.getMessage(),
          A.class.getName() + " is annotated with " + Singleton.class.getName(),
          "but scope annotations are not supported for abstract types.",
          "at " + A.class.getName() + ".class(ScopesTest.java:");
    }
  }

  @SuppressWarnings("InjectScopeAnnotationOnInterfaceOrAbstractClass") // for testing
  @Singleton
  interface A {}

  static class AImpl implements A {}

  @Retention(RUNTIME)
  @interface Component {}

  @SuppressWarnings("InjectScopeAnnotationOnInterfaceOrAbstractClass") // for testing
  @Component
  @Singleton
  interface ComponentAnnotationTest {}

  static class ComponentAnnotationTestImpl implements ComponentAnnotationTest {}

  public void testScopingAnnotationsOnAbstractTypeIsValidForComponent() {
    Guice.createInjector(
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(ComponentAnnotationTest.class).to(ComponentAnnotationTestImpl.class);
          }
        });
  }

  public void testScopingAnnotationsOnAbstractTypeViaImplementedBy() {
    try {
      Guice.createInjector().getInstance(D.class);
      fail();
    } catch (ConfigurationException expected) {
      assertContains(
          expected.getMessage(),
          D.class.getName() + " is annotated with " + Singleton.class.getName(),
          "but scope annotations are not supported for abstract types.",
          "at " + D.class.getName() + ".class(ScopesTest.java:");
    }
  }

  @SuppressWarnings("InjectScopeAnnotationOnInterfaceOrAbstractClass") // for testing
  @Singleton
  @ImplementedBy(DImpl.class)
  interface D {}

  static class DImpl implements D {}

  public void testScopingAnnotationsOnAbstractTypeViaProvidedBy() {
    try {
      Guice.createInjector().getInstance(E.class);
      fail();
    } catch (ConfigurationException expected) {
      assertContains(
          expected.getMessage(),
          E.class.getName() + " is annotated with " + Singleton.class.getName(),
          "but scope annotations are not supported for abstract types.",
          "at " + E.class.getName() + ".class(ScopesTest.java:");
    }
  }

  @SuppressWarnings("InjectScopeAnnotationOnInterfaceOrAbstractClass") // for testing
  @Singleton
  @ProvidedBy(EProvider.class)
  interface E {}

  static class EProvider implements Provider<E> {
    @Override
    public E get() {
      return null;
    }
  }

  public void testScopeUsedButNotBound() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              bind(B.class).in(CustomScoped.class);
              bind(C.class);
            }
          });
      fail();
    } catch (CreationException expected) {
      assertContains(
          expected.getMessage(),
          "1) No scope is bound to " + CustomScoped.class.getName(),
          "at " + getClass().getName(),
          getDeclaringSourcePart(getClass()),
          "2) No scope is bound to " + CustomScoped.class.getName(),
          "at " + C.class.getName() + ".class");
    }
  }

  static class B {}

  @CustomScoped
  static class C {}

  public void testSingletonsInProductionStage() {
    Guice.createInjector(Stage.PRODUCTION, singletonsModule);

    assertEquals(1, AnnotatedSingleton.nextInstanceId);
    assertEquals(1, BoundAsSingleton.nextInstanceId);
    assertEquals(1, EagerSingleton.nextInstanceId);
    assertEquals(1, RealLinkedSingleton.nextInstanceId);
    assertEquals(1, JustInTimeSingleton.nextInstanceId);
    assertEquals(0, NotASingleton.nextInstanceId);
  }

  public void testSingletonsInDevelopmentStage() {
    Guice.createInjector(Stage.DEVELOPMENT, singletonsModule);

    assertEquals(0, AnnotatedSingleton.nextInstanceId);
    assertEquals(0, BoundAsSingleton.nextInstanceId);
    assertEquals(1, EagerSingleton.nextInstanceId);
    assertEquals(0, RealLinkedSingleton.nextInstanceId);
    assertEquals(0, JustInTimeSingleton.nextInstanceId);
    assertEquals(0, NotASingleton.nextInstanceId);
  }

  public void testSingletonScopeIsNotSerializable() throws IOException {
    Asserts.assertNotSerializable(Scopes.SINGLETON);
  }

  public void testNoScopeIsNotSerializable() throws IOException {
    Asserts.assertNotSerializable(Scopes.NO_SCOPE);
  }

  public void testUnscopedProviderWorksOutsideOfRequestedScope() {
    final RememberProviderScope scope = new RememberProviderScope();

    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bindScope(CustomScoped.class, scope);
                bind(List.class).to(ArrayList.class).in(CustomScoped.class);
              }
            });

    injector.getInstance(List.class);
    Provider<?> listProvider = scope.providers.get(Key.get(List.class));

    // this line fails with a NullPointerException because the Providers
    // passed to Scope.scope() don't work outside of the scope() method.
    assertTrue(listProvider.get() instanceof ArrayList);
  }

  static class OuterRuntimeModule extends AbstractModule {
    @Override
    protected void configure() {
      install(new InnerRuntimeModule());
    }
  }

  static class InnerRuntimeModule extends AbstractModule {
    @Override
    protected void configure() {
      bindScope(NotRuntimeRetainedScoped.class, Scopes.NO_SCOPE);
    }
  }

  public void testScopeAnnotationWithoutRuntimeRetention() {
    try {
      Guice.createInjector(new OuterRuntimeModule());
      fail();
    } catch (CreationException expected) {
      assertContains(
          expected.getMessage(),
          "1) Please annotate "
              + NotRuntimeRetainedScoped.class.getName()
              + " with @Retention(RUNTIME).",
          "at " + InnerRuntimeModule.class.getName() + getDeclaringSourcePart(getClass()),
          asModuleChain(OuterRuntimeModule.class, InnerRuntimeModule.class));
    }
  }

  static class OuterDeprecatedModule extends AbstractModule {
    @Override
    protected void configure() {
      install(new InnerDeprecatedModule());
    }
  }

  static class InnerDeprecatedModule extends AbstractModule {
    @Override
    protected void configure() {
      bindScope(Deprecated.class, Scopes.NO_SCOPE);
    }
  }

  public void testBindScopeToAnnotationWithoutScopeAnnotation() {
    try {
      Guice.createInjector(new OuterDeprecatedModule());
      fail();
    } catch (CreationException expected) {
      assertContains(
          expected.getMessage(),
          "1) Please annotate " + Deprecated.class.getName() + " with @ScopeAnnotation.",
          "at " + InnerDeprecatedModule.class.getName() + getDeclaringSourcePart(getClass()),
          asModuleChain(OuterDeprecatedModule.class, InnerDeprecatedModule.class));
    }
  }

  static class OuterScopeModule extends AbstractModule {
    @Override
    protected void configure() {
      install(new CustomNoScopeModule());
      install(new CustomSingletonModule());
    }
  }

  static class CustomNoScopeModule extends AbstractModule {
    @Override
    protected void configure() {
      bindScope(CustomScoped.class, Scopes.NO_SCOPE);
    }
  }

  static class CustomSingletonModule extends AbstractModule {
    @Override
    protected void configure() {
      bindScope(CustomScoped.class, Scopes.SINGLETON);
    }
  }

  public void testBindScopeTooManyTimes() {
    try {
      Guice.createInjector(new OuterScopeModule());
      fail();
    } catch (CreationException expected) {
      assertContains(
          expected.getMessage(),
          "1) Scope Scopes.NO_SCOPE is already bound to "
              + CustomScoped.class.getName()
              + " at "
              + CustomNoScopeModule.class.getName()
              + getDeclaringSourcePart(getClass()),
          asModuleChain(OuterScopeModule.class, CustomNoScopeModule.class),
          "Cannot bind Scopes.SINGLETON.",
          "at " + ScopesTest.class.getName(),
          getDeclaringSourcePart(getClass()),
          asModuleChain(OuterScopeModule.class, CustomSingletonModule.class));
    }
  }

  public void testBindDuplicateScope() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bindScope(CustomScoped.class, Scopes.SINGLETON);
                bindScope(CustomScoped.class, Scopes.SINGLETON);
              }
            });

    assertSame(
        injector.getInstance(AnnotatedCustomScoped.class),
        injector.getInstance(AnnotatedCustomScoped.class));
  }

  public void testDuplicateScopeAnnotations() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bindScope(CustomScoped.class, Scopes.NO_SCOPE);
              }
            });

    try {
      injector.getInstance(SingletonAndCustomScoped.class);
      fail();
    } catch (ConfigurationException expected) {
      assertContains(
          expected.getMessage(),
          "1) More than one scope annotation was found: ",
          "while locating " + SingletonAndCustomScoped.class.getName());
    }
  }

  public void testNullScopedAsASingleton() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {

              final Iterator<String> values = Arrays.asList(null, "A").iterator();

              @Provides
              @Singleton
              String provideString() {
                return values.next();
              }
            });

    assertNull(injector.getInstance(String.class));
    assertNull(injector.getInstance(String.class));
    assertNull(injector.getInstance(String.class));
  }

  class RememberProviderScope implements Scope {
    final Map<Key<?>, Provider<?>> providers = Maps.newHashMap();

    @Override
    public <T> Provider<T> scope(Key<T> key, Provider<T> unscoped) {
      providers.put(key, unscoped);
      return unscoped;
    }
  }

  public void testSingletonAnnotationOnParameterizedType() {
    Injector injector = Guice.createInjector();
    assertSame(
        injector.getInstance(new Key<Injected<String>>() {}),
        injector.getInstance(new Key<Injected<String>>() {}));
    assertSame(
        injector.getInstance(new Key<In<Integer>>() {}),
        injector.getInstance(new Key<In<Short>>() {}));
  }

  @ImplementedBy(Injected.class)
  public interface In<T> {}

  @Singleton
  public static class Injected<T> implements In<T> {}

  @Target({ElementType.TYPE, ElementType.METHOD})
  @Retention(RUNTIME)
  @ScopeAnnotation
  public @interface CustomScoped {}

  static final Scope CUSTOM_SCOPE =
      new Scope() {
        @Override
        public <T> Provider<T> scope(Key<T> key, Provider<T> unscoped) {
          return Scopes.SINGLETON.scope(key, unscoped);
        }
      };

  @SuppressWarnings("InjectScopeOrQualifierAnnotationRetention") // to check failure mode
  @Target({ElementType.TYPE, ElementType.METHOD})
  @ScopeAnnotation
  public @interface NotRuntimeRetainedScoped {}

  @CustomScoped
  static class AnnotatedCustomScoped {}

  @Singleton
  static class AnnotatedSingleton {
    static int nextInstanceId;
    final int instanceId = nextInstanceId++;
  }

  static class BoundAsSingleton {
    static int nextInstanceId;
    final int instanceId = nextInstanceId++;
  }

  static class EagerSingleton {
    static int nextInstanceId;
    final int instanceId = nextInstanceId++;
  }

  interface LinkedSingleton {}

  @Singleton
  static class RealLinkedSingleton implements LinkedSingleton {
    static int nextInstanceId;
    final int instanceId = nextInstanceId++;
  }

  static class DependsOnJustInTimeSingleton {
    @Inject JustInTimeSingleton justInTimeSingleton;
  }

  @Singleton
  static class JustInTimeSingleton {
    static int nextInstanceId;
    final int instanceId = nextInstanceId++;
  }

  static class NotASingleton {
    static int nextInstanceId;
    final int instanceId = nextInstanceId++;
  }

  // suppress compiler error for testing
  @SuppressWarnings({"MoreThanOneScopeAnnotationOnClass", "multiple-scope"})
  @Singleton
  @CustomScoped
  static class SingletonAndCustomScoped {}

  @ImplementedBy(Implementation.class)
  static interface ImplementedBySingleton {}

  @ProvidedBy(ImplementationProvider.class)
  static class ProvidedBySingleton {
    static int nextInstanceId;
    final int instanceId = nextInstanceId++;
  }

  static class Implementation implements ImplementedBySingleton {
    static int nextInstanceId;
    final int instanceId = nextInstanceId++;
  }

  static class ImplementationProvider implements Provider<ProvidedBySingleton> {
    @Override
    public ProvidedBySingleton get() {
      return new ProvidedBySingleton();
    }
  }

  public void testScopeThatGetsAnUnrelatedObject() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(B.class);
                bind(C.class);
                ProviderGetScope providerGetScope = new ProviderGetScope();
                requestInjection(providerGetScope);
                bindScope(CustomScoped.class, providerGetScope);
              }
            });

    injector.getInstance(C.class);
  }

  class ProviderGetScope implements Scope {
    @Inject Provider<B> bProvider;

    @Override
    public <T> Provider<T> scope(Key<T> key, final Provider<T> unscoped) {
      return new Provider<T>() {
        @Override
        public T get() {
          bProvider.get();
          return unscoped.get();
        }
      };
    }
  }

  public void testIsSingletonPositive() {
    final Key<String> a = Key.get(String.class, named("A"));
    final Key<String> b = Key.get(String.class, named("B"));
    final Key<String> c = Key.get(String.class, named("C"));
    final Key<String> d = Key.get(String.class, named("D"));
    final Key<String> e = Key.get(String.class, named("E"));
    final Key<String> f = Key.get(String.class, named("F"));
    final Key<String> g = Key.get(String.class, named("G"));
    final Key<Object> h = Key.get(Object.class, named("H"));
    final Key<String> i = Key.get(String.class, named("I"));

    Module singletonBindings =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(a).to(b);
            bind(b).to(c);
            bind(c).toProvider(Providers.of("c")).in(Scopes.SINGLETON);
            bind(d).toInstance("d");
            bind(e).toProvider(Providers.of("e")).asEagerSingleton();
            bind(f).toProvider(Providers.of("f")).in(Singleton.class);
            bind(h).to(AnnotatedSingleton.class);
            install(
                new PrivateModule() {
                  @Override
                  protected void configure() {
                    bind(i).toProvider(Providers.of("i")).in(Singleton.class);
                    expose(i);
                  }
                });
          }

          @Provides
          @Named("G")
          @Singleton
          String provideG() {
            return "g";
          }
        };

    @SuppressWarnings("unchecked") // we know the module contains only bindings
    List<Element> moduleBindings = Elements.getElements(singletonBindings);
    ImmutableMap<Key<?>, Binding<?>> map = indexBindings(moduleBindings);
    assertFalse(Scopes.isSingleton(map.get(a))); // linked bindings are not followed by modules
    assertFalse(Scopes.isSingleton(map.get(b)));
    assertTrue(Scopes.isSingleton(map.get(c)));
    assertTrue(Scopes.isSingleton(map.get(d)));
    assertTrue(Scopes.isSingleton(map.get(e)));
    assertTrue(Scopes.isSingleton(map.get(f)));
    assertTrue(Scopes.isSingleton(map.get(g)));
    assertFalse(Scopes.isSingleton(map.get(h))); // annotated classes are not followed by modules
    assertTrue(Scopes.isSingleton(map.get(i)));

    Injector injector = Guice.createInjector(singletonBindings);
    assertTrue(Scopes.isSingleton(injector.getBinding(a)));
    assertTrue(Scopes.isSingleton(injector.getBinding(b)));
    assertTrue(Scopes.isSingleton(injector.getBinding(c)));
    assertTrue(Scopes.isSingleton(injector.getBinding(d)));
    assertTrue(Scopes.isSingleton(injector.getBinding(e)));
    assertTrue(Scopes.isSingleton(injector.getBinding(f)));
    assertTrue(Scopes.isSingleton(injector.getBinding(g)));
    assertTrue(Scopes.isSingleton(injector.getBinding(h)));
    assertTrue(Scopes.isSingleton(injector.getBinding(i)));
  }

  public void testIsSingletonNegative() {
    final Key<String> a = Key.get(String.class, named("A"));
    final Key<String> b = Key.get(String.class, named("B"));
    final Key<String> c = Key.get(String.class, named("C"));
    final Key<String> d = Key.get(String.class, named("D"));
    final Key<String> e = Key.get(String.class, named("E"));
    final Key<String> f = Key.get(String.class, named("F"));

    Module singletonBindings =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(a).to(b);
            bind(b).to(c);
            bind(c).toProvider(Providers.of("c")).in(Scopes.NO_SCOPE);
            bind(d).toProvider(Providers.of("d")).in(CustomScoped.class);
            bindScope(CustomScoped.class, Scopes.NO_SCOPE);
            install(
                new PrivateModule() {
                  @Override
                  protected void configure() {
                    bind(f).toProvider(Providers.of("f")).in(CustomScoped.class);
                    expose(f);
                  }
                });
          }

          @Provides
          @Named("E")
          @CustomScoped
          String provideE() {
            return "e";
          }
        };

    @SuppressWarnings("unchecked") // we know the module contains only bindings
    List<Element> moduleBindings = Elements.getElements(singletonBindings);
    ImmutableMap<Key<?>, Binding<?>> map = indexBindings(moduleBindings);
    assertFalse(Scopes.isSingleton(map.get(a)));
    assertFalse(Scopes.isSingleton(map.get(b)));
    assertFalse(Scopes.isSingleton(map.get(c)));
    assertFalse(Scopes.isSingleton(map.get(d)));
    assertFalse(Scopes.isSingleton(map.get(e)));
    assertFalse(Scopes.isSingleton(map.get(f)));

    Injector injector = Guice.createInjector(singletonBindings);
    assertFalse(Scopes.isSingleton(injector.getBinding(a)));
    assertFalse(Scopes.isSingleton(injector.getBinding(b)));
    assertFalse(Scopes.isSingleton(injector.getBinding(c)));
    assertFalse(Scopes.isSingleton(injector.getBinding(d)));
    assertFalse(Scopes.isSingleton(injector.getBinding(e)));
    assertFalse(Scopes.isSingleton(injector.getBinding(f)));
  }

  public void testIsScopedPositive() {
    final Key<String> a = Key.get(String.class, named("A"));
    final Key<String> b = Key.get(String.class, named("B"));
    final Key<String> c = Key.get(String.class, named("C"));
    final Key<String> d = Key.get(String.class, named("D"));
    final Key<String> e = Key.get(String.class, named("E"));
    final Key<Object> f = Key.get(Object.class, named("F"));
    final Key<String> g = Key.get(String.class, named("G"));

    Module customBindings =
        new AbstractModule() {
          @Override
          protected void configure() {
            bindScope(CustomScoped.class, CUSTOM_SCOPE);
            bind(a).to(b);
            bind(b).to(c);
            bind(c).toProvider(Providers.of("c")).in(CUSTOM_SCOPE);
            bind(d).toProvider(Providers.of("d")).in(CustomScoped.class);
            bind(f).to(AnnotatedCustomScoped.class);
            install(
                new PrivateModule() {
                  @Override
                  protected void configure() {
                    bind(g).toProvider(Providers.of("g")).in(CustomScoped.class);
                    expose(g);
                  }
                });
          }

          @Provides
          @Named("E")
          @CustomScoped
          String provideE() {
            return "e";
          }
        };

    @SuppressWarnings("unchecked") // we know the module contains only bindings
    List<Element> moduleBindings = Elements.getElements(customBindings);
    ImmutableMap<Key<?>, Binding<?>> map = indexBindings(moduleBindings);
    assertFalse(isCustomScoped(map.get(a))); // linked bindings are not followed by modules
    assertFalse(isCustomScoped(map.get(b)));
    assertTrue(isCustomScoped(map.get(c)));
    assertTrue(isCustomScoped(map.get(d)));
    assertTrue(isCustomScoped(map.get(e)));
    assertFalse(isCustomScoped(map.get(f))); // annotated classes are not followed by modules
    assertTrue(isCustomScoped(map.get(g)));

    Injector injector = Guice.createInjector(customBindings);
    assertTrue(isCustomScoped(injector.getBinding(a)));
    assertTrue(isCustomScoped(injector.getBinding(b)));
    assertTrue(isCustomScoped(injector.getBinding(c)));
    assertTrue(isCustomScoped(injector.getBinding(d)));
    assertTrue(isCustomScoped(injector.getBinding(e)));
    assertTrue(isCustomScoped(injector.getBinding(f)));
    assertTrue(isCustomScoped(injector.getBinding(g)));
  }

  public void testIsScopedNegative() {
    final Key<String> a = Key.get(String.class, named("A"));
    final Key<String> b = Key.get(String.class, named("B"));
    final Key<String> c = Key.get(String.class, named("C"));
    final Key<String> d = Key.get(String.class, named("D"));
    final Key<String> e = Key.get(String.class, named("E"));
    final Key<String> f = Key.get(String.class, named("F"));
    final Key<String> g = Key.get(String.class, named("G"));
    final Key<String> h = Key.get(String.class, named("H"));

    Module customBindings =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(a).to(b);
            bind(b).to(c);
            bind(c).toProvider(Providers.of("c")).in(Scopes.NO_SCOPE);
            bind(d).toProvider(Providers.of("d")).in(Singleton.class);
            install(
                new PrivateModule() {
                  @Override
                  protected void configure() {
                    bind(f).toProvider(Providers.of("f")).in(Singleton.class);
                    expose(f);
                  }
                });
            bind(g).toInstance("g");
            bind(h).toProvider(Providers.of("h")).asEagerSingleton();
          }

          @Provides
          @Named("E")
          @Singleton
          String provideE() {
            return "e";
          }
        };

    @SuppressWarnings("unchecked") // we know the module contains only bindings
    List<Element> moduleBindings = Elements.getElements(customBindings);
    ImmutableMap<Key<?>, Binding<?>> map = indexBindings(moduleBindings);
    assertFalse(isCustomScoped(map.get(a)));
    assertFalse(isCustomScoped(map.get(b)));
    assertFalse(isCustomScoped(map.get(c)));
    assertFalse(isCustomScoped(map.get(d)));
    assertFalse(isCustomScoped(map.get(e)));
    assertFalse(isCustomScoped(map.get(f)));
    assertFalse(isCustomScoped(map.get(g)));
    assertFalse(isCustomScoped(map.get(h)));

    Injector injector = Guice.createInjector(customBindings);
    assertFalse(isCustomScoped(injector.getBinding(a)));
    assertFalse(isCustomScoped(injector.getBinding(b)));
    assertFalse(isCustomScoped(injector.getBinding(c)));
    assertFalse(isCustomScoped(injector.getBinding(d)));
    assertFalse(isCustomScoped(injector.getBinding(e)));
    assertFalse(isCustomScoped(injector.getBinding(f)));
    assertFalse(isCustomScoped(injector.getBinding(g)));
    assertFalse(isCustomScoped(injector.getBinding(h)));
  }

  private boolean isCustomScoped(Binding<?> binding) {
    return Scopes.isScoped(binding, CUSTOM_SCOPE, CustomScoped.class);
  }

  ImmutableMap<Key<?>, Binding<?>> indexBindings(Iterable<Element> elements) {
    ImmutableMap.Builder<Key<?>, Binding<?>> builder = ImmutableMap.builder();
    for (Element element : elements) {
      if (element instanceof Binding) {
        Binding<?> binding = (Binding<?>) element;
        builder.put(binding.getKey(), binding);
      } else if (element instanceof PrivateElements) {
        PrivateElements privateElements = (PrivateElements) element;
        Map<Key<?>, Binding<?>> privateBindings = indexBindings(privateElements.getElements());
        for (Key<?> exposed : privateElements.getExposedKeys()) {
          builder.put(exposed, privateBindings.get(exposed));
        }
      }
    }
    return builder.build();
  }

  @Singleton
  static class ThrowingSingleton {
    static int nextInstanceId;
    final int instanceId = nextInstanceId++;

    ThrowingSingleton() {
      if (instanceId == 0) {
        throw new RuntimeException();
      }
    }
  }

  public void testSingletonConstructorThrows() {
    Injector injector = Guice.createInjector();

    try {
      injector.getInstance(ThrowingSingleton.class);
      fail();
    } catch (ProvisionException expected) {
    }

    // this behaviour is unspecified. If we change Guice to re-throw the exception, this test
    // should be changed
    injector.getInstance(ThrowingSingleton.class);
    assertEquals(2, ThrowingSingleton.nextInstanceId);
  }

  /**
   * Should only be created by {@link SBarrierProvider}.
   *
   * <p>{@code S} stands for synchronization.
   *
   * @see SBarrierProvider
   */
  static class S {

    private S(int preventInjectionWithoutProvider) {}
  }

  /**
   * Provides all the instances of S simultaneously using {@link CyclicBarrier} with {@code
   * nThreads}. Intended to be used for threads synchronization during injection.
   */
  static class SBarrierProvider implements Provider<S> {

    final CyclicBarrier barrier;
    volatile boolean barrierPassed = false;

    SBarrierProvider(int nThreads) {
      barrier = new CyclicBarrier(nThreads, () -> barrierPassed = true);
    }

    @Override
    public S get() {
      try {
        if (!barrierPassed) {
          // only if we're triggering barrier for the first time
          barrier.await(DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return new S(0);
    }
  }

  /**
   * Tests that different injectors should not affect each other.
   *
   * <p>This creates a second thread to work in parallel, to create two instances of {@link S} as
   * the same time. If the lock if not granular enough (i.e. JVM-wide) then they would block each
   * other creating a deadlock and await timeout.
   */

  public void testInjectorsDontDeadlockOnSingletons() throws Exception {
    final Provider<S> provider = new SBarrierProvider(2);
    final Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                Thread.currentThread().setName("S.class[1]");
                bind(S.class).toProvider(provider).in(Scopes.SINGLETON);
              }
            });
    final Injector secondInjector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                Thread.currentThread().setName("S.class[2]");
                bind(S.class).toProvider(provider).in(Scopes.SINGLETON);
              }
            });

    Future<S> secondThreadResult =
        Executors.newSingleThreadExecutor().submit(() -> secondInjector.getInstance(S.class));

    S firstS = injector.getInstance(S.class);
    S secondS = secondThreadResult.get();

    assertNotSame(firstS, secondS);
  }

  @ImplementedBy(GImpl.class)
  interface G {}

  @Singleton
  static class GImpl implements G {

    final H h;

    /** Relies on Guice implementation to inject S first and H later, which provides a barrier . */
    @Inject
    GImpl(S synchronizationBarrier, H h) {
      this.h = h;
    }
  }

  @ImplementedBy(HImpl.class)
  interface H {}

  @Singleton
  static class HImpl implements H {

    final G g;

    /** Relies on Guice implementation to inject S first and G later, which provides a barrier . */
    @Inject
    HImpl(S synchronizationBarrier, G g) throws Exception {
      this.g = g;
    }
  }

  /**
   * Tests that injector can create two singletons with circular dependency in parallel.
   *
   * <p>This creates two threads to work in parallel, to create instances of {@link G} and {@link
   * H}. Creation is synchronized by injection of {@link S}, first thread would block until second
   * would be inside a singleton creation as well.
   *
   * <p>Both instances are created by sibling injectors, that share singleton scope. Verifies that
   * exactly one circular proxy object is created.
   */

  public void testSiblingInjectorGettingCircularSingletonsOneCircularProxy() throws Exception {
    final Provider<S> provider = new SBarrierProvider(2);
    final Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(S.class).toProvider(provider);
              }
            });

    Future<G> firstThreadResult =
        Executors.newSingleThreadExecutor()
            .submit(
                () -> {
                  Thread.currentThread().setName("G.class");
                  return injector.createChildInjector().getInstance(G.class);
                });
    Future<H> secondThreadResult =
        Executors.newSingleThreadExecutor()
            .submit(
                () -> {
                  Thread.currentThread().setName("H.class");
                  return injector.createChildInjector().getInstance(H.class);
                });

    // using separate threads to avoid potential deadlock on the main thread
    // waiting twice as much to be sure that both would time out in their respective barriers
    GImpl g = (GImpl) firstThreadResult.get(DEADLOCK_TIMEOUT_SECONDS * 3, TimeUnit.SECONDS);
    HImpl h = (HImpl) secondThreadResult.get(DEADLOCK_TIMEOUT_SECONDS * 3, TimeUnit.SECONDS);

    // Check that G and H created are not proxied
    assertTrue(!Scopes.isCircularProxy(g) && !Scopes.isCircularProxy(h));

    // Check that we have no more than one circular proxy created
    assertFalse(Scopes.isCircularProxy(g.h) && Scopes.isCircularProxy(h.g));

    // Check that non-proxy variable points to another singleton
    assertTrue(g.h == h || h.g == g);

    // Check correct proxy initialization as default equals implementation would
    assertEquals(g.h, h);
    assertEquals(h.g, g);
  }

  @Singleton
  static class I0 {

    /** Relies on Guice implementation to inject S first, which provides a barrier . */
    @Inject
    I0(I1 i) {}
  }

  @Singleton
  static class I1 {

    /** Relies on Guice implementation to inject S first, which provides a barrier . */
    @Inject
    I1(S synchronizationBarrier, I2 i) {}
  }

  @Singleton
  static class I2 {

    /** Relies on Guice implementation to inject S first, which provides a barrier . */
    @Inject
    I2(J1 j) {}
  }

  @Singleton
  static class J0 {

    /** Relies on Guice implementation to inject S first, which provides a barrier . */
    @Inject
    J0(J1 j) {}
  }

  @Singleton
  static class J1 {

    /** Relies on Guice implementation to inject S first, which provides a barrier . */
    @Inject
    J1(S synchronizationBarrier, J2 j) {}
  }

  @Singleton
  static class J2 {

    /** Relies on Guice implementation to inject S first, which provides a barrier . */
    @Inject
    J2(K1 k) {}
  }

  @Singleton
  static class K0 {

    /** Relies on Guice implementation to inject S first, which provides a barrier . */
    @Inject
    K0(K1 k) {}
  }

  @Singleton
  static class K1 {

    /** Relies on Guice implementation to inject S first, which provides a barrier . */
    @Inject
    K1(S synchronizationBarrier, K2 k) {}
  }

  @Singleton
  static class K2 {

    /** Relies on Guice implementation to inject S first, which provides a barrier . */
    @Inject
    K2(I1 i) {}
  }

  /**
   * Check that circular dependencies on non-interfaces are correctly resolved in multi-threaded
   * case. And that an error message constructed is a good one.
   *
   * <p>I0 -> I1 -> I2 -> J1 and J0 -> J1 -> J2 -> K1 and K0 -> K1 -> K2, where I1, J1 and K1 are
   * created in parallel.
   *
   * <p>Creation is synchronized by injection of {@link S}, first thread would block until second
   * would be inside a singleton creation as well.
   *
   * <p>Verifies that provision results in an error, that spans two threads and has a dependency
   * cycle.
   */

  public void testUnresolvableSingletonCircularDependencyErrorMessage() throws Exception {
    final Provider<S> provider = new SBarrierProvider(3);
    final Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(S.class).toProvider(provider);
              }
            });

    FutureTask<I0> firstThreadResult = new FutureTask<>(() -> injector.getInstance(I0.class));
    Thread i0Thread = new Thread(firstThreadResult, "I0.class");
    // we need to call toString() now, because the toString() changes after the thread exits.
    String i0ThreadString = i0Thread.toString();
    i0Thread.start();

    FutureTask<J0> secondThreadResult = new FutureTask<>(() -> injector.getInstance(J0.class));
    Thread j0Thread = new Thread(secondThreadResult, "J0.class");
    String j0ThreadString = j0Thread.toString();
    j0Thread.start();

    FutureTask<K0> thirdThreadResult = new FutureTask<>(() -> injector.getInstance(K0.class));
    Thread k0Thread = new Thread(thirdThreadResult, "K0.class");
    String k0ThreadString = k0Thread.toString();
    k0Thread.start();

    // using separate threads to avoid potential deadlock on the main thread
    // waiting twice as much to be sure that both would time out in their respective barriers
    Throwable firstException = null;
    Throwable secondException = null;
    Throwable thirdException = null;
    try {
      firstThreadResult.get(DEADLOCK_TIMEOUT_SECONDS * 3, TimeUnit.SECONDS);
      fail();
    } catch (ExecutionException e) {
      firstException = e.getCause();
    }
    try {
      secondThreadResult.get(DEADLOCK_TIMEOUT_SECONDS * 3, TimeUnit.SECONDS);
      fail();
    } catch (ExecutionException e) {
      secondException = e.getCause();
    }
    try {
      thirdThreadResult.get(DEADLOCK_TIMEOUT_SECONDS * 3, TimeUnit.SECONDS);
      fail();
    } catch (ExecutionException e) {
      thirdException = e.getCause();
    }

    // verification of error messages generated
    List<Message> errors = new ArrayList<>();
    errors.addAll(((ProvisionException) firstException).getErrorMessages());
    errors.addAll(((ProvisionException) secondException).getErrorMessages());
    errors.addAll(((ProvisionException) thirdException).getErrorMessages());
    // We want to find the longest error reported for a cycle spanning multiple threads
    Message spanningError = null;
    for (Message error : errors) {
      if (error.getMessage().contains("Encountered circular dependency spanning several threads")) {
        if (spanningError == null
            || spanningError.getMessage().length() < error.getMessage().length()) {
          spanningError = error;
        }
      }
    }
    if (spanningError == null) {
      fail(
          "Couldn't find multi thread circular dependency error: "
              + Joiner.on("\n\n").join(errors));
    }

    String errorMessage = spanningError.getMessage();
    assertContains(
        errorMessage,
        "Encountered circular dependency spanning several threads. Tried proxying "
            + this.getClass().getName());
    assertFalse(
        "Both I0 and J0 can not be a part of a dependency cycle",
        errorMessage.contains(I0.class.getName()) && errorMessage.contains(J0.class.getName()));
    assertFalse(
        "Both J0 and K0 can not be a part of a dependency cycle",
        errorMessage.contains(J0.class.getName()) && errorMessage.contains(K0.class.getName()));
    assertFalse(
        "Both K0 and I0 can not be a part of a dependency cycle",
        errorMessage.contains(K0.class.getName()) && errorMessage.contains(I0.class.getName()));

    ListMultimap<String, String> threadToSingletons = ArrayListMultimap.create();
    boolean inSingletonsList = false;
    String currentThread = null;
    for (String errorLine : errorMessage.split("\\n")) {
      if (errorLine.startsWith("Thread[")) {
        inSingletonsList = true;
        currentThread =
            errorLine.substring(
                0, errorLine.indexOf(" is holding locks the following singletons in the cycle:"));
      } else if (inSingletonsList) {
        if (errorLine.startsWith("\tat ")) {
          inSingletonsList = false;
        } else {
          threadToSingletons.put(currentThread, errorLine);
        }
      }
    }

    assertEquals("All threads should be in the cycle", 3, threadToSingletons.keySet().size());

    // NOTE:  J0,K0,I0 are not reported because their locks are not part of the cycle.
    assertEquals(
        threadToSingletons.get(j0ThreadString),
        ImmutableList.of(J1.class.getName(), J2.class.getName(), K1.class.getName()));
    assertEquals(
        threadToSingletons.get(k0ThreadString),
        ImmutableList.of(K1.class.getName(), K2.class.getName(), I1.class.getName()));
    assertEquals(
        threadToSingletons.get(i0ThreadString),
        ImmutableList.of(I1.class.getName(), I2.class.getName(), J1.class.getName()));
  }

  // Test for https://github.com/google/guice/issues/1032

  public void testScopeAppliedByUserInsteadOfScoping() throws Exception {
    Injector injector =
        java.util.concurrent.Executors.newSingleThreadExecutor()
            .submit(
                () ->
                    Guice.createInjector(
                        new AbstractModule() {
                          @Override
                          protected void configure() {
                            bindListener(Matchers.any(), new ScopeMutatingProvisionListener());
                            bind(SingletonClass.class);
                          }
                        }))
            .get();
    injector.getInstance(SingletonClass.class); // will fail here with NPE
  }

  @Singleton
  static class SingletonClass {}

  /** Uses Scope's public API to add a 'marker' into the provisioned instance's scope. */
  private static final class ScopeMutatingProvisionListener implements ProvisionListener {
    private static class ScopeMarker {
      static final Provider<ScopeMarker> PROVIDER =
          new Provider<ScopeMarker>() {
            @Override
            public ScopeMarker get() {
              return new ScopeMarker();
            }
          };
    }

    @Override
    public <T> void onProvision(final ProvisionInvocation<T> provisionInvocation) {
      provisionInvocation.provision();
      provisionInvocation
          .getBinding()
          .acceptScopingVisitor(
              new DefaultBindingScopingVisitor<Void>() {
                @Override
                public Void visitScope(Scope scope) {
                  scope.scope(Key.get(ScopeMarker.class), ScopeMarker.PROVIDER);
                  return null;
                }
              });
    }
  }

  public void testForInstanceOfNoScopingReturnsUnscoped() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(AImpl.class).in(Scopes.NO_SCOPE);
              }
            });

    assertTrue(
        injector
            .getBinding(Key.get(AImpl.class))
            .acceptScopingVisitor(
                new DefaultBindingScopingVisitor<Boolean>() {
                  @Override
                  protected Boolean visitOther() {
                    return false;
                  }

                  @Override
                  public Boolean visitNoScoping() {
                    return true;
                  }
                }));
  }

  public void testScopedLinkedBindingDoesNotPropagateEagerSingleton() {
    final Key<String> a = Key.get(String.class, named("A"));
    final Key<String> b = Key.get(String.class, named("B"));

    final Scope notInScopeScope =
        new Scope() {
          @Override
          public <T> Provider<T> scope(Key<T> key, Provider<T> unscoped) {
            return new Provider<T>() {
              @Override
              public T get() {
                throw new IllegalStateException("Not in scope");
              }
            };
          }
        };

    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(a).toInstance("a");
            bind(b).to(a).in(CustomScoped.class);
            bindScope(CustomScoped.class, notInScopeScope);
          }
        };

    Injector injector = Guice.createInjector(module);
    Provider<String> bProvider = injector.getProvider(b);
    try {
      bProvider.get();
      fail("expected failure");
    } catch (ProvisionException expected) {
    }
  }
}
