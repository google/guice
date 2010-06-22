package com.google.inject;

import static com.google.inject.Asserts.assertContains;
import junit.framework.TestCase;

/**
 * Some tests for {@link InjectorBuilder#requireExplicitBindings()}
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
  
  public void testLinkedBindingWorks() {
    Injector injector = new InjectorBuilder().requireExplicitBindings().addModules(new AbstractModule() {
      @Override
      protected void configure() {
        bind(Foo.class).to(FooImpl.class);
      }
    }).build();
    // Foo was explicitly bound
    ensureWorks(injector, Foo.class);
    // FooImpl was implicitly bound, it is an error to call getInstance or getProvider,
    // It is OK to call getBinding for introspection, but an error to get the provider
    // of the binding
    ensureFails(injector, true, FooImpl.class);
  }
  
  public void testMoreBasicsWork() {
    Injector injector = new InjectorBuilder().requireExplicitBindings().addModules(new AbstractModule() {
      @Override
      protected void configure() {
        bind(Foo.class).to(FooImpl.class);
        bind(Bar.class);
        bind(FooBar.class);
      }
    }).build();
    // Foo, Bar & FooBar was explicitly bound    
    ensureWorks(injector, FooBar.class, Bar.class, Foo.class);
    // FooImpl was implicitly bound, it is an error to call getInstance or getProvider,
    // It is OK to call getBinding for introspection, but an error to get the provider
    // of the binding    
    ensureFails(injector, true,  FooImpl.class);    
  }
  
  public void testLinkedEagerSingleton() {
    Injector injector = new InjectorBuilder().requireExplicitBindings().addModules(new AbstractModule() {
      @Override
      protected void configure() {
        bind(Foo.class).to(FooImpl.class).asEagerSingleton();
      }
    }).build();
    // Foo was explicitly bound
    ensureWorks(injector, Foo.class);
    // FooImpl was implicitly bound, it is an error to call getInstance or getProvider,
    // It is OK to call getBinding for introspection, but an error to get the provider
    // of the binding
    ensureFails(injector, true, FooImpl.class);
  }
  
  public void testBasicsWithEagerSingleton() {
    Injector injector = new InjectorBuilder().requireExplicitBindings().addModules(new AbstractModule() {
      @Override
      protected void configure() {
        bind(Foo.class).to(FooImpl.class).asEagerSingleton();
        bind(Bar.class);
        bind(FooBar.class);
      }
    }).build();
    // Foo, Bar & FooBar was explicitly bound    
    ensureWorks(injector, FooBar.class, Bar.class, Foo.class);
    // FooImpl was implicitly bound, it is an error to call getInstance or getProvider,
    // It is OK to call getBinding for introspection, but an error to get the provider
    // of the binding    
    ensureFails(injector, true,  FooImpl.class);    
  }  
  
  public void testLinkedToScoped() {
    Injector injector = new InjectorBuilder().requireExplicitBindings().addModules(new AbstractModule() {
      @Override
      protected void configure() {
        bind(Foo.class).to(ScopedFooImpl.class);
      }
    }).build();
    // Foo was explicitly bound
    ensureWorks(injector, Foo.class);
    // FooSingletonImpl was implicitly bound, it is an error to call getInstance or getProvider,
    // It is OK to call getBinding for introspection, but an error to get the provider
    // of the binding
    ensureFails(injector, true, ScopedFooImpl.class);    
  }
  
  public void testBasicsWithScoped() {
    Injector injector = new InjectorBuilder().requireExplicitBindings().addModules(new AbstractModule() {
      @Override
      protected void configure() {
        bind(Foo.class).to(ScopedFooImpl.class);
        bind(Bar.class);
        bind(FooBar.class);
      }
    }).build();
    // Foo, Bar & FooBar was explicitly bound    
    ensureWorks(injector, FooBar.class, Bar.class, Foo.class);
    // FooSingletonImpl was implicitly bound, it is an error to call getInstance or getProvider,
    // It is OK to call getBinding for introspection, but an error to get the provider
    // of the binding    
    ensureFails(injector, true,  ScopedFooImpl.class);   
  }
  
  public void testFailsIfInjectingScopedDirectlyWhenItIsntBound() {
    try {
      new InjectorBuilder().requireExplicitBindings().addModules(new AbstractModule() {
        @Override
        protected void configure() {
          bind(Foo.class).to(ScopedFooImpl.class);
          bind(WantsScopedFooImpl.class);
        }
      }).build();
      fail();
    } catch(CreationException expected) {
      assertContains(expected.getMessage(), "1) " + jitFailed(ScopedFooImpl.class));
      assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
    }
  }
  
  public void testLinkedProviderBindingWorks() {
    Injector injector = new InjectorBuilder().requireExplicitBindings().addModules(new AbstractModule() {
      @Override
      protected void configure() {
        bind(Foo.class).toProvider(FooProvider.class);
      }
    }).build();
    // Foo was explicitly bound
    ensureWorks(injector, Foo.class);
    // FooImpl was not bound at all (even implicitly), it is an error
    // to call getInstance, getProvider, or getBinding.
    ensureFails(injector, false, FooImpl.class);
  }
  
  public void testJitGetFails() {
    try {
      new InjectorBuilder().requireExplicitBindings().build().getInstance(Bar.class);
      fail("should have failed");
    } catch(ConfigurationException expected) {
      assertContains(expected.getMessage(), "1) " + jitFailed(Bar.class));
      assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
    }
  }
  
  public void testJitInjectionFails() {
    try {
      new InjectorBuilder().requireExplicitBindings().addModules(new AbstractModule() {
        @Override
        protected void configure() {
          bind(Foo.class).to(FooImpl.class);
          bind(FooBar.class);
        }
      }).build();
      fail("should have failed");
    } catch (CreationException expected) {
      assertContains(expected.getMessage(), "1) " + jitFailed(Bar.class));
      assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
    }
  }

  public void testJitProviderGetFails() {
    try {
      new InjectorBuilder().requireExplicitBindings().build().getProvider(Bar.class);
      fail("should have failed");
    } catch (ConfigurationException expected) {
      assertContains(expected.getMessage(), "1) " + jitFailed(Bar.class));
      assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
    }
  }

  public void testJitProviderInjectionFails() {
    try {
      new InjectorBuilder().requireExplicitBindings().addModules(new AbstractModule() {
        @Override
        protected void configure() {
          bind(Foo.class).to(FooImpl.class);
          bind(ProviderFooBar.class);
        }
      }).build();
      fail("should have failed");
    } catch (CreationException expected) {
      assertContains(expected.getMessage(), "1) " + jitFailed(Bar.class));
      assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
    }
  }
  
  public void testImplementedBy() {
    Injector injector = new InjectorBuilder().requireExplicitBindings().addModules(new AbstractModule() {
      @Override
      protected void configure() {
        bind(ImplBy.class);
      }
    }).build();
    ensureWorks(injector, ImplBy.class);
    ensureFails(injector, true, ImplByImpl.class);
  }
  
  public void testImplementedBySomethingThatIsAnnotated() {
    Injector injector = new InjectorBuilder().requireExplicitBindings().addModules(new AbstractModule() {
      @Override
      protected void configure() {
        bind(ImplByScoped.class);
      }
    }).build();
    ensureWorks(injector, ImplByScoped.class);
    ensureFails(injector, true, ImplByScopedImpl.class);    
  }
  
  public void testProvidedBy() {
    Injector injector = new InjectorBuilder().requireExplicitBindings().addModules(new AbstractModule() {
      @Override
      protected void configure() {
        bind(ProvBy.class);
      }
    }).build();
    ensureWorks(injector, ProvBy.class);
    ensureFails(injector, true, ProvByProvider.class);
  }
  
  public void testProviderMethods() {
    Injector injector = new InjectorBuilder().requireExplicitBindings().addModules(new AbstractModule() {
      @Override protected void configure() {}
      @SuppressWarnings("unused") @Provides Foo foo() { return new FooImpl(); }
    }).build();
    ensureWorks(injector, Foo.class);
  }
  
  public void testChildInjectors() {
    Injector parent = new InjectorBuilder().requireExplicitBindings().addModules(new AbstractModule() {
      @Override
      protected void configure() {
        bind(Bar.class);
      }
    }).build();
    ensureWorks(parent, Bar.class);
    ensureFails(parent, false, FooImpl.class, FooBar.class, Foo.class);
    
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
    ensureFails(child, true, FooImpl.class);
    ensureFails(parent, false, FooImpl.class, FooBar.class, Foo.class); // parent still doesn't have these
    
    Injector grandchild = child.createChildInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(FooBar.class);
      }
    });
    ensureWorks(grandchild, FooBar.class, Foo.class, Bar.class);
    ensureFails(grandchild, true, FooImpl.class);
    ensureFails(child, true, FooImpl.class);
    ensureFails(parent, false, FooImpl.class, FooBar.class, Foo.class); // parent still doesn't have these    
  }

  public void testPrivateModules() {
    try {
      new InjectorBuilder().requireExplicitBindings().addModules(new AbstractModule() {
        protected void configure() {
          bind(Foo.class).to(FooImpl.class);
  
          install(new PrivateModule() {
            public void configure() {
              bind(FooBar.class);
              expose(FooBar.class);
            }
          });
        }
      }).build();
      fail("should have failed");
    } catch(CreationException expected) {
      assertContains(expected.getMessage(), "1) " + jitFailed(Bar.class));
      assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
    }
  }
  
  private void ensureWorks(Injector injector, Class<?>... classes) {
    for(int i = 0; i < classes.length; i++) {
      injector.getInstance(classes[i]);
      injector.getProvider(classes[i]).get();
      injector.getBinding(classes[i]).getProvider().get();
    }
  }
  
  private void ensureFails(Injector injector, boolean allowGetBinding, Class<?>... classes) {
    for(int i = 0; i < classes.length; i++) {      
      try { 
        injector.getInstance(classes[i]);
        fail("should have failed");
      } catch(ConfigurationException expected) {
        assertContains(expected.getMessage(), "1) " + jitFailed(classes[i]));
        assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
      }
      
      try { 
        injector.getProvider(classes[i]);
        fail("should have failed");
      } catch(ConfigurationException expected) {
        assertContains(expected.getMessage(), "1) " + jitFailed(classes[i]));
        assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
      }
      
      if(allowGetBinding) {
        Binding<?> binding = injector.getBinding(classes[i]);
        try {
          binding.getProvider();
          fail("should have failed");
        } catch(ConfigurationException expected) {
          assertContains(expected.getMessage(), "1) " + jitFailed(classes[i]));
          assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
        }
      } else {
        try {
          injector.getBinding(classes[i]);
          fail("should have failed");          
        } catch(ConfigurationException expected) {
          assertContains(expected.getMessage(), "1) " + jitFailed(classes[i]));
          assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
        }
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
}
