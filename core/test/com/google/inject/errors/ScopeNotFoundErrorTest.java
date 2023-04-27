package com.google.inject.errors;

import static com.google.inject.errors.ErrorMessageTestUtils.assertGuiceErrorEqualsIgnoreLineNumber;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.inject.AbstractModule;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Provides;
import com.google.inject.ScopeAnnotation;
import com.google.inject.internal.InternalFlags;
import com.google.inject.internal.InternalFlags.IncludeStackTraceOption;
import java.lang.annotation.Retention;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public final class ScopeNotFoundErrorTest {

  @BeforeAll
  public static void checkStackTraceIsIncluded() {
    // Only run the tests when the stack traces are included in the errors.
    assumeTrue(InternalFlags.getIncludeStackTraceOption() != IncludeStackTraceOption.OFF);
  }

  @ScopeAnnotation
  @Retention(RUNTIME)
  @interface BatchScoped {}

  @ScopeAnnotation
  @Retention(RUNTIME)
  @interface RequestScoped {}

  @RequestScoped
  static class RequestHelper {}

  static class BatchHelper {}

  static class ErrorModule extends AbstractModule {
    @Override
    protected void configure() {
      bind(BatchHelper.class).in(BatchScoped.class);
      bind(RequestHelper.class);
    }

    @Provides
    @RequestScoped
    String provideString() {
      return "request scoped";
    }
  }

  @Test
  public void scopeNotFoundError() {
    CreationException exception =
        assertThrows(CreationException.class, () -> Guice.createInjector(new ErrorModule()));

    assertGuiceErrorEqualsIgnoreLineNumber(exception.getMessage(), "scope_not_found_error.txt");
  }
}
