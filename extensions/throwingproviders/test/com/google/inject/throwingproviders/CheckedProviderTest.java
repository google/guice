/**
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

import com.google.inject.AbstractModule;

import com.google.inject.Asserts;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.util.Classes;
import com.google.inject.internal.util.Function;
import com.google.inject.internal.util.ImmutableList;
import com.google.inject.internal.util.ImmutableSet;
import com.google.inject.internal.util.Iterables;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.HasDependencies;
import com.google.inject.spi.Message;
import com.google.inject.throwingproviders.ThrowingProviderBinder.Result;

import java.io.IOException;
import java.net.BindException;
import java.rmi.AccessException;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TooManyListenersException;

import junit.framework.TestCase;

/**
 * @author jmourits@google.com (Jerome Mourits)
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class CheckedProviderTest extends TestCase {

  private final TypeLiteral<RemoteProvider<String>> remoteProviderOfString
      = new TypeLiteral<RemoteProvider<String>>() { };
  private final MockRemoteProvider<String> mockRemoteProvider = new MockRemoteProvider<String>();
  private final TestScope testScope = new TestScope();
  private Injector bindInjector = Guice.createInjector(new AbstractModule() {
    protected void configure() {
      ThrowingProviderBinder.create(binder())
          .bind(RemoteProvider.class, String.class)
          .to(mockRemoteProvider)
          .in(testScope);
    }
  });
  private Injector providesInjector = Guice.createInjector(new AbstractModule() {
    protected void configure() {
     install(ThrowingProviderBinder.forModule(this));
     bindScope(TestScope.Scoped.class, testScope);
    }
    
    @SuppressWarnings("unused")
    @CheckedProvides(RemoteProvider.class)
    @TestScope.Scoped
    String throwOrGet() throws RemoteException, BindException {
      return mockRemoteProvider.get();
    }
  });

  public void testExceptionsThrown_Bind() throws Exception {
    tExceptionsThrown(bindInjector);
  }
  
  public void testExceptionsThrown_Provides() throws Exception {
    tExceptionsThrown(providesInjector);
  }
  
  private void tExceptionsThrown(Injector injector) throws Exception {
    RemoteProvider<String> remoteProvider = 
      injector.getInstance(Key.get(remoteProviderOfString));

    mockRemoteProvider.throwOnNextGet(new BindException("kaboom!"));
    try {
      remoteProvider.get();
      fail();
    } catch (BindException expected) {
      assertEquals("kaboom!", expected.getMessage());
    }
  }

  public void testValuesScoped_Bind() throws Exception  {
    tValuesScoped(bindInjector);
  }
  
  public void testValuesScoped_Provides() throws Exception  {
    tValuesScoped(providesInjector);
  }
  
  private void tValuesScoped(Injector injector) throws Exception {
    RemoteProvider<String> remoteProvider = 
      injector.getInstance(Key.get(remoteProviderOfString));

    mockRemoteProvider.setNextToReturn("A");
    assertEquals("A", remoteProvider.get());

    mockRemoteProvider.setNextToReturn("B");
    assertEquals("A", remoteProvider.get());

    testScope.beginNewScope();
    assertEquals("B", remoteProvider.get());
  }

  public void testExceptionsScoped_Bind() throws Exception {
    tExceptionsScoped(bindInjector);
  }
  
  public void testExceptionsScoped_Provides() throws Exception {
    tExceptionsScoped(providesInjector);
  }
  
  private void tExceptionsScoped(Injector injector) throws Exception {
    RemoteProvider<String> remoteProvider = 
        injector.getInstance(Key.get(remoteProviderOfString));

    mockRemoteProvider.throwOnNextGet(new RemoteException("A"));
    try {
      remoteProvider.get();
      fail();
    } catch (RemoteException expected) {
      assertEquals("A", expected.getMessage());
    }
    
    mockRemoteProvider.throwOnNextGet(new RemoteException("B"));
    try {
      remoteProvider.get();
      fail();
    } catch (RemoteException expected) {
      assertEquals("A", expected.getMessage());
    }
  }
  
  public void testAnnotations_Bind() throws Exception {
    final MockRemoteProvider<String> mockRemoteProviderA = new MockRemoteProvider<String>();
    final MockRemoteProvider<String> mockRemoteProviderB = new MockRemoteProvider<String>();
    bindInjector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        ThrowingProviderBinder.create(binder())
            .bind(RemoteProvider.class, String.class)
            .annotatedWith(Names.named("a"))
            .to(mockRemoteProviderA);

        ThrowingProviderBinder.create(binder())
            .bind(RemoteProvider.class, String.class)
            .to(mockRemoteProviderB);
      }
    });
    tAnnotations(bindInjector, mockRemoteProviderA, mockRemoteProviderB);
  }
  
  public void testAnnotations_Provides() throws Exception {
    final MockRemoteProvider<String> mockRemoteProviderA = new MockRemoteProvider<String>();
    final MockRemoteProvider<String> mockRemoteProviderB = new MockRemoteProvider<String>();
    providesInjector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        install(ThrowingProviderBinder.forModule(this));
       }
       
       @SuppressWarnings("unused")
       @CheckedProvides(RemoteProvider.class)
       @Named("a")
       String throwOrGet() throws RemoteException, BindException {
         return mockRemoteProviderA.get();
       }
       
       @SuppressWarnings("unused")
       @CheckedProvides(RemoteProvider.class)
       String throwOrGet2() throws RemoteException, BindException {
         return mockRemoteProviderB.get();
       }
    });
    tAnnotations(providesInjector, mockRemoteProviderA, mockRemoteProviderB);
  }
  
  private void tAnnotations(Injector injector, MockRemoteProvider<String> mockA,
      MockRemoteProvider<String> mockB) throws Exception {
    mockA.setNextToReturn("A");
    mockB.setNextToReturn("B");
    assertEquals("A", 
        injector.getInstance(Key.get(remoteProviderOfString, Names.named("a"))).get());

    assertEquals("B", 
        injector.getInstance(Key.get(remoteProviderOfString)).get());
  }
  
  public void testUndeclaredExceptions_Bind() throws Exception {
    tUndeclaredExceptions(bindInjector);
  }
  
  public void testUndeclaredExceptions_Provides() throws Exception {
    tUndeclaredExceptions(providesInjector);
  }

  private void tUndeclaredExceptions(Injector injector) throws Exception { 
    RemoteProvider<String> remoteProvider = 
        injector.getInstance(Key.get(remoteProviderOfString));
    mockRemoteProvider.throwOnNextGet(new IndexOutOfBoundsException("A"));
    try {
      remoteProvider.get();
      fail();
    } catch (RuntimeException e) {
      assertEquals("A", e.getCause().getMessage());
    }

    // undeclared exceptions shouldn't be scoped
    mockRemoteProvider.throwOnNextGet(new IndexOutOfBoundsException("B"));
    try {
      remoteProvider.get();
      fail();
    } catch (RuntimeException e) {
      assertEquals("B", e.getCause().getMessage());
    }
  }

  public void testThrowingProviderSubclassing() throws Exception {
    final SubMockRemoteProvider aProvider = new SubMockRemoteProvider();
    aProvider.setNextToReturn("A");

    bindInjector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        ThrowingProviderBinder.create(binder())
            .bind(RemoteProvider.class, String.class)
            .to(aProvider);
      }
    });

    assertEquals("A",
        bindInjector.getInstance(Key.get(remoteProviderOfString)).get());
  }

  static class SubMockRemoteProvider extends MockRemoteProvider<String> { }

  public void testBindingToNonInterfaceType_Bind() throws Exception {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          ThrowingProviderBinder.create(binder())
              .bind(MockRemoteProvider.class, String.class)
              .to(mockRemoteProvider);
        }
      });
      fail();
    } catch (CreationException expected) {
      assertEquals(MockRemoteProvider.class.getName() + " must be an interface",
          Iterables.getOnlyElement(expected.getErrorMessages()).getMessage());
    }
  }
  
  public void testBindingToNonInterfaceType_Provides() throws Exception {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          install(ThrowingProviderBinder.forModule(this));
        }
          
        @SuppressWarnings("unused")
        @CheckedProvides(MockRemoteProvider.class)
        String foo() {
          return null;
        }
      });
      fail();
    } catch (CreationException expected) {
      assertEquals(MockRemoteProvider.class.getName() + " must be an interface",
          Iterables.getOnlyElement(expected.getErrorMessages()).getMessage());
    }
  }  
  
  public void testBindingToSubSubInterface_Bind() throws Exception {
    try {
      bindInjector = Guice.createInjector(new AbstractModule() {
        protected void configure() {
          ThrowingProviderBinder.create(binder())
              .bind(SubRemoteProvider.class, String.class);
        }
      });
      fail();
    } catch (CreationException expected) {
      assertEquals(SubRemoteProvider.class.getName() + " must extend CheckedProvider (and only CheckedProvider)",
          Iterables.getOnlyElement(expected.getErrorMessages()).getMessage());
    }
  }
  
  public void testBindingToSubSubInterface_Provides() throws Exception {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          install(ThrowingProviderBinder.forModule(this));
        }
          
        @SuppressWarnings("unused")
        @CheckedProvides(SubRemoteProvider.class)
        String foo() {
          return null;
        }
      });
      fail();
    } catch (CreationException expected) {
      assertEquals(SubRemoteProvider.class.getName() + " must extend CheckedProvider (and only CheckedProvider)",
          Iterables.getOnlyElement(expected.getErrorMessages()).getMessage());
    }
  }    

  interface SubRemoteProvider extends RemoteProvider<String> { }

  public void testBindingToInterfaceWithExtraMethod_Bind() throws Exception {
    try {
      bindInjector = Guice.createInjector(new AbstractModule() {
        protected void configure() {
          ThrowingProviderBinder.create(binder())
              .bind(RemoteProviderWithExtraMethod.class, String.class);
        }
      });
      fail();
    } catch (CreationException expected) {
      assertEquals(RemoteProviderWithExtraMethod.class.getName() + " may not declare any new methods, but declared " 
          + RemoteProviderWithExtraMethod.class.getDeclaredMethods()[0].toGenericString(),
          Iterables.getOnlyElement(expected.getErrorMessages()).getMessage());
    }
  }
  
  public void testBindingToInterfaceWithExtraMethod_Provides() throws Exception {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          install(ThrowingProviderBinder.forModule(this));
        }
          
        @SuppressWarnings("unused")
        @CheckedProvides(RemoteProviderWithExtraMethod.class)
        String foo() {
          return null;
        }
      });
      fail();
    } catch (CreationException expected) {
      assertEquals(RemoteProviderWithExtraMethod.class.getName() + " may not declare any new methods, but declared " 
          + RemoteProviderWithExtraMethod.class.getDeclaredMethods()[0].toGenericString(),
          Iterables.getOnlyElement(expected.getErrorMessages()).getMessage());
    }
  }
  
  public void testDependencies_Bind() {
    bindInjector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(String.class).toInstance("Foo");
        bind(Integer.class).toInstance(5);
        bind(Double.class).toInstance(5d);
        bind(Long.class).toInstance(5L);
        ThrowingProviderBinder.create(binder())
            .bind(RemoteProvider.class, String.class)
            .to(DependentRemoteProvider.class);
      }
    });
    
    HasDependencies hasDependencies =
        (HasDependencies)bindInjector.getBinding(Key.get(remoteProviderOfString));
    hasDependencies = 
        (HasDependencies)bindInjector.getBinding(
            Iterables.getOnlyElement(hasDependencies.getDependencies()).getKey());
    // Make sure that that is dependent on DependentRemoteProvider.
    assertEquals(Dependency.get(Key.get(DependentRemoteProvider.class)), 
        Iterables.getOnlyElement(hasDependencies.getDependencies()));
    // And make sure DependentRemoteProvider has the proper dependencies.
    hasDependencies = (HasDependencies)bindInjector.getBinding(DependentRemoteProvider.class);
    Set<Key<?>> dependencyKeys = ImmutableSet.copyOf(
        Iterables.transform(hasDependencies.getDependencies(),
          new Function<Dependency<?>, Key<?>>() {
            public Key<?> apply(Dependency<?> from) {
              return from.getKey();
            }
          }));
    assertEquals(ImmutableSet.<Key<?>>of(Key.get(String.class), Key.get(Integer.class),
        Key.get(Long.class), Key.get(Double.class)), dependencyKeys);
  }
  
  public void testDependencies_Provides() {
    providesInjector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(String.class).toInstance("Foo");
        bind(Integer.class).toInstance(5);
        bind(Double.class).toInstance(5d);
        bind(Long.class).toInstance(5L);
        install(ThrowingProviderBinder.forModule(this));
      }
      
      @SuppressWarnings("unused")
      @CheckedProvides(RemoteProvider.class)
      String foo(String s, Integer i, Double d, Long l) {
        return null;
      }
    });
    
    HasDependencies hasDependencies =
        (HasDependencies)providesInjector.getBinding(Key.get(remoteProviderOfString));
    // RemoteProvider<String> is dependent on the provider method..
    hasDependencies = 
        (HasDependencies)providesInjector.getBinding(
            Iterables.getOnlyElement(hasDependencies.getDependencies()).getKey());
    // And the provider method has our real dependencies..
    hasDependencies = (HasDependencies)providesInjector.getBinding(
        Iterables.getOnlyElement(hasDependencies.getDependencies()).getKey());
    Set<Key<?>> dependencyKeys = ImmutableSet.copyOf(
        Iterables.transform(hasDependencies.getDependencies(),
          new Function<Dependency<?>, Key<?>>() {
            public Key<?> apply(Dependency<?> from) {
              return from.getKey();
            }
          }));
    assertEquals(ImmutableSet.<Key<?>>of(Key.get(String.class), Key.get(Integer.class),
        Key.get(Long.class), Key.get(Double.class)), dependencyKeys);
  }  

  interface RemoteProviderWithExtraMethod<T> extends CheckedProvider<T> {
    T get(T defaultValue) throws RemoteException, BindException;
  }

  interface RemoteProvider<T> extends CheckedProvider<T> { 
    public T get() throws RemoteException, BindException;
  }
  
  static class DependentRemoteProvider<T> implements RemoteProvider<T> {
    @Inject double foo;
    
    @Inject public DependentRemoteProvider(String foo, int bar) {
    }
    
    @Inject void initialize(long foo) {}
    
    public T get() throws RemoteException {
      return null;
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
    bindInjector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        ThrowingProviderBinder.create(binder())
            .bind(StringRemoteProvider.class, String.class)
            .to(new StringRemoteProvider() {
              public String get() throws RemoteException {
                return "A";
              }
            });
      }
    });
    
    assertEquals("A", bindInjector.getInstance(StringRemoteProvider.class).get());
  }
  
  public void testBindingToInterfaceWithBoundValueType_Provides() throws RemoteException {
    providesInjector = Guice.createInjector(new AbstractModule() {
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
    String get() throws RemoteException;  
  }

  public void testBindingToInterfaceWithGeneric_Bind() throws Exception {
    bindInjector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        ThrowingProviderBinder.create(binder())
            .bind(RemoteProvider.class, new TypeLiteral<List<String>>() { }.getType())
            .to(new RemoteProvider<List<String>>() {
              public List<String> get() throws RemoteException {
                return Arrays.asList("A", "B");
              }
            });
      }
    });

    Key<RemoteProvider<List<String>>> key
        = Key.get(new TypeLiteral<RemoteProvider<List<String>>>() { });
    assertEquals(Arrays.asList("A", "B"), bindInjector.getInstance(key).get());
  }
  
  public void testBindingToInterfaceWithGeneric_Provides() throws Exception {
    providesInjector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        install(ThrowingProviderBinder.forModule(this));
      }
      
      @SuppressWarnings("unused")
      @CheckedProvides(RemoteProvider.class)
      List<String> foo() throws RemoteException {
          return Arrays.asList("A", "B");
      }
    });

    Key<RemoteProvider<List<String>>> key
        = Key.get(new TypeLiteral<RemoteProvider<List<String>>>() { });
    assertEquals(Arrays.asList("A", "B"), providesInjector.getInstance(key).get());
  }
  
  public void testProviderMethodWithWrongException() {
    try {
      Guice.createInjector(new AbstractModule() {
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
    } catch(CreationException ce) {
      assertEquals(InterruptedException.class.getName()
          + " is not compatible with the exceptions (["
          + RemoteException.class + ", " + BindException.class
          + "]) declared in the CheckedProvider interface ("
          + RemoteProvider.class.getName()
          + ")", 
          Iterables.getOnlyElement(ce.getErrorMessages()).getMessage());
    }
  }
  
  public void testProviderMethodWithSubclassOfExceptionIsOk() throws Exception {
    providesInjector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        install(ThrowingProviderBinder.forModule(this));
      }
      
      @SuppressWarnings("unused")
      @CheckedProvides(RemoteProvider.class)
      String foo() throws AccessException {
        throw new AccessException("boo!");
      }
    });
    
    RemoteProvider<String> remoteProvider = 
      providesInjector.getInstance(Key.get(remoteProviderOfString));

    try {
      remoteProvider.get();
      fail();
    } catch (RemoteException expected) {
      assertTrue(expected instanceof AccessException);
      assertEquals("boo!", expected.getMessage());
    }
  }
  
  public void testProviderMethodWithSuperclassFails() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          install(ThrowingProviderBinder.forModule(this));
        }
        
        @SuppressWarnings("unused")
        @CheckedProvides(RemoteProvider.class)
        String foo() throws IOException {
            return null;
        }
      });
      fail();
    } catch(CreationException ce) {
      assertEquals(IOException.class.getName()
          + " is not compatible with the exceptions (["
          + RemoteException.class + ", " + BindException.class
          + "]) declared in the CheckedProvider interface ("
          + RemoteProvider.class.getName()
          + ")", 
          Iterables.getOnlyElement(ce.getErrorMessages()).getMessage());
    }
  }
  
  public void testProviderMethodWithRuntimeExceptionsIsOk() throws Exception {
    providesInjector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        install(ThrowingProviderBinder.forModule(this));
      }
      
      @SuppressWarnings("unused")
      @CheckedProvides(RemoteProvider.class)
      String foo() throws RuntimeException {
        throw new RuntimeException("boo!");
      }
    });
    
    RemoteProvider<String> remoteProvider = 
      providesInjector.getInstance(Key.get(remoteProviderOfString));

    try {
      remoteProvider.get();
      fail();
    } catch (RuntimeException expected) {
      assertEquals("boo!", expected.getCause().getMessage());
    }
  }
  
  private static class SubBindException extends BindException {}
  
  public void testProviderMethodWithManyExceptions() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          install(ThrowingProviderBinder.forModule(this));
        }
        
        @SuppressWarnings("unused")
        @CheckedProvides(RemoteProvider.class)
        String foo() throws InterruptedException, RuntimeException, RemoteException, 
                            AccessException, TooManyListenersException,
                            BindException, SubBindException {
            return null;
        }
      });
      fail();
    } catch(CreationException ce) {
      // The only two that should fail are Interrupted & TooManyListeners.. the rest are OK.
      List<Message> errors = ImmutableList.copyOf(ce.getErrorMessages());
      assertEquals(InterruptedException.class.getName()
          + " is not compatible with the exceptions (["
          + RemoteException.class + ", " + BindException.class
          + "]) declared in the CheckedProvider interface ("
          + RemoteProvider.class.getName()
          + ")", 
          errors.get(0).getMessage());
      assertEquals(TooManyListenersException.class.getName()
          + " is not compatible with the exceptions (["
          + RemoteException.class + ", " + BindException.class
          + "]) declared in the CheckedProvider interface ("
          + RemoteProvider.class.getName()
          + ")", 
          errors.get(1).getMessage());
      assertEquals(2, errors.size());
    }
  }
  
  public void testMoreTypeParameters() {
    try {
      Guice.createInjector(new AbstractModule() {
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
    } catch(CreationException ce) {
      assertEquals(TooManyTypeParameters.class.getName() + " has more than one generic type parameter: [T, P]",
          Iterables.getOnlyElement(ce.getErrorMessages()).getMessage());
    }    
  }
  
  public void testWrongThrowingProviderType() {
    try {
      Guice.createInjector(new AbstractModule() {
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
    } catch(CreationException ce) {
      assertEquals(WrongThrowingProviderType.class.getName() 
          + " does not properly extend CheckedProvider, the first type parameter of CheckedProvider "
          + "(java.lang.String) is not a generic type",
          Iterables.getOnlyElement(ce.getErrorMessages()).getMessage());
    }    
  }
  
  public void testOneMethodThatIsntGet() {
    try {
      Guice.createInjector(new AbstractModule() {
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
    } catch(CreationException ce) {
      assertEquals(OneNoneGetMethod.class.getName() 
          + " may not declare any new methods, but declared " + Classes.toString(OneNoneGetMethod.class.getDeclaredMethods()[0]),
          Iterables.getOnlyElement(ce.getErrorMessages()).getMessage());
    }
  }
  
  public void testManyMethods() {
    try {
      Guice.createInjector(new AbstractModule() {
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
    } catch(CreationException ce) {
      assertEquals(ManyMethods.class.getName() 
          + " may not declare any new methods, but declared " + Arrays.asList(ManyMethods.class.getDeclaredMethods()),
          Iterables.getOnlyElement(ce.getErrorMessages()).getMessage());
    }
  }
  
  public void testIncorrectPredefinedType_Bind() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          ThrowingProviderBinder.create(binder())
              .bind(StringRemoteProvider.class, Integer.class)
              .to(new StringRemoteProvider() {
                public String get() throws RemoteException {
                  return "A";
                }
              });
        }
      });
      fail();
    } catch(CreationException ce) {
      assertEquals(StringRemoteProvider.class.getName() 
          + " expects the value type to be java.lang.String, but it was java.lang.Integer",
          Iterables.getOnlyElement(ce.getErrorMessages()).getMessage());
    }
  }
  
  public void testIncorrectPredefinedType_Provides() {
    try {
      Guice.createInjector(new AbstractModule() {
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
    } catch(CreationException ce) {
      assertEquals(StringRemoteProvider.class.getName() 
          + " expects the value type to be java.lang.String, but it was java.lang.Integer",
          Iterables.getOnlyElement(ce.getErrorMessages()).getMessage());
    }
  }
  
  private static interface TooManyTypeParameters<T, P> extends CheckedProvider<T> {    
  }
  
  private static interface WrongThrowingProviderType<T> extends CheckedProvider<String> {    
  }
  
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
    } catch(Exception ex) {
      assertEquals("boo", ex.getMessage());
    }
  }
  
  public void testEarlyBindingError() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          ThrowingProviderBinder.create(binder())
              .bind(StringRemoteProvider.class, String.class)
              .to(FailingProvider.class);
        }
      });
      fail();
    } catch(CreationException ce) {
      assertEquals("Could not find a suitable constructor in " + FailingProvider.class.getName()
          + ". Classes must have either one (and only one) constructor annotated with @Inject"
          + " or a zero-argument constructor that is not private.",
          Iterables.getOnlyElement(ce.getErrorMessages()).getMessage());
    }
  }
  
  private static class FailingProvider implements StringRemoteProvider {
    // no @Inject.
    @SuppressWarnings("unused")
    FailingProvider(Integer foo) {}
    
    public String get() {
      return null;
    }
  }
}
