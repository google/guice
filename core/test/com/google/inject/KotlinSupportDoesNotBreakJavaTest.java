package com.google.inject;

import static com.google.common.truth.Truth.assertThat;

import javax.inject.Inject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that code in {@link KotlinSupport} doesn't break when analyzing Java code. */
@RunWith(JUnit4.class)
public class KotlinSupportDoesNotBreakJavaTest {

  private static class InjectedViaConstructor {
    final String s;

    @Inject
    InjectedViaConstructor(String s) {
      this.s = s;
    }
  }

  @Test
  public void testConstructorInjection() {
    String s =
        Guice.createInjector(
                new AbstractModule() {
                  @Override
                  protected void configure() {
                    bind(String.class).toInstance("test");
                  }
                })
            .getInstance(InjectedViaConstructor.class)
            .s;
    assertThat(s).isEqualTo("test");
  }

  private static class InjectedViaMethod {
    String s;

    @Inject
    void setter(String s) {
      this.s = s;
    }
  }

  @Test
  public void testMethodInjection() {
    String s =
        Guice.createInjector(
                new AbstractModule() {
                  @Override
                  protected void configure() {
                    bind(String.class).toInstance("test");
                  }
                })
            .getInstance(InjectedViaMethod.class)
            .s;
    assertThat(s).isEqualTo("test");
  }
}
