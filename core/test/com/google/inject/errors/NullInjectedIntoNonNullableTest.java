package com.google.inject.errors;

import static com.google.inject.errors.ErrorMessageTestUtils.assertGuiceErrorEqualsIgnoreLineNumber;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import com.google.inject.internal.InternalFlags;
import com.google.inject.internal.InternalFlags.IncludeStackTraceOption;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import javax.inject.Inject;
import javax.inject.Qualifier;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class NullInjectedIntoNonNullableTest {

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  @interface Bar {}

  static class Foo {
    @Inject
    Foo(@Bar String string) {}
  }

  static class FromProvidesMethodModule extends AbstractModule {
    @Provides
    @Bar
    String provideString() {
      return null;
    }
  }

  static class FromProviderModule extends AbstractModule {
    @Override
    protected void configure() {
      bind(String.class).annotatedWith(Bar.class).toProvider(() -> null);
    }
  }

  static class IntermediateModule extends AbstractModule {
    public static final String NULL = null;

    private static final Module MODULE =
        new AbstractModule() {
          @Override
          protected void configure() {
            Field field = null;
            try {
              field = IntermediateModule.class.getField("NULL");
            } catch (NoSuchFieldException error) {
              throw new AssertionError("NULL field missing!");
            }
            binder()
                .withSource(field)
                .bind(String.class)
                .annotatedWith(Bar.class)
                .toProvider(() -> NULL);
          }
        };

    @Override
    protected void configure() {
      install(MODULE);
    }
  }

  @Before
  public void ensureStackTraceIsIncluded() {
    assumeTrue(InternalFlags.getIncludeStackTraceOption() != IncludeStackTraceOption.OFF);
  }

  @Test
  public void nullReturnedFromProvidesMethod() {
    Injector injector = Guice.createInjector(new FromProvidesMethodModule());

    ProvisionException exception =
        assertThrows(ProvisionException.class, () -> injector.getInstance(Foo.class));
    assertGuiceErrorEqualsIgnoreLineNumber(
        exception.getMessage(), "null_returned_from_provides_method.txt");
  }

  @Test
  public void nullReturnedFromProvider() {
    Injector injector = Guice.createInjector(new FromProviderModule());

    ProvisionException exception =
        assertThrows(ProvisionException.class, () -> injector.getInstance(Foo.class));
    assertGuiceErrorEqualsIgnoreLineNumber(
        exception.getMessage(), "null_returned_from_provider.txt");
  }

  @Test
  public void nullReturnedFromProviderWithModuleStack() {
    Injector injector = Guice.createInjector(new IntermediateModule());

    ProvisionException exception =
        assertThrows(ProvisionException.class, () -> injector.getInstance(Foo.class));
    assertGuiceErrorEqualsIgnoreLineNumber(
        exception.getMessage(), "null_returned_from_provider_with_module_stack.txt");
  }
}
