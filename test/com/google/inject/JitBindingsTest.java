package com.google.inject;

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
    Injector injector = Guice.createInjectorBuilder().requireExplicitBindings().addModules(new AbstractModule() {
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
    Injector injector = Guice.createInjectorBuilder().requireExplicitBindings().addModules(new AbstractModule() {
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
  
  public void testLinkedProviderBindingWorks() {
    Injector injector = Guice.createInjectorBuilder().requireExplicitBindings().addModules(new AbstractModule() {
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
      Guice.createInjectorBuilder().requireExplicitBindings().build().getInstance(Bar.class);
      fail("should have failed");
    } catch(ConfigurationException expected) {
      Asserts.assertContains(expected.getMessage(), "1) " + jitFailed(Bar.class));
      assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
    }
  }
  
  public void testJitInjectionFails() {
    try {
      Guice.createInjectorBuilder().requireExplicitBindings().addModules(new AbstractModule() {
        @Override
        protected void configure() {
          bind(Foo.class).to(FooImpl.class);
          bind(FooBar.class);
        }
      }).build();
      fail("should have failed");
    } catch (CreationException expected) {
      Asserts.assertContains(expected.getMessage(), "1) " + jitFailed(Bar.class));
      assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
    }
  }

  public void testJitProviderGetFails() {
    try {
      Guice.createInjectorBuilder().requireExplicitBindings().build().getProvider(Bar.class);
      fail("should have failed");
    } catch (ConfigurationException expected) {
      Asserts.assertContains(expected.getMessage(), "1) " + jitFailed(Bar.class));
      assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
    }
  }

  public void testJitProviderInjectionFails() {
    try {
      Guice.createInjectorBuilder().requireExplicitBindings().addModules(new AbstractModule() {
        @Override
        protected void configure() {
          bind(Foo.class).to(FooImpl.class);
          bind(ProviderFooBar.class);
        }
      }).build();
      fail("should have failed");
    } catch (CreationException expected) {
      Asserts.assertContains(expected.getMessage(), "1) " + jitFailed(Bar.class));
      assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
    }
  }
  
  public void testImplementedBy() {
    Injector injector = Guice.createInjectorBuilder().requireExplicitBindings().addModules(new AbstractModule() {
      @Override
      protected void configure() {
        bind(ImplBy.class);
      }
    }).build();
    ensureWorks(injector, ImplBy.class);
    ensureFails(injector, true, ImplByImpl.class);
  }
  
  public void testProvidedBy() {
    Injector injector = Guice.createInjectorBuilder().requireExplicitBindings().addModules(new AbstractModule() {
      @Override
      protected void configure() {
        bind(ProvBy.class);
      }
    }).build();
    ensureWorks(injector, ProvBy.class);
    ensureFails(injector, true, ProvByProvider.class);
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
        Asserts.assertContains(expected.getMessage(), "1) " + jitFailed(classes[i]));
        assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
      }
      
      try { 
        injector.getProvider(classes[i]);
        fail("should have failed");
      } catch(ConfigurationException expected) {
        Asserts.assertContains(expected.getMessage(), "1) " + jitFailed(classes[i]));
        assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
      }
      
      if(allowGetBinding) {
        Binding<?> binding = injector.getBinding(classes[i]);
        try {
          binding.getProvider();
          fail("should have failed");
        } catch(ConfigurationException expected) {
          Asserts.assertContains(expected.getMessage(), "1) " + jitFailed(classes[i]));
          assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
        }
      } else {
        try {
          injector.getBinding(classes[i]);
          fail("should have failed");          
        } catch(ConfigurationException expected) {
          Asserts.assertContains(expected.getMessage(), "1) " + jitFailed(classes[i]));
          assertTrue(expected.getMessage(), !expected.getMessage().contains("2) "));
        }
      }
    }
  }  
  
  private static interface Foo {}
  private static class FooImpl implements Foo {}
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
    @Override
    public Foo get() {
      return new FooImpl();
    }
  }

  @ImplementedBy(ImplByImpl.class)
  private static interface ImplBy {}
  private static class ImplByImpl implements ImplBy {}

  @ProvidedBy(ProvByProvider.class)
  private static interface ProvBy {}
  private static class ProvByProvider implements Provider<ProvBy> {
    @Override
    public ProvBy get() {
      return new ProvBy() {};
    }
  }
}
