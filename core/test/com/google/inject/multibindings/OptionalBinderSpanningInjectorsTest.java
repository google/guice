package com.google.inject.multibindings;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Optional;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.PrivateModule;
import com.google.inject.TypeLiteral;
import junit.framework.TestCase;

public class OptionalBinderSpanningInjectorsTest extends TestCase {

  public void testDefaultInParentActualInChild() {
    Injector injector = Guice.createInjector(
        new AbstractModule() {
          @Override
          protected void configure() {
            OptionalBinder.newOptionalBinder(binder(), String.class).setDefault().toInstance("Default");
            
            install(new PrivateModule() {
              @Override
              protected void configure() {
                // Use new API to contribute to the parent's OptionalBinder
                OptionalBinder.newOptionalContributor(binder(), String.class)
                    .permitSpanInjectors()
                    .setBinding().toInstance("Actual");
              }
            });
          }
        });

    Optional<String> optional = injector.getInstance(Key.get(new TypeLiteral<Optional<String>>() {}));
    assertThat(optional.get()).isEqualTo("Actual");
  }

  public void testNoDefaultInParentActualInChild() {
    Injector injector = Guice.createInjector(
        new AbstractModule() {
          @Override
          protected void configure() {
            OptionalBinder.newOptionalBinder(binder(), String.class);
            
            install(new PrivateModule() {
              @Override
              protected void configure() {
                OptionalBinder.newOptionalContributor(binder(), String.class)
                    .permitSpanInjectors()
                    .setBinding().toInstance("Actual");
              }
            });
          }
        });

    Optional<String> optional = injector.getInstance(Key.get(new TypeLiteral<Optional<String>>() {}));
    assertThat(optional.isPresent()).isTrue();
    assertThat(optional.get()).isEqualTo("Actual");
  }

  public void testDefaultInChildActualInParent() {
    Injector injector = Guice.createInjector(
        new AbstractModule() {
          @Override
          protected void configure() {
            OptionalBinder.newOptionalBinder(binder(), String.class).setBinding().toInstance("Actual");
            
            install(new PrivateModule() {
              @Override
              protected void configure() {
                OptionalBinder.newOptionalContributor(binder(), String.class)
                    .permitSpanInjectors()
                    .setDefault().toInstance("Default");
              }
            });
          }
        });

    Optional<String> optional = injector.getInstance(Key.get(new TypeLiteral<Optional<String>>() {}));
    // Actual overrides Default even if Default is in child.
    assertThat(optional.get()).isEqualTo("Actual");
  }
}