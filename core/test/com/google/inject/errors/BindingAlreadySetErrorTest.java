package com.google.inject.errors;

import static com.google.inject.errors.ErrorMessageTestUtils.assertGuiceErrorEqualsIgnoreLineNumber;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.internal.InternalFlags;
import com.google.inject.internal.InternalFlags.IncludeStackTraceOption;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.ModuleAnnotatedMethodScanner;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class BindingAlreadySetErrorTest {

  @Before
  public void checkStackTraceIsIncluded() {
    // Only run the tests when the stack traces are included in the errors.
    assumeTrue(InternalFlags.getIncludeStackTraceOption() != IncludeStackTraceOption.OFF);
  }

  static class First {}

  static class Second {}

  static class BindWithProviderMethodModule extends AbstractModule {
    @Provides
    First provideFirstClass() {
      return new First();
    }
  }

  static class BindWithDslModule extends AbstractModule {
    @Override
    protected void configure() {
      bind(First.class);
      bind(Second.class);
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface TestProvides {}

  static class BindWithCustomScannerModule extends AbstractModule {

    @Override
    protected void configure() {
      binder().scanModulesForAnnotatedMethods(new TestProvidesScanner());
    }

    @TestProvides
    Second second() {
      return new Second();
    }
  }

  static class TestProvidesScanner extends ModuleAnnotatedMethodScanner {

    @Override
    public Set<? extends Class<? extends Annotation>> annotationClasses() {
      return ImmutableSet.of(TestProvides.class);
    }

    @Override
    public <T> Key<T> prepareMethod(
        Binder binder, Annotation annotation, Key<T> key, InjectionPoint injectionPoint) {
      return key;
    }
  }

  @Test
  public void singleBindingAlreadySetError() {
    CreationException exception =
        assertThrows(
            CreationException.class,
            () ->
                Guice.createInjector(new BindWithProviderMethodModule(), new BindWithDslModule()));
    assertGuiceErrorEqualsIgnoreLineNumber(
        exception.getMessage(), "single_binding_already_set_error.txt");
  }

  @Test
  public void multipleBindingAlreadySetErrors() {
    CreationException exception =
        assertThrows(
            CreationException.class,
            () ->
                Guice.createInjector(
                    new BindWithCustomScannerModule(),
                    new BindWithProviderMethodModule(),
                    new BindWithDslModule()));
    assertGuiceErrorEqualsIgnoreLineNumber(
        exception.getMessage(), "multiple_binding_already_set_errors.txt");
  }

  static class TestModule1 extends AbstractModule {
    @Override
    protected void configure() {
      install(new BindWithProviderMethodModule());
    }
  }

  static class TestModule2 extends AbstractModule {
    @Override
    protected void configure() {
      install(
          new AbstractModule() {
            @Override
            protected void configure() {
              install(new BindWithProviderMethodModule());
            }
          });
    }
  }

  @Test
  public void bindingAlreadySetErrorsWithModuleStack() {
    CreationException exception =
        assertThrows(
            CreationException.class,
            () -> Guice.createInjector(new TestModule1(), new TestModule2()));
    assertGuiceErrorEqualsIgnoreLineNumber(
        exception.getMessage(), "binding_already_set_errors_with_module_stack.txt");
  }
}
