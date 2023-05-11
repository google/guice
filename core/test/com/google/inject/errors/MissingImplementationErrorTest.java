package com.google.inject.errors;

import static com.google.common.truth.Truth.assertThat;
import static com.google.inject.errors.ErrorMessageTestUtils.assertGuiceErrorEqualsIgnoreLineNumber;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.google.inject.AbstractModule;
import com.google.inject.ConfigurationException;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.internal.InternalFlags;
import com.google.inject.internal.InternalFlags.IncludeStackTraceOption;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import jakarta.inject.Provider;
import jakarta.inject.Qualifier;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class MissingImplementationErrorTest {

  private static final String GOLDEN_SUFFIX;

  static {
    boolean nestedAnnotUsesDots;
    try {
      nestedAnnotUsesDots =
          RequiresFooUsingMethod.class
              .getDeclaredMethod("setMyString", String.class)
              .getParameters()[0]
              .getAnnotation(Foo.class)
              .toString()
              .equals("@" + MissingImplementationErrorTest.class.getName() + ".Foo()");
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
    if (nestedAnnotUsesDots) {
      GOLDEN_SUFFIX = "_with_dot_annots.txt";
    } else {
      GOLDEN_SUFFIX = ".txt";
    }
  }

  @Before
  public void checkStackTraceIsIncluded() {
    // Only run the tests when the stack traces are included in the errors.
    assumeTrue(InternalFlags.getIncludeStackTraceOption() != IncludeStackTraceOption.OFF);
  }

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  @interface Foo {}

  static class RequiresFooUsingConstructor {
    @Inject
    RequiresFooUsingConstructor(@Foo String ctorParam) {}
  }

  static class RequiresFooUsingField {
    @Inject @Foo private String unused;
  }

  static class RequiresFooUsingMethod {
    @Inject
    public void setMyString(@Foo String myString) {}
  }

  interface Dao {}

  static class RequestHandler {
    @Inject
    RequestHandler(Dao dao) {}
  }

  static class Server {
    @Inject
    Server(Provider<RequestHandler> handler) {}
  }

  static class DependsOnMissingBindings {
    @Inject
    DependsOnMissingBindings(
        RequiresFooUsingConstructor ctorInjection,
        RequiresFooUsingField fieldInjection,
        RequiresFooUsingMethod methodInjection,
        Server server) {}
  }

  static class TestModule extends AbstractModule {
    @Override
    protected void configure() {
      bind(DependsOnMissingBindings.class);
    }
  }

  @Test
  public void missingImplementationErrors() throws Exception {
    CreationException exception =
        assertThrows(CreationException.class, () -> Guice.createInjector(new TestModule()));
    assertGuiceErrorEqualsIgnoreLineNumber(
        exception.getMessage(), "missing_implementation_errors" + GOLDEN_SUFFIX);
  }

  static class TestModule1 extends AbstractModule {
    @Override
    protected void configure() {
      bind(Server.class);
    }
  }

  static class TestModule2 extends AbstractModule {
    @Override
    protected void configure() {
      install(new TestModule1());
    }
  }

  @Test
  public void missingImplementationWithModuleStack() throws Exception {
    CreationException exception =
        assertThrows(CreationException.class, () -> Guice.createInjector(new TestModule2()));
    assertGuiceErrorEqualsIgnoreLineNumber(
        exception.getMessage(), "missing_implementation_with_module_stack.txt");
  }

  @Qualifier
  @Retention(RUNTIME)
  @interface A {}

  @Qualifier
  @Retention(RUNTIME)
  @interface B {}

  static class Klass {}

  static class Klass2 {
    Klass2(int i) {}
  }

  static class HintsModule extends AbstractModule {
    @Provides
    @A
    Klass provideKlass() {
      return new Klass();
    }

    // Inject @B Klass triggers a suggestion to use @A Klass.
    @Provides
    String provideString(@B Klass missing) {
      return "string";
    }

    // Inject @A Klass2 triggers a suggestion to use @A Klass.
    @Provides
    @A
    String provideAString(@A Klass2 klass2) {
      return "string a";
    }
  }

  @Test
  public void missingImplementationWithHints() throws Exception {
    CreationException exception =
        assertThrows(CreationException.class, () -> Guice.createInjector(new HintsModule()));
    assertGuiceErrorEqualsIgnoreLineNumber(
        exception.getMessage(), "missing_implementation_with_hints" + GOLDEN_SUFFIX);
  }

  private static interface CustomType {
    static class InnerType {}
  }

  @Test
  public void missingImplementationWithHints_memoizesSuggestion() throws Exception {
    Injector injector = Guice.createInjector();
    ConfigurationException ex =
        assertThrows(ConfigurationException.class, () -> injector.getInstance(CustomType.class));
    // Ensure that the message doesn't contain a "Did you mean?" by default,
    // because there's no other type that fits.
    assertThat(ex).hasMessageThat().doesNotContain("Did you mean?");
    // And even after we insert another type that fits, we don't redo the suggestions.
    injector.getInstance(CustomType.InnerType.class);
    assertThat(ex).hasMessageThat().doesNotContain("Did you mean?");
  }

  @Test
  public void missingImplementationWithHints_lazyInjectorUsage() throws Exception {
    // Note: this test is extremely contrived. This scenario is unlikely to happen for real, but
    // it's a very convenient way to assert that usage of the injector is lazy.
    // By adding a type into the injector after the exception is thrown but before we
    // call getMessage, we're validating that the suggestions are populated only on getMessage
    // usage.
    // This test works in tandem with the above one which asserts that by default,
    // the message *will not* have suggestions.
    Injector injector = Guice.createInjector();
    ConfigurationException ex =
        assertThrows(ConfigurationException.class, () -> injector.getInstance(CustomType.class));
    injector.getInstance(CustomType.InnerType.class);
    assertThat(ex).hasMessageThat().containsMatch("Did you mean?");
    assertThat(ex).hasMessageThat().containsMatch("InnerType");
  }
}
