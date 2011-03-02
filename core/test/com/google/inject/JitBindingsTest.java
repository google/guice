/*
 * Copyright (C) 2010 Google Inc.
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
import static com.google.inject.JitBindingsTest.GetBindingCheck.ALLOW_BINDING_PROVIDER;
import static com.google.inject.JitBindingsTest.GetBindingCheck.FAIL_ALL;
import static com.google.inject.JitBindingsTest.GetBindingCheck.ALLOW_BINDING;
import static com.google.inject.internal.util.ImmutableSet.of;

import junit.framework.TestCase;

import java.util.Set;

/**
 * Some tests for {@link Binder#requireExplicitBindings()}
 * 
 * @author sberlin@gmail.com (Sam Berlin)
 */
public class JitBindingsTest extends TestCase {
  
  private String jitFailed(Class<?> clazz) {
    return jitFailed(TypeLiteral.get(clazz));
  }
  
  private String jitFailed(TypeLiteral<?> clazz) {
    return "Explicit bindings are required and " + clazz + " is not explicitly bound.";
  }
  
  private String inChildMessage(Class<?> clazz) {
    return "Unable to create binding for "
        + clazz.getName()
        + ". It was already configured on one or more child injectors or private modules";
  }
  
  public void testLinkedBindingWorks() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        binder().requireExplicitBindings();
        bind(Foo.class).to(FooImpl.class);
      }
    });
    // Foo was explicitly bound
    ensureWorks(injector, Foo.class);
    // FooImpl was implicitly bound, it is an error to call getInstance or getProvider,
    // It is OK to call getBinding for introspection, but an error to get the provider
    // of the binding
    ensureFails(injector, ALLOW_BINDING, FooImpl.class);
  }
  
  public void testMoreBasicsWork() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        binder().requireExplicitBindings();
        bind(Foo.class).to(FooImpl.class);
        bind(Bar.class);
        bind(FooBar.class);
      }
    });
    // Foo, Bar & FooBar was explicitly bound    
    ensureWorks(injector, FooBar.class, Bar.class, Foo.class);
    // FooImpl was implicitly bound, it is an error to call getInstance or getProvider,
    // It is OK to call getBinding for introspection, but an error to get the provider
    // of the binding    
    ensureFails(injector, ALLOW_BINDING,  FooImpl.class);    
  }
  
  public void testLinkedEagerSingleton() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        binder().requireExplicitBindings();
        bind(Foo.class).to(FooImpl.class).asEagerSingleton();
      }
    });
    // Foo was explicitly bound
    ensureWorks(injector, Foo.class);
    // FooImpl was implicitly bound, it is an error to call getInstance or getProvider,
    // It is OK to call getBinding for introspection, but an error to get the provider
    // of the binding
    ensureFails(injector, ALLOW_BINDING, FooImpl.class);
  }
  
  public void testBasicsWithEagerSingleton() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        binder().requireExplicitBindings();
        bind(Foo.class).to(FooImpl.class).asEagerSingleton();
        bind(Bar.class);
        bind(FooBar.class);
      }
    });
    // Foo, Bar & FooBar was explicitly bound    
    ensureWorks(injector, FooBar.class, Bar.class, Foo.class);
    // FooImpl was implicitly bound, it is an error to call getInstance or getProvider,
    // It is OK to call getBinding for introspection, but an error to get the provider
    // of the binding    
    ensureFails(injector, ALLOW_BINDING,  FooImpl.class);    
  }  
  
  public void testLinkedToScoped() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        binder.requireExplicitBindings();
        bind(Foo.class).to(ScopedFooImpl.class);
      }
    });
    // Foo was explicitly bound
    ensureWorks(injector, Foo.class);
    // FooSingletonImpl was implicitly bound, it is an error to call getInstance or getProvider,
    // It is OK to call getBinding for introspection, but an error to get the provider
    // of the binding
    ensureFails(injector, ALLOW_BINDING, ScopedFooImpl.class);    
  }
  
  public void testBasicsWithScoped() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        binder().requireExplicitBindings();
        bind(Foo.class).to(ScopedFooImpl.class);
        bind(Bar.class);
        bind(FooBar.class);
      }
    });
    // Foo, Bar & FooBar was explicitly bound    
    ensureWorks(injector, FooBar.class, Bar.class, Foo.class);
    // FooSingletonImpl was implicitly bound, it is an error to call getInstance or getProvider,
    // It is OK to call getBinding for introspection, but an error to get the provider
    // of the binding    
    ensureFails(injector, ALLOW_BINDING,  ScopedFooImpl.class);   
  }
  
  public void testFailsIfInjectingScopedDirectlyWhenItIsntBound() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override
        protected void configure() {
          binder().requireExplicitBindings();
          bind(Foo.class).to(ScopedFooImpl.class);
          bind(WantsScopedFooImpl.class);
        }
      });
      fail();
    } catch(CreationException expected) {
      assertContains(expected.getMessage(), "1) " + jitFailed(ScopedFooImpl.class));
      assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
    }
  }
  
  public void testLinkedProviderBindingWorks() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        binder().requireExplicitBindings();
        bind(Foo.class).toProvider(FooProvider.class);
      }
    });
    // Foo was explicitly bound
    ensureWorks(injector, Foo.class);
    // FooImpl was not bound at all (even implicitly), it is an error
    // to call getInstance, getProvider, or getBinding.
    ensureFails(injector, FAIL_ALL, FooImpl.class);
  }
  
  public void testJitGetFails() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override
        protected void configure() {
         binder().requireExplicitBindings(); 
        }
      }).getInstance(Bar.class);
      fail("should have failed");
    } catch(ConfigurationException expected) {
      assertContains(expected.getMessage(), "1) " + jitFailed(Bar.class));
      assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
    }
  }
  
  public void testJitInjectionFails() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override
        protected void configure() {
          binder().requireExplicitBindings();
          bind(Foo.class).to(FooImpl.class);
          bind(FooBar.class);
        }
      });
      fail("should have failed");
    } catch (CreationException expected) {
      assertContains(expected.getMessage(), "1) " + jitFailed(Bar.class));
      assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
    }
  }

  public void testJitProviderGetFails() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override
        protected void configure() {
          binder().requireExplicitBindings(); 
        }
      }).getProvider(Bar.class);
      fail("should have failed");
    } catch (ConfigurationException expected) {
      assertContains(expected.getMessage(), "1) " + jitFailed(Bar.class));
      assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
    }
  }

  public void testJitProviderInjectionFails() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override
        protected void configure() {
          binder().requireExplicitBindings();
          bind(Foo.class).to(FooImpl.class);
          bind(ProviderFooBar.class);
        }
      });
      fail("should have failed");
    } catch (CreationException expected) {
      assertContains(expected.getMessage(), "1) " + jitFailed(Bar.class));
      assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
    }
  }
  
  public void testImplementedBy() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        binder().requireExplicitBindings();
        bind(ImplBy.class);
      }
    });
    ensureWorks(injector, ImplBy.class);
    ensureFails(injector, ALLOW_BINDING, ImplByImpl.class);
  }
  
  public void testImplementedBySomethingThatIsAnnotated() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        binder().requireExplicitBindings();
        bind(ImplByScoped.class);
      }
    });
    ensureWorks(injector, ImplByScoped.class);
    ensureFails(injector, ALLOW_BINDING, ImplByScopedImpl.class);    
  }
  
  public void testProvidedBy() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        binder().requireExplicitBindings();
        bind(ProvBy.class);
      }
    });
    ensureWorks(injector, ProvBy.class);
    ensureFails(injector, ALLOW_BINDING, ProvByProvider.class);
  }
  
  public void testProviderMethods() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override protected void configure() {
        binder().requireExplicitBindings();
      }
      @SuppressWarnings("unused") @Provides Foo foo() { return new FooImpl(); }
    });
    ensureWorks(injector, Foo.class);
  }
  
  public void testChildInjectorInheritsOption() {
    Injector parent = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        binder().requireExplicitBindings();
        bind(Bar.class);
      }
    });
    ensureWorks(parent, Bar.class);
    ensureFails(parent, FAIL_ALL, FooImpl.class, FooBar.class, Foo.class);
    
    try {
      parent.createChildInjector(new AbstractModule() {
        @Override
        protected void configure() {
          bind(FooBar.class);
        }
      });
      fail("should have failed");
    } catch(CreationException expected) {
      assertContains(expected.getMessage(), "1) " + jitFailed(Foo.class));
      assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
    }
    
    Injector child = parent.createChildInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(Foo.class).to(FooImpl.class);
      }
    });
    ensureWorks(child, Foo.class, Bar.class);
    ensureFails(child, ALLOW_BINDING, FooImpl.class);
    ensureInChild(parent, FooImpl.class, FooBar.class, Foo.class);
    
    Injector grandchild = child.createChildInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(FooBar.class);
      }
    });
    ensureWorks(grandchild, FooBar.class, Foo.class, Bar.class);
    ensureFails(grandchild, ALLOW_BINDING, FooImpl.class);
    ensureFails(child, ALLOW_BINDING, FooImpl.class);
    ensureInChild(parent, FooImpl.class, FooBar.class, Foo.class);    
  }
  
  public void testChildInjectorAddsOption() {
    Injector parent = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(Bar.class);
      }
    });
    int totalParentBindings = parent.getAllBindings().size();
    
    try {
      parent.createChildInjector(new AbstractModule() {
        @Override
        protected void configure() {
          binder().requireExplicitBindings();
          bind(FooBar.class);
        }
      });
      fail("should have failed");
    } catch(CreationException expected) {
      assertContains(expected.getMessage(), "1) " + jitFailed(Foo.class));
      assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
    }
    assertEquals(totalParentBindings, parent.getAllBindings().size());
    
    Injector child = parent.createChildInjector(new AbstractModule() {
      @Override
      protected void configure() {
        binder().requireExplicitBindings();
        bind(Foo.class).to(FooImpl.class);
      }
    });
    totalParentBindings++; // creating this child added FooImpl to the parent.
    assertEquals(totalParentBindings, parent.getAllBindings().size());
    ensureWorks(child, Foo.class, Bar.class);
    ensureFails(child, ALLOW_BINDING_PROVIDER, FooImpl.class);
    // Make extra certain that if something tries to inject a FooImpl from child
    // that it fails, even if calling getBinding().getProvider works.. because
    // the binding is built with the parent injector.
    try {
      child.injectMembers(new Object() {
        @SuppressWarnings("unused")
        @Inject
        void inject(FooImpl fooImpl) {}
      });
      fail();
    } catch(ConfigurationException expected) {
      assertContains(expected.getMessage(), "1) " + jitFailed(FooImpl.class));
      assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
    }
    
    Injector grandchild = child.createChildInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(FooBar.class);
      }
    });
    assertEquals(totalParentBindings, parent.getAllBindings().size());    
    ensureWorks(grandchild, FooBar.class, Foo.class, Bar.class);
    ensureFails(grandchild, ALLOW_BINDING_PROVIDER, FooImpl.class);
    ensureFails(child, ALLOW_BINDING_PROVIDER, FooImpl.class);
    
    // Make sure siblings of children don't inherit each others settings...
    // a new child should be able to get FooImpl.
    child = parent.createChildInjector();
    ensureWorks(child, FooImpl.class);
  }

  public void testPrivateModulesInheritOptions() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          binder().requireExplicitBindings();
          bind(Foo.class).to(FooImpl.class);
  
          install(new PrivateModule() {
            public void configure() {
              bind(FooBar.class);
              expose(FooBar.class);
            }
          });
        }
      });
      fail("should have failed");
    } catch(CreationException expected) {
      assertContains(expected.getMessage(), "1) " + jitFailed(Bar.class));
      assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
    }
    
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        binder().requireExplicitBindings();

        install(new PrivateModule() {
          public void configure() {
            bind(Foo.class).to(FooImpl.class);
            expose(Foo.class);
          }
        });
      }
    });
    ensureInChild(injector, FooImpl.class);
  }
  
  public void testPrivateModuleAddsOption() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(Foo.class).to(FooImpl.class);
  
          // Fails because FooBar is in the private module,
          // and it wants Bar, but Bar would be JIT.
          install(new PrivateModule() {
            public void configure() {
              binder().requireExplicitBindings();
              bind(FooBar.class);
              expose(FooBar.class);
            }
          });
        }
      });
      fail("should have failed");
    } catch(CreationException expected) {
      assertContains(expected.getMessage(), "1) " + jitFailed(Bar.class));
      assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
    }
  }

  public void testPrivateModuleSiblingsDontShareOption() {
    Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(Foo.class).to(FooImpl.class);

        install(new PrivateModule() {
          public void configure() {
            binder().requireExplicitBindings();
          }
        });

        // This works, even though Bar is JIT,
        // because the requireExplicitBindings isn't shared
        // between sibling private modules.
        install(new PrivateModule() {
          public void configure() {
            bind(FooBar.class);
            expose(FooBar.class);
          }
        });
      }
    });
  }  

  public void testTypeLiteralsCanBeInjected() {
    Injector injector = Guice.createInjector(new AbstractModule() {
        @Override protected void configure() {
          binder().requireExplicitBindings();
          bind(new TypeLiteral<WantsTypeLiterals<String>>() {});
          bind(new TypeLiteral<Set<String>>() {}).toInstance(of("bar"));
        }
      });

    WantsTypeLiterals<String> foo = injector.getInstance(new Key<WantsTypeLiterals<String>>() {});
    assertEquals(foo.literal.getRawType(), String.class);
    assertEquals(of("bar"), foo.set);
  }
  
  public void testMembersInjectorsCanBeInjected() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override protected void configure() {
        binder().requireExplicitBindings();
      }
      
      @Provides String data(MembersInjector<String> mi) {
        String data = "foo";
        mi.injectMembers(data);
        return data;
      }
    });

    String data = injector.getInstance(String.class);
    assertEquals("foo", data);
  }
  
  private void ensureWorks(Injector injector, Class<?>... classes) {
    for(int i = 0; i < classes.length; i++) {
      injector.getInstance(classes[i]);
      injector.getProvider(classes[i]).get();
      injector.getBinding(classes[i]).getProvider().get();
    }
  }
  
  enum GetBindingCheck { FAIL_ALL, ALLOW_BINDING, ALLOW_BINDING_PROVIDER }
  private void ensureFails(Injector injector, GetBindingCheck getBinding, Class<?>... classes) {
    for(int i = 0; i < classes.length; i++) {      
      try { 
        injector.getInstance(classes[i]);
        fail("should have failed tring to retrieve class: " + classes[i]);
      } catch(ConfigurationException expected) {
        assertContains(expected.getMessage(), "1) " + jitFailed(classes[i]));
        assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
      }
      
      try { 
        injector.getProvider(classes[i]);
        fail("should have failed tring to retrieve class: " + classes[i]);
      } catch(ConfigurationException expected) {
        assertContains(expected.getMessage(), "1) " + jitFailed(classes[i]));
        assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
      }
      
      if (getBinding == GetBindingCheck.ALLOW_BINDING
          || getBinding == GetBindingCheck.ALLOW_BINDING_PROVIDER) {
        Binding<?> binding = injector.getBinding(classes[i]);
        try {
          binding.getProvider();
          if (getBinding != GetBindingCheck.ALLOW_BINDING_PROVIDER) {
            fail("should have failed trying to retrieve class: " + classes[i]);
          }
        } catch(ConfigurationException expected) {
          if (getBinding == GetBindingCheck.ALLOW_BINDING_PROVIDER) {
            throw expected;
          }
          assertContains(expected.getMessage(), "1) " + jitFailed(classes[i]));
          assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
        }
      } else {
        try {
          injector.getBinding(classes[i]);
          fail("should have failed tring to retrieve class: " + classes[i]);          
        } catch(ConfigurationException expected) {
          assertContains(expected.getMessage(), "1) " + jitFailed(classes[i]));
          assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
        }
      }
    }
  }
  
  private void ensureInChild(Injector injector, Class<?>... classes) {
    for(int i = 0; i < classes.length; i++) {      
      try { 
        injector.getInstance(classes[i]);
        fail("should have failed tring to retrieve class: " + classes[i]);
      } catch(ConfigurationException expected) {
        assertContains(expected.getMessage(), "1) " + inChildMessage(classes[i]));
        assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
      }
      
      try { 
        injector.getProvider(classes[i]);
        fail("should have failed tring to retrieve class: " + classes[i]);
      } catch(ConfigurationException expected) {
        assertContains(expected.getMessage(), "1) " + inChildMessage(classes[i]));
        assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
      }
      
      try {
        injector.getBinding(classes[i]);
        fail("should have failed tring to retrieve class: " + classes[i]);          
      } catch(ConfigurationException expected) {
        assertContains(expected.getMessage(), "1) " + inChildMessage(classes[i]));
        assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
      }
    }
  }
  
  private static interface Foo {}
  private static class FooImpl implements Foo {}
  @Singleton private static class ScopedFooImpl implements Foo {}
  private static class WantsScopedFooImpl {
    @SuppressWarnings("unused") @Inject ScopedFooImpl scopedFoo;
  }
  private static class Bar {}
  private static class FooBar {
    @SuppressWarnings("unused") @Inject Foo foo;
    @SuppressWarnings("unused") @Inject Bar bar;
  }
  private static class ProviderFooBar {
    @SuppressWarnings("unused") @Inject Provider<Foo> foo;
    @SuppressWarnings("unused") @Inject Provider<Bar> bar;
  }
  private static class FooProvider implements Provider<Foo> {
    public Foo get() {
      return new FooImpl();
    }
  }

  @ImplementedBy(ImplByImpl.class)
  private static interface ImplBy {}
  private static class ImplByImpl implements ImplBy {}
  
  @ImplementedBy(ImplByScopedImpl.class)
  private static interface ImplByScoped {}
  @Singleton
  private static class ImplByScopedImpl implements ImplByScoped {}  

  @ProvidedBy(ProvByProvider.class)
  private static interface ProvBy {}
  private static class ProvByProvider implements Provider<ProvBy> {
    public ProvBy get() {
      return new ProvBy() {};
    }
  }
  
  private static class WantsTypeLiterals<T> {
    TypeLiteral<T> literal;
    Set<T> set;
    
    @Inject WantsTypeLiterals(TypeLiteral<T> literal, Set<T> set) {
      this.literal = literal;
      this.set = set;
      
    }
  }
}
