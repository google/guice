package com.google.inject.errors;

import static com.google.inject.errors.ErrorMessageTestUtils.assertGuiceErrorEqualsIgnoreLineNumber;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.google.inject.AbstractModule;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import com.google.inject.internal.InternalFlags;
import com.google.inject.internal.InternalFlags.IncludeStackTraceOption;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class GenericErrorTest {

  @Before
  public void checkStackTraceIsIncluded() {
    // Only run the tests when the stack traces are included in the errors.
    assumeTrue(InternalFlags.getIncludeStackTraceOption() != IncludeStackTraceOption.OFF);
  }

  static class ErrorModule extends AbstractModule {
    @Override
    protected void configure() {
      binder().addError("Custom error");
    }
  }

  static class BadConstructor {
    BadConstructor() {
      throw new RuntimeException("bad");
    }
  }

  static class ProvisionErrorModule extends AbstractModule {

    @Provides
    String provideString() {
      throw new RuntimeException("can't do that");
    }

    @Provides
    Object provideObject(BadConstructor bad) {
      return bad;
    }
  }

  @Test
  public void customError() {
    CreationException exception =
        assertThrows(CreationException.class, () -> Guice.createInjector(new ErrorModule()));
    assertGuiceErrorEqualsIgnoreLineNumber(exception.getMessage(), "custom_error.txt");
  }

  @Test
  public void errorInCustomProvider() {
    Injector injector = Guice.createInjector(new ProvisionErrorModule());
    ProvisionException exception =
        assertThrows(ProvisionException.class, () -> injector.getInstance(String.class));
    assertGuiceErrorEqualsIgnoreLineNumber(exception.getMessage(), "error_in_custom_provider.txt");
  }

  @Test
  public void errorInjectingConstructor() {
    Injector injector = Guice.createInjector(new ProvisionErrorModule());
    ProvisionException exception =
        assertThrows(ProvisionException.class, () -> injector.getInstance(Object.class));
    assertGuiceErrorEqualsIgnoreLineNumber(
        exception.getMessage(), "error_injecting_constructor.txt");
  }
}
