package com.google.inject.errors;

import static com.google.inject.errors.ErrorMessageTestUtils.assertGuiceErrorEqualsIgnoreLineNumber;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.google.inject.AbstractModule;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Provides;
import com.google.inject.internal.InternalFlags;
import com.google.inject.internal.InternalFlags.IncludeStackTraceOption;
import java.lang.annotation.Retention;
import jakarta.inject.Qualifier;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ErrorMessagesTest {

  @Before
  public void checkStackTraceIsIncluded() {
    // Only run the tests when the stack traces are included in the errors.
    assumeTrue(InternalFlags.getIncludeStackTraceOption() != IncludeStackTraceOption.OFF);
  }

  static class OuterClass {
    @Qualifier
    @Retention(RUNTIME)
    @interface UserId {}

    interface Foo {}

    private OuterClass() {}
  }

  @Qualifier
  @Retention(RUNTIME)
  @interface UserId {}

  static class Foo {}

  static class MissingBindingsModule extends AbstractModule {
    @Provides
    static Foo provideFoo(@UserId String unused) {
      return new Foo();
    }

    @Provides
    static OuterClass.Foo providerAnotherFoo(@OuterClass.UserId Long unused) {
      return null;
    }
  }

  @Test
  public void classNamesAreCompressedInErrorMessage() throws Exception {
    CreationException exception =
        assertThrows(
            CreationException.class, () -> Guice.createInjector(new MissingBindingsModule()));
    // Newer JDKs print nested annotations with dots, whereas older JDKs use $.
    // Check to see which kind of annotation output we expect, and use the appropriate golden
    boolean nestedAnnotUsesDots =
        MissingBindingsModule.class
            .getDeclaredMethod("provideFoo", String.class)
            .getParameters()[0]
            .getAnnotation(UserId.class)
            .toString()
            .equals("@" + ErrorMessagesTest.class.getName() + ".UserId()");
    String golden =
        nestedAnnotUsesDots
            ? "class_names_are_compressed_in_error_message_with_dot_annots.txt"
            : "class_names_are_compressed_in_error_message.txt";
    assertGuiceErrorEqualsIgnoreLineNumber(exception.getMessage(), golden);
  }
}
