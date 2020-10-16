package com.google.inject.errors;

import static com.google.inject.errors.ErrorMessageTestUtils.assertGuiceErrorEqualsIgnoreLineNumber;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.google.inject.AbstractModule;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Provides;
import com.google.inject.internal.InternalFlags;
import com.google.inject.internal.InternalFlags.IncludeStackTraceOption;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class MissingConstructorErrorTest {

  @Before
  public void checkStackTraceIsIncluded() {
    // Only run the tests when the stack traces are included in the errors.
    assumeTrue(InternalFlags.getIncludeStackTraceOption() != IncludeStackTraceOption.OFF);
  }

  static class NoArgConstructorWithAtInject {
    @Inject
    NoArgConstructorWithAtInject() {}
  }

  static class NoArgConstructorWithoutAtInject {
    NoArgConstructorWithoutAtInject() {}
  }

  static class MissingNoArgConstructor {
    MissingNoArgConstructor(int param) {}
  }

  static class MissingAtInjectConstructor {
    MissingAtInjectConstructor() {}
  }

  static class PrivateConstructor {
    private PrivateConstructor() {}
  }

  private static class PrivateClassWithPrivateConstructor {
    private PrivateClassWithPrivateConstructor() {}
  }

  static class MissingConstructorModule extends AbstractModule {
    @Provides
    static Object provideObject(
        NoArgConstructorWithAtInject noArgConstructorWithAtInject,
        NoArgConstructorWithoutAtInject noArgConstructorWithoutAtInject,
        MissingNoArgConstructor missingNoArgConstructor,
        PrivateConstructor privateConstructor,
        PrivateClassWithPrivateConstructor privateClassWithPrivateConstructor) {
      return null;
    }
  }

  @Test
  public void missingConstructorErrors() throws Exception {
    CreationException exception =
        assertThrows(
            CreationException.class, () -> Guice.createInjector(new MissingConstructorModule()));
    assertGuiceErrorEqualsIgnoreLineNumber(
        exception.getMessage(), "missing_constructor_errors.txt");
  }

  static class MissingConstructorAtInjectRequiredModule extends AbstractModule {
    @Override
    protected void configure() {
      binder().requireAtInjectOnConstructors();
    }

    @Provides
    static Object provideObject(
        NoArgConstructorWithAtInject noArgConstructorWithAtInject,
        NoArgConstructorWithoutAtInject noArgConstructorWithoutAtInject,
        PrivateConstructor privateConstructor) {
      return null;
    }
  }

  @Test
  public void missingConstructorErrors_atInjectRequired() throws Exception {
    CreationException exception =
        assertThrows(
            CreationException.class,
            () -> Guice.createInjector(new MissingConstructorAtInjectRequiredModule()));
    assertGuiceErrorEqualsIgnoreLineNumber(
        exception.getMessage(), "missing_constructor_errors_at_inject_required.txt");
  }

  static class DependsOnMissingNoArgConstructor {
    @Inject
    DependsOnMissingNoArgConstructor(MissingNoArgConstructor noArgConstructor) {}
  }

  static class DependsOnMissingNoArgConstructorModule extends AbstractModule {
    @Override
    protected void configure() {
      bind(DependsOnMissingNoArgConstructor.class);
    }

    @Provides
    String provideString(PrivateConstructor privateConstructor) {
      return privateConstructor.toString();
    }
  }

  @Test
  public void missingConstructorErrors_merged() throws Exception {
    CreationException exception =
        assertThrows(
            CreationException.class,
            () ->
                Guice.createInjector(
                    new MissingConstructorModule(), new DependsOnMissingNoArgConstructorModule()));
    assertGuiceErrorEqualsIgnoreLineNumber(
        exception.getMessage(), "missing_constructor_errors_merged.txt");
  }
}
