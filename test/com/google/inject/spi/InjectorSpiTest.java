package com.google.inject.spi;

import java.util.Map;

import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;

import junit.framework.TestCase;

/**
 * @author sberlin@gmail.com (Sam Berlin)
 */
public class InjectorSpiTest extends TestCase {

  public void testExistingBinding() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(Foo.class);
        bind(Baz.class);
      }
    });
    // Sanity check -- ensure we return the proper binding for all existing bindings.
    for(Map.Entry<Key<?>, Binding<?>> entry : injector.getAllBindings().entrySet()) {
      assertSame(entry.getValue(), injector.getExistingBinding(entry.getKey()));
    }
    
    // Now run through specifics...
    Binding<?> binding;
    
    // 1) non-Provider Foo.class
    binding = injector.getExistingBinding(Key.get(Foo.class));
    assertNotNull(binding);
    assertEquals(Foo.class, binding.getKey().getTypeLiteral().getRawType());
    
    // 2) Provider<Foo> class (should already exist, because Baz @Injects it).
    // the assertTrue is a bit stricter than necessary, but makes sure this works for pre-existing Provider bindings
    assertTrue(injector.getAllBindings().containsKey(Key.get(new TypeLiteral<Provider<Foo>>() {})));
    binding = injector.getExistingBinding(Key.get(new TypeLiteral<Provider<Foo>>() {}));
    assertNotNull(binding);
    assertEquals(Provider.class, binding.getKey().getTypeLiteral().getRawType());
    assertEquals(Foo.class, ((Provider)binding.getProvider().get()).get().getClass());
    
    // 3) non-Provider Baz.class
    binding = injector.getExistingBinding(Key.get(Baz.class));
    assertNotNull(binding);
    assertEquals(Baz.class, binding.getKey().getTypeLiteral().getRawType());
    
    // 4) Provider<Baz> class (should not already exist, because nothing used it yet).
    // the assertFalse is a bit stricter than necessary, but makes sure this works for non-pre-existing Provider bindings
    assertFalse(injector.getAllBindings().containsKey(Key.get(new TypeLiteral<Provider<Baz>>() {})));    
    binding = injector.getExistingBinding(Key.get(new TypeLiteral<Provider<Baz>>() {}));
    assertNotNull(binding);
    assertEquals(Provider.class, binding.getKey().getTypeLiteral().getRawType());
    assertEquals(Baz.class, ((Provider)binding.getProvider().get()).get().getClass());
    
    // 5) non-Provider Bar, doesn't exist.
    assertNull(injector.getExistingBinding(Key.get(Bar.class)));
    
    // 6) Provider Bar, doesn't exist.
    assertNull(injector.getExistingBinding(Key.get(new TypeLiteral<Provider<Bar>>() {})));
  }
  
  private static class Foo {}
  private static class Bar {}
  private static class Baz { @SuppressWarnings("unused") @Inject Provider<Foo> fooP; }
  
}
