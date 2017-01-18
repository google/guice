package com.google.inject.testing.throwingproviders;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.truth.FailureStrategy;
import com.google.inject.throwingproviders.CheckedProvider;
import com.google.inject.throwingproviders.CheckedProviders;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link CheckedProviderSubject}.
 *
 * @author eatnumber1@google.com (Russ Harmon)
 */
@RunWith(JUnit4.class)
public class CheckedProviderSubjectTest {
  private static final class FailureStrategyException extends RuntimeException {
    FailureStrategyException() {}

    FailureStrategyException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private static final class ThrowingFailureStrategy extends FailureStrategy {
    @Override
    public void fail(String message, Throwable cause) {
      throw new FailureStrategyException(message, cause);
    }
  }

  private interface StringCheckedProvider extends CheckedProvider<String> {}

  @Test
  public void providedValue_gotExpected_expectSuccess() {
    String expected = "keep Summer safe";
    CheckedProvider<String> provider = CheckedProviders.of(StringCheckedProvider.class, expected);

    createSubject(provider).providedValue().isEqualTo(expected);
  }

  @Test
  public void providedValue_gotUnexpected_expectFailure() {
    String expected = "keep Summer safe";
    String unexpected = "Summer is unsafe";
    CheckedProvider<String> provider = CheckedProviders.of(StringCheckedProvider.class, unexpected);
    String message =
        String.format(
            "value provided by <%s>: Not true that <%s> is equal to <%s>",
            getReturningProviderName(unexpected), unexpected, expected);

    try {
      createSubject(provider).providedValue().isEqualTo(expected);
      fail("Expected an exception to be thrown");
    } catch (FailureStrategyException e) {
      assertThat(e).hasMessage(message);
    }
  }

  private static final class SummerException extends RuntimeException {}

  @Test
  public void providedValue_throws_expectFailure() {
    CheckedProvider<?> provider =
        CheckedProviders.throwing(StringCheckedProvider.class, SummerException.class);
    String message =
        String.format(
            "checked provider <%s> threw an exception",
            getThrowingProviderName(SummerException.class.getName()));

    try {
      createSubject(provider).providedValue();
      fail("Expected an exception to be thrown");
    } catch (FailureStrategyException e) {
      assertThat(e.getCause()).isInstanceOf(SummerException.class);
      assertThat(e).hasMessage(message);
    }
  }

  @Test
  public void thrownException_threwExpected_expectSuccess() {
    CheckedProvider<?> provider =
        CheckedProviders.throwing(StringCheckedProvider.class, SummerException.class);

    createSubject(provider).thrownException().isInstanceOf(SummerException.class);
  }

  @Test
  public void thrownException_threwUnexpected_expectFailure() {
    Class<? extends Throwable> expected = SummerException.class;
    Class<? extends Throwable> unexpected = UnsupportedOperationException.class;
    CheckedProvider<?> provider =
        CheckedProviders.throwing(StringCheckedProvider.class, unexpected);
    String message =
        String.format(
            "exception thrown by <%s>: Not true that <%s> is an instance of <%s>. "
                + "It is an instance of <%s>",
            getThrowingProviderName(UnsupportedOperationException.class.getName()),
            UnsupportedOperationException.class.getName(),
            SummerException.class.getName(),
            UnsupportedOperationException.class.getName());

    try {
      createSubject(provider).thrownException().isInstanceOf(expected);
      fail("Expected an exception to be thrown");
    } catch (FailureStrategyException e) {
      assertThat(e).hasMessage(message);
    }
  }

  @Test
  public void thrownException_gets_expectFailure() {
    String getValue = "keep WINTER IS COMING safe";
    CheckedProvider<?> provider = CheckedProviders.of(StringCheckedProvider.class, getValue);
    String message =
        String.format(
            "Not true that <%s> threw <an exception>. It provided <%s>",
            getReturningProviderName(getValue), getValue);

    try {
      createSubject(provider).thrownException();
      fail("Expected an exception to be thrown");
    } catch (FailureStrategyException e) {
      assertThat(e).hasMessage(message);
    }
  }

  private <T, P extends CheckedProvider<T>> CheckedProviderSubject<T, P> createSubject(P provider) {
    return new CheckedProviderSubject<T, P>(new ThrowingFailureStrategy(), provider);
  }

  private String getReturningProviderName(String providing) {
    return String.format("generated CheckedProvider returning <%s>", providing);
  }

  private String getThrowingProviderName(String throwing) {
    return String.format("generated CheckedProvider throwing <%s>", throwing);
  }
}
