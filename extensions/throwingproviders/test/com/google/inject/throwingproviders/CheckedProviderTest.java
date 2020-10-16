/*
 * Copyright (C) 2007 Google Inc.
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

package com.google.inject.throwingproviders;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.inject.Asserts.assertContains;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.inject.AbstractModule;
import com.google.inject.Asserts;
import com.google.inject.BindingAnnotation;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Scope;
import com.google.inject.ScopeAnnotation;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.util.Classes;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.HasDependencies;
import com.google.inject.spi.Message;
import com.google.inject.throwingproviders.ThrowingProviderBinder.Result;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.BindException;
import java.rmi.AccessException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TooManyListenersException;
import junit.framework.TestCase;

/**
 * @author jmourits@google.com (Jerome Mourits)
 * @author jessewilson@google.com (Jesse Wilson)
 * @author sameb@google.com (Sam Berlin)
 */
public class CheckedProviderTest extends TestCase {
  @Target(METHOD)
  @Retention(RUNTIME)
  @BindingAnnotation
  @interface NotExceptionScoping {};

  private final TypeLiteral<RemoteProvider<Foo>> remoteProviderOfFoo =
      new TypeLiteral<RemoteProvider<Foo>>() {};
  private final MockRemoteProvider<Foo> mockRemoteProvider = new MockRemoteProvider<>();
  private final TestScope testScope = new TestScope();

  private Injector bindInjector;
  private Injector providesInjector;
  private Injector cxtorInjector;

