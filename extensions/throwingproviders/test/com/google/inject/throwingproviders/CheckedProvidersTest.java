package com.google.inject.throwingproviders;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.inject.TypeLiteral;
import java.util.Arrays;
import junit.framework.TestCase;

/**
 * Unit tests for {@link CheckedProviders}.
 *
 * @author eatnumber1@google.com (Russ Harmon)
 */
public final class CheckedProvidersTest extends TestCase {
  private static interface StringCheckedProvider extends CheckedProvider<String> {}

  public void testCheckedProviderClass_get_returnsValidString() throws Exception {
    String expected = "rick";

    StringCheckedProvider provider = CheckedProviders.of(StringCheckedProvider.class, expected);
    assertThat(provider.get()).isEqualTo(expected);
  }

  public void testCheckedProviderTypeLiteral_get_returnsValidString() throws Exception {
    String expected = "morty";

    StringCheckedProvider provider =
        CheckedProviders.of(TypeLiteral.get(StringCheckedProvider.class), expected);
    assertThat(provider.get()).isEqualTo(expected);
  }

  public void testCheckedProviderClassNull_get_returnsNull() throws Exception {
    StringCheckedProvider provider = CheckedProviders.of(StringCheckedProvider.class, null);
    assertThat(provider.get()).isNull();
  }

  public void testCheckedProviderTypeLiteralNull_get_returnsNull() throws Exception {
    StringCheckedProvider provider =
        CheckedProviders.of(TypeLiteral.get(StringCheckedProvider.class), null);
    assertThat(provider.get()).isNull();
  }

  private static final class FooException extends Exception {}

  private interface FooCheckedProvider extends CheckedProvider<Object> {
    @Override
    Object get() throws FooException;
  }

  public void testThrowingCheckedProviderClass_get_throwsException() {
    FooCheckedProvider provider =
        CheckedProviders.throwing(FooCheckedProvider.class, FooException.class);
    try {
      provider.get();
      fail();
    } catch (FooException expected) {
    }
  }

  public void testThrowingCheckedProviderTypeLiteral_get_throwsException() {
    FooCheckedProvider provider =
        CheckedProviders.throwing(TypeLiteral.get(FooCheckedProvider.class), FooException.class);
    try {
      provider.get();
      fail();
    } catch (FooException expected) {
    }
  }

  private interface MoreMethodsCheckedProvider<T> extends CheckedProvider<T> {
    @Override
    T get() throws FooException;

    void otherMethod();
  }

  public void testUnsupportedMethods_otherMethod_throwsIllegalArgumentException()
      throws NoSuchMethodException {
    String message =
        String.format(
            "%s may not declare any new methods, but declared %s",
            MoreMethodsCheckedProvider.class.getName(),
            Arrays.toString(MoreMethodsCheckedProvider.class.getDeclaredMethods()));

    try {
      CheckedProviders.of(
          new TypeLiteral<MoreMethodsCheckedProvider<String>>() {}, "SHOW ME WHAT YOU GOT");
      fail("Expected an exception to be thrown");
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessageThat().isEqualTo(message);
    }
  }

  private static final class StringException extends RuntimeException {
    StringException(String arg) {}
  }

  public void testCheckThrowable_unsupportedThrowableConstructor_throwsIllegalArgumentException() {
    String message =
        String.format(
            "Thrown exception <%s> must have a no-argument constructor",
            StringException.class.getName());

    try {
      CheckedProviders.throwing(FooCheckedProvider.class, StringException.class);
      fail("Expected an exception to be thrown");
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessageThat().isEqualTo(message);
      assertWithMessage(String.format("exception <%s> with cause", e))
          .that(e.getCause())
          .isInstanceOf(NoSuchMethodException.class);
    }
  }

  private static final class BarException extends Exception {}

  public void testCheckThrowable_checkedExceptionNotDeclared_throwsIllegalArgumentException()
      throws Exception {
    String message =
        String.format(
            "Thrown exception <%s> is not declared to be thrown by <%s>",
            BarException.class.getName(), FooCheckedProvider.class.getMethod("get"));

    try {
      CheckedProviders.throwing(FooCheckedProvider.class, BarException.class);
      fail("Expected an exception to be thrown");
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessageThat().isEqualTo(message);
    }
  }

  private static final class ExpectedRuntimeException extends RuntimeException {}

  public void testCheckThrowable_runtimeExceptionNotDeclared_throwsExpectedRuntimeException()
      throws Exception {
    FooCheckedProvider provider =
        CheckedProviders.throwing(FooCheckedProvider.class, ExpectedRuntimeException.class);

    try {
      provider.get();
      fail("Expected an exception to be thrown");
    } catch (ExpectedRuntimeException e) {
      // expected
    }
  }

  private static final class ExpectedError extends Error {}

  public void testCheckThrowable_errorNotDeclared_throwsExpectedError() throws Exception {
    FooCheckedProvider provider =
        CheckedProviders.throwing(FooCheckedProvider.class, ExpectedError.class);

    try {
      provider.get();
      fail("Expected an exception to be thrown");
    } catch (ExpectedError e) {
      // expected
    }
  }
}
