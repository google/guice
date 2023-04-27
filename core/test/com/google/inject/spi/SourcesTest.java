package com.google.inject.spi;

import static com.google.common.truth.Truth.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Module;
import org.junit.jupiter.api.Test;

/** Tests sources are set correctly in elements. */
public final class SourcesTest {

  @Test
  public void entirelyFilteredSourceShowsAsUnknown() {
    ElementSource source =
        (ElementSource)
            Guice.createInjector(
                    new AbstractModule() {
                      @Override
                      protected void configure() {
                        binder().skipSources(getClass()).bind(String.class).toInstance("Foo");
                      }
                    })
                .getBinding(String.class)
                .getSource();
    assertThat(source.getDeclaringSource()).isEqualTo("[unknown source]");
  }

  @Test
  public void unfilteredShowsCorrectly() {
    Module m =
        new AbstractModule() {
          @Override
          protected void configure() {
            binder().bind(String.class).toInstance("Foo");
          }
        };
    ElementSource source =
        (ElementSource) Guice.createInjector(m).getBinding(String.class).getSource();
    StackTraceElement ste = (StackTraceElement) source.getDeclaringSource();
    assertThat(ste.getClassName()).isEqualTo(m.getClass().getName());
    assertThat(ste.getMethodName()).isEqualTo("configure");
  }
}
