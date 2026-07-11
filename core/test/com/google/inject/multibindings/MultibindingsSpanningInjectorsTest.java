package com.google.inject.multibindings;

import static com.google.common.truth.Truth.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.PrivateModule;
import com.google.inject.TypeLiteral;
import java.util.Map;
import java.util.Set;
import junit.framework.TestCase;

public class MultibindingsSpanningInjectorsTest extends TestCase {

  public static class MainModule extends AbstractModule {
    @Override
    protected void configure() {
      Multibinder.newSetBinder(binder(), String.class).addBinding().toInstance("Main");
      install(new ChildModule());
    }
  }

  public static class ChildModule extends PrivateModule {
    @Override
    protected void configure() {
      // Use the new API to contribute to the parent's Multibinder
      Multibinder.newSetContributor(binder(), String.class)
          .permitSpanInjectors()
          .addBinding().toInstance("Private");
    }
  }

  public void testPrivateModuleContributionExposed() {
    Injector injector = Guice.createInjector(new MainModule());

    Set<String> set = injector.getInstance(Key.get(new TypeLiteral<Set<String>>() {}));
    
    assertThat(set).containsExactly("Main", "Private").inOrder();
  }

  public static class PrivateModuleA extends PrivateModule {
    @Override protected void configure() {
      Multibinder.newSetBinder(binder(), String.class).addBinding().toInstance("A");
    }
  }

  public static class PrivateModuleB extends PrivateModule {
    @Override protected void configure() {
      Multibinder.newSetBinder(binder(), String.class).addBinding().toInstance("B");
    }
  }

  public void testSiblings() {
     Injector injector = Guice.createInjector(new PrivateModuleA(), new PrivateModuleB());
     // Siblings are isolated, so this just verifies we didn't break existing isolation/instantiation
  }

  public static class MapMainModule extends AbstractModule {
    @Override
    protected void configure() {
      MapBinder.newMapBinder(binder(), String.class, String.class).addBinding("KeyMain").toInstance("ValueMain");
      install(new MapChildModule());
    }
  }

  public static class MapChildModule extends PrivateModule {
    @Override
    protected void configure() {
      MapBinder.newMapContributor(binder(), String.class, String.class)
          .permitSpanInjectors()
          .addBinding("KeyPrivate").toInstance("ValuePrivate");
    }
  }

  public void testMapBinderContribution() {
    Injector injector = Guice.createInjector(new MapMainModule());
    Map<String, String> map = injector.getInstance(Key.get(new TypeLiteral<Map<String, String>>() {}));
    assertThat(map).containsExactly("KeyMain", "ValueMain", "KeyPrivate", "ValuePrivate");
  }
}
