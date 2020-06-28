package com.google.inject.spi;

import static com.google.common.truth.Truth.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.RestrictedBindingSource;
import com.google.inject.util.Modules;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.inject.Named;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for the cleanup of {@link BindingSourceRestriction} data after enforcement.
 *
 * <p>The rest of this class is tested through the public {@code RestrictedBindingSource} API it's
 * implementing.
 *
 * @author vzm@google.com (Vladimir Makaric)
 */
@RunWith(JUnit4.class)
public final class BindingSourceRestrictionTest {

  @RestrictedBindingSource.Permit
  @Retention(RetentionPolicy.RUNTIME)
  @interface Permit1 {}

  @RestrictedBindingSource.Permit
  @Retention(RetentionPolicy.RUNTIME)
  @interface Permit2 {}

  @RestrictedBindingSource.Permit
  @Retention(RetentionPolicy.RUNTIME)
  @interface Permit3 {}

  @Permit1
  static class Module1 extends AbstractModule {
    @Provides
    @Named("1")
    String provideFoo() {
      return "foo";
    }
  }

  @Permit2
  static class Module2 extends AbstractModule {
    @Provides
    @Named("2")
    String provideFoo2() {
      return "foo2";
    }

    @Override
    protected void configure() {
      install(new Module1());
    }
  }

  @Permit3
  static class Module3 extends AbstractModule {
    @Override
    protected void configure() {
      install(new Module2());
    }
  }

  @Test
  public void singleBinder() throws Exception {
    assertThatInjectorIsWiped(Guice.createInjector(new Module3()));
  }

  @Test
  public void multipleNestedBinders() throws Exception {
    assertThatInjectorIsWiped(
        Guice.createInjector(
            Modules.override(
                    Modules.override(new Module3())
                        .with(
                            new AbstractModule() {
                              @Provides
                              @Named("2")
                              String provideFoo2() {
                                return "foo2.1";
                              }
                            }))
                .with(
                    new AbstractModule() {
                      @Provides
                      @Named("1")
                      String provideFoo() {
                        return "foo1.1";
                      }
                    })));
  }

  void assertThatInjectorIsWiped(Injector injector) {
    for (Element element : injector.getElements()) {
      Object source = element.getSource();
      if (source instanceof ElementSource) {
        assertThatTheElementSourceChainIsWiped((ElementSource) source);
      }
    }
  }

  void assertThatTheElementSourceChainIsWiped(ElementSource elementSource) {
    while (elementSource != null) {
      assertThat(
              BindingSourceRestriction.PermitMapConstruction.isElementSourceCleared(elementSource))
          .isTrue();
      elementSource = elementSource.getOriginalElementSource();
    }
  }
}