  @Override
  protected void setUp() throws Exception {
    MockFoo.nextToThrow = null;
    MockFoo.nextToReturn = null;
    AnotherMockFoo.nextToThrow = null;
    AnotherMockFoo.nextToReturn = null;

    bindInjector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                ThrowingProviderBinder.create(binder())
                    .bind(RemoteProvider.class, Foo.class)
                    .to(mockRemoteProvider)
                    .in(testScope);

                ThrowingProviderBinder.create(binder())
                    .bind(RemoteProvider.class, Foo.class)
                    .annotatedWith(NotExceptionScoping.class)
                    .scopeExceptions(false)
                    .to(mockRemoteProvider)
                    .in(testScope);
              }
            });

    providesInjector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                install(ThrowingProviderBinder.forModule(this));
                bindScope(TestScope.Scoped.class, testScope);
              }

              @SuppressWarnings("unused")
              @CheckedProvides(RemoteProvider.class)
              @TestScope.Scoped
              Foo throwOrGet() throws RemoteException, BindException {
                return mockRemoteProvider.get();
              }

              @SuppressWarnings("unused")
              @CheckedProvides(value = RemoteProvider.class, scopeExceptions = false)
              @NotExceptionScoping
              @TestScope.Scoped
              Foo notExceptionScopingThrowOrGet() throws RemoteException, BindException {
                return mockRemoteProvider.get();
              }
            });

    cxtorInjector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                ThrowingProviderBinder.create(binder())
                    .bind(RemoteProvider.class, Foo.class)
                    .providing(MockFoo.class)
                    .in(testScope);

                ThrowingProviderBinder.create(binder())
                    .bind(RemoteProvider.class, Foo.class)
                    .annotatedWith(NotExceptionScoping.class)
                    .scopeExceptions(false)
                    .providing(MockFoo.class)
                    .in(testScope);
              }
            });
  }

  public void testExceptionsThrown_Bind() throws Exception {
    tExceptionsThrown(bindInjector);
  }

  public void testExceptionsThrown_Provides() throws Exception {
    tExceptionsThrown(providesInjector);
  }

  public void testExceptionsThrown_Cxtor() throws Exception {
    tExceptionsThrown(cxtorInjector);
  }

  private void tExceptionsThrown(Injector injector) throws Exception {
    RemoteProvider<Foo> remoteProvider = injector.getInstance(Key.get(remoteProviderOfFoo));

    mockRemoteProvider.throwOnNextGet(new BindException("kaboom!"));
    MockFoo.nextToThrow = new BindException("kaboom!");
    try {
      remoteProvider.get();
      fail();
    } catch (BindException expected) {
      assertEquals("kaboom!", expected.getMessage());
    }
  }

  public void testValuesScoped_Bind() throws Exception {
    tValuesScoped(bindInjector, null);
  }

  public void testValuesScoped_Provides() throws Exception {
    tValuesScoped(providesInjector, null);
  }

  public void testValuesScopedWhenNotExceptionScoping_Bind() throws Exception {
    tValuesScoped(bindInjector, NotExceptionScoping.class);
  }

  public void testValuesScopedWhenNotExceptionScoping_Provides() throws Exception {
    tValuesScoped(providesInjector, NotExceptionScoping.class);
  }

  private void tValuesScoped(Injector injector, Class<? extends Annotation> annotation)
      throws Exception {
    Key<RemoteProvider<Foo>> key =
        annotation != null
            ? Key.get(remoteProviderOfFoo, annotation)
            : Key.get(remoteProviderOfFoo);
    RemoteProvider<Foo> remoteProvider = injector.getInstance(key);

    mockRemoteProvider.setNextToReturn(new SimpleFoo("A"));
    assertEquals("A", remoteProvider.get().s());

    mockRemoteProvider.setNextToReturn(new SimpleFoo("B"));
    assertEquals("A", remoteProvider.get().s());

    testScope.beginNewScope();
    assertEquals("B", remoteProvider.get().s());
  }

  public void testValuesScoped_Cxtor() throws Exception {
    RemoteProvider<Foo> remoteProvider = cxtorInjector.getInstance(Key.get(remoteProviderOfFoo));

    Foo retrieved = remoteProvider.get();
    assertSame(retrieved, remoteProvider.get()); // same, not in new scope.

    testScope.beginNewScope();
    assertNotSame(retrieved, remoteProvider.get()); // different, new scope.
  }

  public void testExceptionsScoped_Bind() throws Exception {
    tExceptionsScoped(bindInjector);
  }

  public void testExceptionsScoped_Provides() throws Exception {
    tExceptionsScoped(providesInjector);
  }

  public void testExceptionScopes_Cxtor() throws Exception {
    tExceptionsScoped(cxtorInjector);
  }

  private void tExceptionsScoped(Injector injector) throws Exception {
    RemoteProvider<Foo> remoteProvider = injector.getInstance(Key.get(remoteProviderOfFoo));

    mockRemoteProvider.throwOnNextGet(new RemoteException("A"));
    MockFoo.nextToThrow = new RemoteException("A");
    try {
      remoteProvider.get();
      fail();
    } catch (RemoteException expected) {
      assertEquals("A", expected.getMessage());
    }

    mockRemoteProvider.throwOnNextGet(new RemoteException("B"));
    MockFoo.nextToThrow = new RemoteException("B");
    try {
      remoteProvider.get();
      fail();
    } catch (RemoteException expected) {
      assertEquals("A", expected.getMessage());
    }
  }

  public void testExceptionsNotScopedWhenNotExceptionScoping_Bind() throws Exception {
    tExceptionsNotScopedWhenNotExceptionScoping(bindInjector);
  }

  public void testExceptionsNotScopedWhenNotExceptionScoping_Provides() throws Exception {
    tExceptionsNotScopedWhenNotExceptionScoping(providesInjector);
  }

  public void testExceptionNotScopedWhenNotExceptionScoping_Cxtor() throws Exception {
    tExceptionsNotScopedWhenNotExceptionScoping(cxtorInjector);
  }

  private void tExceptionsNotScopedWhenNotExceptionScoping(Injector injector) throws Exception {
    RemoteProvider<Foo> remoteProvider =
        injector.getInstance(Key.get(remoteProviderOfFoo, NotExceptionScoping.class));

    mockRemoteProvider.throwOnNextGet(new RemoteException("A"));
    MockFoo.nextToThrow = new RemoteException("A");
    try {
      remoteProvider.get();
      fail();
    } catch (RemoteException expected) {
      assertEquals("A", expected.getMessage());
    }

    mockRemoteProvider.throwOnNextGet(new RemoteException("B"));
    MockFoo.nextToThrow = new RemoteException("B");
    try {
      remoteProvider.get();
      fail();
    } catch (RemoteException expected) {
      assertEquals("B", expected.getMessage());
    }
  }

  public void testAnnotations_Bind() throws Exception {
    final MockRemoteProvider<Foo> mockRemoteProviderA = new MockRemoteProvider<>();
    final MockRemoteProvider<Foo> mockRemoteProviderB = new MockRemoteProvider<>();
    bindInjector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                ThrowingProviderBinder.create(binder())
                    .bind(RemoteProvider.class, Foo.class)
                    .annotatedWith(Names.named("a"))
                    .to(mockRemoteProviderA);

                ThrowingProviderBinder.create(binder())
                    .bind(RemoteProvider.class, Foo.class)
                    .to(mockRemoteProviderB);
              }
            });
    tAnnotations(bindInjector, mockRemoteProviderA, mockRemoteProviderB);
  }

  public void testAnnotations_Provides() throws Exception {
    final MockRemoteProvider<Foo> mockRemoteProviderA = new MockRemoteProvider<>();
    final MockRemoteProvider<Foo> mockRemoteProviderB = new MockRemoteProvider<>();
    providesInjector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                install(ThrowingProviderBinder.forModule(this));
              }

              @SuppressWarnings("unused")
              @CheckedProvides(RemoteProvider.class)
              @Named("a")
              Foo throwOrGet() throws RemoteException, BindException {
                return mockRemoteProviderA.get();
              }

              @SuppressWarnings("unused")
              @CheckedProvides(RemoteProvider.class)
              Foo throwOrGet2() throws RemoteException, BindException {
                return mockRemoteProviderB.get();
              }
            });
    tAnnotations(providesInjector, mockRemoteProviderA, mockRemoteProviderB);
  }

  private void tAnnotations(
      Injector injector, MockRemoteProvider<Foo> mockA, MockRemoteProvider<Foo> mockB)
      throws Exception {
    mockA.setNextToReturn(new SimpleFoo("A"));
    mockB.setNextToReturn(new SimpleFoo("B"));
    assertEquals(
        "A", injector.getInstance(Key.get(remoteProviderOfFoo, Names.named("a"))).get().s());

    assertEquals("B", injector.getInstance(Key.get(remoteProviderOfFoo)).get().s());
  }

  public void testAnnotations_Cxtor() throws Exception {
    cxtorInjector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                ThrowingProviderBinder.create(binder())
                    .bind(RemoteProvider.class, Foo.class)
                    .annotatedWith(Names.named("a"))
                    .providing(MockFoo.class);

                ThrowingProviderBinder.create(binder())
                    .bind(RemoteProvider.class, Foo.class)
                    .providing(AnotherMockFoo.class);
              }
            });
    MockFoo.nextToReturn = "A";
    AnotherMockFoo.nextToReturn = "B";
    assertEquals(
        "A", cxtorInjector.getInstance(Key.get(remoteProviderOfFoo, Names.named("a"))).get().s());

    assertEquals("B", cxtorInjector.getInstance(Key.get(remoteProviderOfFoo)).get().s());
  }

  public void testUndeclaredExceptions_Bind() throws Exception {
    tUndeclaredExceptions(bindInjector);
  }

  public void testUndeclaredExceptions_Provides() throws Exception {
    tUndeclaredExceptions(providesInjector);
  }

  public void testUndeclaredExceptions_Cxtor() throws Exception {
    tUndeclaredExceptions(cxtorInjector);
  }

  private void tUndeclaredExceptions(Injector injector) throws Exception {
    RemoteProvider<Foo> remoteProvider = injector.getInstance(Key.get(remoteProviderOfFoo));
    mockRemoteProvider.throwOnNextGet(new IndexOutOfBoundsException("A"));
    MockFoo.nextToThrow = new IndexOutOfBoundsException("A");
    try {
      remoteProvider.get();
      fail();
    } catch (RuntimeException e) {
      assertEquals("A", e.getCause().getMessage());
    }

    // undeclared exceptions shouldn't be scoped
    mockRemoteProvider.throwOnNextGet(new IndexOutOfBoundsException("B"));
    MockFoo.nextToThrow = new IndexOutOfBoundsException("B");
    try {
      remoteProvider.get();
      fail();
    } catch (RuntimeException e) {
      assertEquals("B", e.getCause().getMessage());
    }
  }

  public void testThrowingProviderSubclassing() throws Exception {
    final SubMockRemoteProvider aProvider = new SubMockRemoteProvider();
    aProvider.setNextToReturn(new SimpleFoo("A"));

    bindInjector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                ThrowingProviderBinder.create(binder())
                    .bind(RemoteProvider.class, Foo.class)
                    .to(aProvider);
              }
            });

    assertEquals("A", bindInjector.getInstance(Key.get(remoteProviderOfFoo)).get().s());
  }

  static class SubMockRemoteProvider extends MockRemoteProvider<Foo> {}

  public void testBindingToNonInterfaceType_Bind() throws Exception {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              ThrowingProviderBinder.create(binder())
                  .bind(MockRemoteProvider.class, Foo.class)
                  .to(mockRemoteProvider);
            }
          });
      fail();
    } catch (CreationException expected) {
      assertEquals(
          MockRemoteProvider.class.getName() + " must be an interface",
          Iterables.getOnlyElement(expected.getErrorMessages()).getMessage());
    }
  }

  public void testBindingToNonInterfaceType_Provides() throws Exception {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              install(ThrowingProviderBinder.forModule(this));
            }

            @SuppressWarnings("unused")
            @CheckedProvides(MockRemoteProvider.class)
            Foo foo() {
              return null;
            }
          });
      fail();
    } catch (CreationException expected) {
      assertEquals(
          MockRemoteProvider.class.getName() + " must be an interface",
          Iterables.getOnlyElement(expected.getErrorMessages()).getMessage());
    }
  }

  public void testBindingToSubSubInterface_Bind() throws Exception {
    try {
      bindInjector =
          Guice.createInjector(
              new AbstractModule() {
                @Override
                protected void configure() {
                  ThrowingProviderBinder.create(binder()).bind(SubRemoteProvider.class, Foo.class);
                }
              });
      fail();
    } catch (CreationException expected) {
      assertEquals(
          SubRemoteProvider.class.getName()
              + " must extend CheckedProvider (and only CheckedProvider)",
          Iterables.getOnlyElement(expected.getErrorMessages()).getMessage());
    }
  }

  public void testBindingToSubSubInterface_Provides() throws Exception {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              install(ThrowingProviderBinder.forModule(this));
            }

            @SuppressWarnings("unused")
            @CheckedProvides(SubRemoteProvider.class)
            Foo foo() {
              return null;
            }
          });
      fail();
    } catch (CreationException expected) {
      assertEquals(
          SubRemoteProvider.class.getName()
              + " must extend CheckedProvider (and only CheckedProvider)",
          Iterables.getOnlyElement(expected.getErrorMessages()).getMessage());
    }
  }

  interface SubRemoteProvider extends RemoteProvider<String> {}

  public void testBindingToInterfaceWithExtraMethod_Bind() throws Exception {
    try {
      bindInjector =
          Guice.createInjector(
              new AbstractModule() {
                @Override
                protected void configure() {
                  ThrowingProviderBinder.create(binder())
                      .bind(RemoteProviderWithExtraMethod.class, Foo.class);
                }
              });
      fail();
    } catch (CreationException expected) {
      assertEquals(
          RemoteProviderWithExtraMethod.class.getName()
              + " may not declare any new methods, but declared "
              + RemoteProviderWithExtraMethod.class.getDeclaredMethods()[0].toGenericString(),
          Iterables.getOnlyElement(expected.getErrorMessages()).getMessage());
    }
  }

  public void testBindingToInterfaceWithExtraMethod_Provides() throws Exception {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              install(ThrowingProviderBinder.forModule(this));
            }

            @SuppressWarnings("unused")
            @CheckedProvides(RemoteProviderWithExtraMethod.class)
            Foo foo() {
              return null;
            }
          });
      fail();
    } catch (CreationException expected) {
      assertEquals(
          RemoteProviderWithExtraMethod.class.getName()
              + " may not declare any new methods, but declared "
              + RemoteProviderWithExtraMethod.class.getDeclaredMethods()[0].toGenericString(),
          Iterables.getOnlyElement(expected.getErrorMessages()).getMessage());
    }
  }

  public void testDependencies_Bind() {
    bindInjector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(String.class).toInstance("Foo");
                bind(Integer.class).toInstance(5);
                bind(Double.class).toInstance(5d);
                bind(Long.class).toInstance(5L);
                ThrowingProviderBinder.create(binder())
                    .bind(RemoteProvider.class, Foo.class)
                    .to(DependentRemoteProvider.class);
              }
            });

    HasDependencies hasDependencies =
        (HasDependencies) bindInjector.getBinding(Key.get(remoteProviderOfFoo));
    hasDependencies =
        (HasDependencies)
            bindInjector.getBinding(
                Iterables.getOnlyElement(hasDependencies.getDependencies()).getKey());
    // Make sure that that is dependent on DependentRemoteProvider.
    assertEquals(
        Dependency.get(Key.get(DependentRemoteProvider.class)),
        Iterables.getOnlyElement(hasDependencies.getDependencies()));
    // And make sure DependentRemoteProvider has the proper dependencies.
    hasDependencies = (HasDependencies) bindInjector.getBinding(DependentRemoteProvider.class);
    Set<Key<?>> dependencyKeys =
        hasDependencies.getDependencies().stream()
            .map(Dependency::getKey)
            .collect(toImmutableSet());
    assertEquals(
        ImmutableSet.<Key<?>>of(
            Key.get(String.class),
            Key.get(Integer.class),
            Key.get(Long.class),
            Key.get(Double.class)),
        dependencyKeys);
  }

  public void testDependencies_Provides() {
    providesInjector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(String.class).toInstance("Foo");
                bind(Integer.class).toInstance(5);
                bind(Double.class).toInstance(5d);
                bind(Long.class).toInstance(5L);
                install(ThrowingProviderBinder.forModule(this));
              }

              @SuppressWarnings("unused")
              @CheckedProvides(RemoteProvider.class)
              Foo foo(String s, Integer i, Double d, Long l) {
                return null;
              }
            });

    HasDependencies hasDependencies =
        (HasDependencies) providesInjector.getBinding(Key.get(remoteProviderOfFoo));
    // RemoteProvider<String> is dependent on the provider method..
    hasDependencies =
        (HasDependencies)
            providesInjector.getBinding(
                Iterables.getOnlyElement(hasDependencies.getDependencies()).getKey());
    // And the provider method has our real dependencies..
    hasDependencies =
        (HasDependencies)
            providesInjector.getBinding(
                Iterables.getOnlyElement(hasDependencies.getDependencies()).getKey());
    Set<Key<?>> dependencyKeys =
        hasDependencies.getDependencies().stream()
            .map(Dependency::getKey)
            .collect(toImmutableSet());
    assertEquals(
        ImmutableSet.<Key<?>>of(
            Key.get(String.class),
            Key.get(Integer.class),
            Key.get(Long.class),
            Key.get(Double.class)),
        dependencyKeys);
  }

  public void testDependencies_Cxtor() {
    cxtorInjector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(String.class).toInstance("Foo");
                bind(Integer.class).toInstance(5);
                bind(Double.class).toInstance(5d);
                bind(Long.class).toInstance(5L);
                ThrowingProviderBinder.create(binder())
                    .bind(RemoteProvider.class, Foo.class)
                    .providing(DependentMockFoo.class);
              }
            });

    Key<?> key = Key.get(remoteProviderOfFoo);

    // RemoteProvider<String> is dependent on Result.
    HasDependencies hasDependencies = (HasDependencies) cxtorInjector.getBinding(key);
    key = Iterables.getOnlyElement(hasDependencies.getDependencies()).getKey();
    assertEquals(Result.class, key.getTypeLiteral().getRawType());

    // Result is dependent on the fake CheckedProvider impl
    hasDependencies = (HasDependencies) cxtorInjector.getBinding(key);
    key = Iterables.getOnlyElement(hasDependencies.getDependencies()).getKey();
    assertTrue(CheckedProvider.class.isAssignableFrom(key.getTypeLiteral().getRawType()));

    // And the CheckedProvider is dependent on DependentMockFoo...
    hasDependencies = (HasDependencies) cxtorInjector.getBinding(key);
    key = Iterables.getOnlyElement(hasDependencies.getDependencies()).getKey();
    assertEquals(DependentMockFoo.class, key.getTypeLiteral().getRawType());

    // And DependentMockFoo is dependent on the goods.
    hasDependencies = (HasDependencies) cxtorInjector.getBinding(key);
    Set<Key<?>> dependencyKeys =
        hasDependencies.getDependencies().stream()
            .map(Dependency::getKey)
            .collect(toImmutableSet());
    assertEquals(
        ImmutableSet.<Key<?>>of(
            Key.get(String.class),
            Key.get(Integer.class),
            Key.get(Long.class),
            Key.get(Double.class)),
        dependencyKeys);
  }

  interface RemoteProviderWithExtraMethod<T> extends CheckedProvider<T> {
    T get(T defaultValue) throws RemoteException, BindException;
  }

  interface RemoteProvider<T> extends CheckedProvider<T> {
    @Override
    public T get() throws RemoteException, BindException;
  }

  static class DependentMockFoo implements Foo {
    @Inject double foo;

    @ThrowingInject
    public DependentMockFoo(String foo, int bar) {}

    @Inject
    void initialize(long foo) {}

    @Override
    public String s() {
      return null;
    }
  }

  static class DependentRemoteProvider<T> implements RemoteProvider<T> {
    @Inject double foo;

    @Inject
    public DependentRemoteProvider(String foo, int bar) {}

    @Inject
    void initialize(long foo) {}

    @Override
    public T get() {
      return null;
    }
  }

  interface Foo {
    String s();
  }

  static class SimpleFoo implements Foo {
    private String s;

    SimpleFoo(String s) {
      this.s = s;
    }

    @Override
    public String s() {
      return s;
    }

    @Override
    public String toString() {
      return s;
    }
  }

  static class MockFoo implements Foo {
    static Exception nextToThrow;
    static String nextToReturn;

    @ThrowingInject
    MockFoo() throws RemoteException, BindException {
      if (nextToThrow instanceof RemoteException) {
        throw (RemoteException) nextToThrow;
      } else if (nextToThrow instanceof BindException) {
        throw (BindException) nextToThrow;
      } else if (nextToThrow instanceof RuntimeException) {
        throw (RuntimeException) nextToThrow;
      } else if (nextToThrow == null) {
        // Do nothing, return this.
      } else {
        throw new AssertionError("nextToThrow must be a runtime or remote exception");
      }
    }

    @Override
    public String s() {
      return nextToReturn;
    }

    @Override
    public String toString() {
      return nextToReturn;
    }
  }

  static class AnotherMockFoo implements Foo {
    static Exception nextToThrow;
    static String nextToReturn;

    @ThrowingInject
    AnotherMockFoo() throws RemoteException, BindException {
      if (nextToThrow instanceof RemoteException) {
        throw (RemoteException) nextToThrow;
      } else if (nextToThrow instanceof BindException) {
        throw (BindException) nextToThrow;
      } else if (nextToThrow instanceof RuntimeException) {
        throw (RuntimeException) nextToThrow;
      } else if (nextToThrow == null) {
        // Do nothing, return this.
      } else {
        throw new AssertionError("nextToThrow must be a runtime or remote exception");
      }
    }

    @Override
    public String s() {
      return nextToReturn;
    }

    @Override
    public String toString() {
      return nextToReturn;
    }
  }

  static class MockRemoteProvider<T> implements RemoteProvider<T> {
    Exception nextToThrow;
    T nextToReturn;

    public void throwOnNextGet(Exception nextToThrow) {
      this.nextToThrow = nextToThrow;
    }

    public void setNextToReturn(T nextToReturn) {
      this.nextToReturn = nextToReturn;
    }

    @Override
    public T get() throws RemoteException, BindException {
      if (nextToThrow instanceof RemoteException) {
        throw (RemoteException) nextToThrow;
      } else if (nextToThrow instanceof BindException) {
        throw (BindException) nextToThrow;
      } else if (nextToThrow instanceof RuntimeException) {
        throw (RuntimeException) nextToThrow;
      } else if (nextToThrow == null) {
        return nextToReturn;
      } else {
        throw new AssertionError("nextToThrow must be a runtime or remote exception");
      }
    }
  }

  public void testBindingToInterfaceWithBoundValueType_Bind() throws RemoteException {
    bindInjector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                ThrowingProviderBinder.create(binder())
                    .bind(StringRemoteProvider.class, String.class)
                    .to(
                        new StringRemoteProvider() {
                          @Override
                          public String get() {
                            return "A";
                          }
                        });
              }
            });

    assertEquals("A", bindInjector.getInstance(StringRemoteProvider.class).get());
  }

  public void testBindingToInterfaceWithBoundValueType_Provides() throws RemoteException {
    providesInjector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                install(ThrowingProviderBinder.forModule(this));
              }

              @SuppressWarnings("unused")
              @CheckedProvides(StringRemoteProvider.class)
              String foo() throws RemoteException {
                return "A";
              }
            });

    assertEquals("A", providesInjector.getInstance(StringRemoteProvider.class).get());
  }

  interface StringRemoteProvider extends CheckedProvider<String> {
    @Override
    String get() throws RemoteException;
  }

  @SuppressWarnings("deprecation")
  public void testBindingToInterfaceWithGeneric_Bind() throws Exception {
    bindInjector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                ThrowingProviderBinder.create(binder())
                    .bind(RemoteProvider.class, new TypeLiteral<List<String>>() {}.getType())
                    .to(
                        new RemoteProvider<List<String>>() {
                          @Override
                          public List<String> get() {
                            return Arrays.asList("A", "B");
                          }
                        });
              }
            });

    Key<RemoteProvider<List<String>>> key =
        Key.get(new TypeLiteral<RemoteProvider<List<String>>>() {});
    assertEquals(Arrays.asList("A", "B"), bindInjector.getInstance(key).get());
  }

  public void testBindingToInterfaceWithGeneric_BindUsingTypeLiteral() throws Exception {
    bindInjector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                ThrowingProviderBinder.create(binder())
                    .bind(RemoteProvider.class, new TypeLiteral<List<String>>() {})
                    .to(
                        new RemoteProvider<List<String>>() {
                          @Override
                          public List<String> get() {
                            return Arrays.asList("A", "B");
                          }
                        });
              }
            });

    Key<RemoteProvider<List<String>>> key =
        Key.get(new TypeLiteral<RemoteProvider<List<String>>>() {});
    assertEquals(Arrays.asList("A", "B"), bindInjector.getInstance(key).get());
  }

  public void testBindingToInterfaceWithGeneric_Provides() throws Exception {
    providesInjector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                install(ThrowingProviderBinder.forModule(this));
              }

              @SuppressWarnings("unused")
              @CheckedProvides(RemoteProvider.class)
              List<String> foo() throws RemoteException {
                return Arrays.asList("A", "B");
              }
            });

    Key<RemoteProvider<List<String>>> key =
        Key.get(new TypeLiteral<RemoteProvider<List<String>>>() {});
    assertEquals(Arrays.asList("A", "B"), providesInjector.getInstance(key).get());
  }

  public void testBindingToInterfaceWithGeneric_Cxtor() throws Exception {
    cxtorInjector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                ThrowingProviderBinder.create(binder())
                    .bind(RemoteProvider.class, new TypeLiteral<List<String>>() {})
                    .providing(new TypeLiteral<ThrowingArrayList<String>>() {});
              }
            });

    Key<RemoteProvider<List<String>>> key =
        Key.get(new TypeLiteral<RemoteProvider<List<String>>>() {});
    assertEquals(Arrays.asList(), cxtorInjector.getInstance(key).get());
  }

  private static class ThrowingArrayList<T> extends ArrayList<T> {
    @SuppressWarnings("unused")
    @ThrowingInject
    ThrowingArrayList() {}
  }

  public void testProviderMethodWithWrongException() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              install(ThrowingProviderBinder.forModule(this));
            }

            @SuppressWarnings("unused")
            @CheckedProvides(RemoteProvider.class)
            String foo() throws InterruptedException {
              return null;
            }
          });
      fail();
    } catch (CreationException ce) {
      assertEquals(
          InterruptedException.class.getName()
              + " is not compatible with the exceptions (["
              + RemoteException.class
              + ", "
              + BindException.class
              + "]) declared in the CheckedProvider interface ("
              + RemoteProvider.class.getName()
              + ")",
          Iterables.getOnlyElement(ce.getErrorMessages()).getMessage());
    }
  }

  public void testCxtorWithWrongException() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              ThrowingProviderBinder.create(binder())
                  .bind(RemoteProvider.class, Foo.class)
                  .providing(WrongExceptionFoo.class);
            }
          });
      fail();
    } catch (CreationException ce) {
      assertEquals(
          InterruptedException.class.getName()
              + " is not compatible with the exceptions (["
              + RemoteException.class
              + ", "
              + BindException.class
              + "]) declared in the CheckedProvider interface ("
              + RemoteProvider.class.getName()
              + ")",
          Iterables.getOnlyElement(ce.getErrorMessages()).getMessage());
    }
  }

  static class WrongExceptionFoo implements Foo {
    @SuppressWarnings("unused")
    @ThrowingInject
    public WrongExceptionFoo() throws InterruptedException {}

    @Override
    public String s() {
      return null;
    }
  }

  public void testProviderMethodWithSubclassOfExceptionIsOk() throws Exception {
    providesInjector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                install(ThrowingProviderBinder.forModule(this));
              }

              @SuppressWarnings("unused")
              @CheckedProvides(RemoteProvider.class)
              Foo foo() throws AccessException {
                throw new AccessException("boo!");
              }
            });

    RemoteProvider<Foo> remoteProvider = providesInjector.getInstance(Key.get(remoteProviderOfFoo));

    try {
      remoteProvider.get();
      fail();
    } catch (RemoteException expected) {
      assertTrue(expected instanceof AccessException);
      assertEquals("boo!", expected.getMessage());
    }
  }

  public void testCxtorWithSubclassOfExceptionIsOk() throws Exception {
    cxtorInjector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                ThrowingProviderBinder.create(binder())
                    .bind(RemoteProvider.class, Foo.class)
                    .providing(SubclassExceptionFoo.class);
              }
            });

    RemoteProvider<Foo> remoteProvider = cxtorInjector.getInstance(Key.get(remoteProviderOfFoo));

    try {
      remoteProvider.get();
      fail();
    } catch (RemoteException expected) {
      assertTrue(expected instanceof AccessException);
      assertEquals("boo!", expected.getMessage());
    }
  }

  static class SubclassExceptionFoo implements Foo {
    @ThrowingInject
    public SubclassExceptionFoo() throws AccessException {
      throw new AccessException("boo!");
    }

    @Override
    public String s() {
      return null;
    }
  }

  public void testProviderMethodWithSuperclassExceptionFails() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              install(ThrowingProviderBinder.forModule(this));
            }

            @SuppressWarnings("unused")
            @CheckedProvides(RemoteProvider.class)
            Foo foo() throws IOException {
              return null;
            }
          });
      fail();
    } catch (CreationException ce) {
      assertEquals(
          IOException.class.getName()
              + " is not compatible with the exceptions (["
              + RemoteException.class
              + ", "
              + BindException.class
              + "]) declared in the CheckedProvider interface ("
              + RemoteProvider.class.getName()
              + ")",
          Iterables.getOnlyElement(ce.getErrorMessages()).getMessage());
    }
  }

  public void testCxtorWithSuperclassExceptionFails() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              ThrowingProviderBinder.create(binder())
                  .bind(RemoteProvider.class, Foo.class)
                  .providing(SuperclassExceptionFoo.class);
            }
          });
      fail();
    } catch (CreationException ce) {
      assertEquals(
          IOException.class.getName()
              + " is not compatible with the exceptions (["
              + RemoteException.class
              + ", "
              + BindException.class
              + "]) declared in the CheckedProvider interface ("
              + RemoteProvider.class.getName()
              + ")",
          Iterables.getOnlyElement(ce.getErrorMessages()).getMessage());
    }
  }

  static class SuperclassExceptionFoo implements Foo {
    @SuppressWarnings("unused")
    @ThrowingInject
    public SuperclassExceptionFoo() throws IOException {}

    @Override
    public String s() {
      return null;
    }
  }

  public void testProviderMethodWithRuntimeExceptionsIsOk() throws Exception {
    providesInjector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                install(ThrowingProviderBinder.forModule(this));
              }

              @SuppressWarnings("unused")
              @CheckedProvides(RemoteProvider.class)
              Foo foo() throws RuntimeException {
                throw new RuntimeException("boo!");
              }
            });

    RemoteProvider<Foo> remoteProvider = providesInjector.getInstance(Key.get(remoteProviderOfFoo));

    try {
      remoteProvider.get();
      fail();
    } catch (RuntimeException expected) {
      assertEquals("boo!", expected.getCause().getMessage());
    }
  }

  public void testCxtorWithRuntimeExceptionsIsOk() throws Exception {
    cxtorInjector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                ThrowingProviderBinder.create(binder())
                    .bind(RemoteProvider.class, Foo.class)
                    .providing(RuntimeExceptionFoo.class);
              }
            });

    RemoteProvider<Foo> remoteProvider = cxtorInjector.getInstance(Key.get(remoteProviderOfFoo));

    try {
      remoteProvider.get();
      fail();
    } catch (RuntimeException expected) {
      assertEquals("boo!", expected.getCause().getMessage());
    }
  }

  static class RuntimeExceptionFoo implements Foo {
    @ThrowingInject
    public RuntimeExceptionFoo() throws RuntimeException {
      throw new RuntimeException("boo!");
    }

    @Override
    public String s() {
      return null;
    }
  }

  private static class SubBindException extends BindException {}

  public void testProviderMethodWithManyExceptions() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              install(ThrowingProviderBinder.forModule(this));
            }

            @SuppressWarnings("unused")
            @CheckedProvides(RemoteProvider.class)
            String foo()
                throws InterruptedException, RuntimeException, RemoteException, AccessException,
                    TooManyListenersException, BindException, SubBindException {
              return null;
            }
          });
      fail();
    } catch (CreationException ce) {
      // The only two that should fail are Interrupted & TooManyListeners.. the rest are OK.
      List<Message> errors = ImmutableList.copyOf(ce.getErrorMessages());
      assertEquals(
          InterruptedException.class.getName()
              + " is not compatible with the exceptions (["
              + RemoteException.class
              + ", "
              + BindException.class
              + "]) declared in the CheckedProvider interface ("
              + RemoteProvider.class.getName()
              + ")",
          errors.get(0).getMessage());
      assertEquals(
          TooManyListenersException.class.getName()
              + " is not compatible with the exceptions (["
              + RemoteException.class
              + ", "
              + BindException.class
              + "]) declared in the CheckedProvider interface ("
              + RemoteProvider.class.getName()
              + ")",
          errors.get(1).getMessage());
      assertEquals(2, errors.size());
    }
  }

  public void testCxtorWithManyExceptions() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              ThrowingProviderBinder.create(binder())
                  .bind(RemoteProvider.class, Foo.class)
                  .providing(ManyExceptionFoo.class);
            }
          });
      fail();
    } catch (CreationException ce) {
      // The only two that should fail are Interrupted & TooManyListeners.. the rest are OK.
      List<Message> errors = ImmutableList.copyOf(ce.getErrorMessages());
      assertEquals(
          InterruptedException.class.getName()
              + " is not compatible with the exceptions (["
              + RemoteException.class
              + ", "
              + BindException.class
              + "]) declared in the CheckedProvider interface ("
              + RemoteProvider.class.getName()
              + ")",
          errors.get(0).getMessage());
      assertEquals(
          TooManyListenersException.class.getName()
              + " is not compatible with the exceptions (["
              + RemoteException.class
              + ", "
              + BindException.class
              + "]) declared in the CheckedProvider interface ("
              + RemoteProvider.class.getName()
              + ")",
          errors.get(1).getMessage());
      assertEquals(2, errors.size());
    }
  }

  static class ManyExceptionFoo implements Foo {
    @SuppressWarnings("unused")
    @ThrowingInject
    public ManyExceptionFoo()
        throws InterruptedException, RuntimeException, RemoteException, AccessException,
            TooManyListenersException, BindException, SubBindException {}

    @Override
    public String s() {
      return null;
    }
  }

  public void testMoreTypeParameters() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              install(ThrowingProviderBinder.forModule(this));
            }

            @SuppressWarnings("unused")
            @CheckedProvides(TooManyTypeParameters.class)
            String foo() {
              return null;
            }
          });
      fail();
    } catch (CreationException ce) {
      assertEquals(
          TooManyTypeParameters.class.getName()
              + " has more than one generic type parameter: [T, P]",
          Iterables.getOnlyElement(ce.getErrorMessages()).getMessage());
    }
  }

  public void testWrongThrowingProviderType() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              install(ThrowingProviderBinder.forModule(this));
            }

            @SuppressWarnings("unused")
            @CheckedProvides(WrongThrowingProviderType.class)
            String foo() {
              return null;
            }
          });
      fail();
    } catch (CreationException ce) {
      assertEquals(
          WrongThrowingProviderType.class.getName()
              + " does not properly extend CheckedProvider, the first type parameter of"
              + " CheckedProvider (java.lang.String) is not a generic type",
          Iterables.getOnlyElement(ce.getErrorMessages()).getMessage());
    }
  }

  public void testOneMethodThatIsntGet() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              install(ThrowingProviderBinder.forModule(this));
            }

            @SuppressWarnings("unused")
            @CheckedProvides(OneNoneGetMethod.class)
            String foo() {
              return null;
            }
          });
      fail();
    } catch (CreationException ce) {
      assertEquals(
          OneNoneGetMethod.class.getName()
              + " may not declare any new methods, but declared "
              + Classes.toString(OneNoneGetMethod.class.getDeclaredMethods()[0]),
          Iterables.getOnlyElement(ce.getErrorMessages()).getMessage());
    }
  }

  public void testManyMethods() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              install(ThrowingProviderBinder.forModule(this));
            }

            @SuppressWarnings("unused")
            @CheckedProvides(ManyMethods.class)
            String foo() {
              return null;
            }
          });
      fail();
    } catch (CreationException ce) {
      assertEquals(
          ManyMethods.class.getName()
              + " may not declare any new methods, but declared "
              + Arrays.asList(ManyMethods.class.getDeclaredMethods()),
          Iterables.getOnlyElement(ce.getErrorMessages()).getMessage());
    }
  }

  public void testIncorrectPredefinedType_Bind() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              ThrowingProviderBinder.create(binder())
                  .bind(StringRemoteProvider.class, Integer.class)
                  .to(
                      new StringRemoteProvider() {
                        @Override
                        public String get() {
                          return "A";
                        }
                      });
            }
          });
      fail();
    } catch (CreationException ce) {
      assertEquals(
          StringRemoteProvider.class.getName()
              + " expects the value type to be java.lang.String, but it was java.lang.Integer",
          Iterables.getOnlyElement(ce.getErrorMessages()).getMessage());
    }
  }

  public void testIncorrectPredefinedType_Provides() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              install(ThrowingProviderBinder.forModule(this));
            }

            @SuppressWarnings("unused")
            @CheckedProvides(StringRemoteProvider.class)
            Integer foo() {
              return null;
            }
          });
      fail();
    } catch (CreationException ce) {
      assertEquals(
          StringRemoteProvider.class.getName()
              + " expects the value type to be java.lang.String, but it was java.lang.Integer",
          Iterables.getOnlyElement(ce.getErrorMessages()).getMessage());
    }
  }

  private static interface TooManyTypeParameters<T, P> extends CheckedProvider<T> {}

  private static interface WrongThrowingProviderType<T> extends CheckedProvider<String> {}

  private static interface OneNoneGetMethod<T> extends CheckedProvider<T> {
    T bar();
  }

  private static interface ManyMethods<T> extends CheckedProvider<T> {
    T bar();

    String baz();
  }

  public void testResultSerializes() throws Exception {
    Result result = Result.forValue("foo");
    result = Asserts.reserialize(result);
    assertEquals("foo", result.getOrThrow());
  }

  public void testResultExceptionSerializes() throws Exception {
    Result result = Result.forException(new Exception("boo"));
    result = Asserts.reserialize(result);
    try {
      result.getOrThrow();
      fail();
    } catch (Exception ex) {
      assertEquals("boo", ex.getMessage());
    }
  }

  public void testEarlyBindingError() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              ThrowingProviderBinder.create(binder())
                  .bind(StringRemoteProvider.class, String.class)
                  .to(FailingProvider.class);
            }
          });
      fail();
    } catch (CreationException ce) {
      assertContains(
          Iterables.getOnlyElement(ce.getErrorMessages()).getMessage(),
          "No implementation for "
              + FailingProvider.class.getName()
              + " (with no qualifier annotation) was bound");
    }
  }

  private static class FailingProvider implements StringRemoteProvider {
    // no @Inject.
    @SuppressWarnings("unused")
    FailingProvider(Integer foo) {}

    @Override
    public String get() {
      return null;
    }
  }

  public void testNoInjectionPointForUsing() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              ThrowingProviderBinder.create(binder())
                  .bind(RemoteProvider.class, Foo.class)
                  .providing(InvalidFoo.class);
            }
          });
      fail();
    } catch (CreationException ce) {
      assertEquals(
          "Could not find a suitable constructor in "
              + InvalidFoo.class.getName()
              + ". Classes must have either one (and only one) constructor annotated with "
              + "@ThrowingInject.",
          Iterables.getOnlyElement(ce.getErrorMessages()).getMessage());
    }
  }

  static class InvalidFoo implements Foo {
    public InvalidFoo(String dep) {}

    @Override
    public String s() {
      return null;
    }
  }

  public void testNoThrowingInject() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              ThrowingProviderBinder.create(binder())
                  .bind(RemoteProvider.class, Foo.class)
                  .providing(NormalInjectableFoo.class);
            }
          });
      fail();
    } catch (CreationException ce) {
      assertEquals(
          "Could not find a suitable constructor in "
              + NormalInjectableFoo.class.getName()
              + ". Classes must have either one (and only one) constructor annotated with "
              + "@ThrowingInject.",
          Iterables.getOnlyElement(ce.getErrorMessages()).getMessage());
    }
  }

  static class NormalInjectableFoo implements Foo {
    @Inject
    public NormalInjectableFoo() {}

    @Override
    public String s() {
      return null;
    }
  }

  public void testProvisionExceptionOnDependenciesOfCxtor() throws Exception {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                ThrowingProviderBinder.create(binder())
                    .bind(RemoteProvider.class, Foo.class)
                    .providing(ProvisionExceptionFoo.class);
                bindScope(
                    BadScope.class,
                    new Scope() {
                      @Override
                      public <T> Provider<T> scope(final Key<T> key, Provider<T> unscoped) {
                        return new Provider<T>() {
                          @Override
                          public T get() {
                            throw new OutOfScopeException("failure: " + key.toString());
                          }
                        };
                      }
                    });
              }
            });

    try {
      injector.getInstance(Key.get(remoteProviderOfFoo)).get();
      fail();
    } catch (ProvisionException pe) {
      Message message = Iterables.getOnlyElement(pe.getErrorMessages());
      assertEquals(
          "Error in custom provider, com.google.inject.OutOfScopeException: failure: "
              + Key.get(Unscoped1.class),
          message.getMessage());
    }
  }

  @ScopeAnnotation
  @Target({TYPE, METHOD})
  @Retention(RetentionPolicy.RUNTIME)
  private @interface BadScope {}

  @BadScope
  private static class Unscoped1 {}

  @BadScope
  private static class Unscoped2 {}

  static class ProvisionExceptionFoo implements Foo {
    @ThrowingInject
    public ProvisionExceptionFoo(Unscoped1 a, Unscoped2 b) {}

    @Override
    public String s() {
      return null;
    }
  }

  public void testUsingDoesntClashWithBindingsOfSameType() throws Exception {
    cxtorInjector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                ThrowingProviderBinder.create(binder())
                    .bind(RemoteProvider.class, Foo.class)
                    .providing(MockFoo.class);
                bind(Foo.class).to(MockFoo.class);
                bind(MockFoo.class).to(SubMockFoo.class);
              }
            });

    RemoteProvider<Foo> remoteProvider = cxtorInjector.getInstance(Key.get(remoteProviderOfFoo));
    Foo providerGot = remoteProvider.get();
    Foo fooGot = cxtorInjector.getInstance(Foo.class);
    Foo mockGot = cxtorInjector.getInstance(MockFoo.class);

    assertEquals(MockFoo.class, providerGot.getClass());
    assertEquals(SubMockFoo.class, fooGot.getClass());
    assertEquals(SubMockFoo.class, mockGot.getClass());
  }

  static class SubMockFoo extends MockFoo {
    public SubMockFoo() throws RemoteException, BindException {}
  }
}
