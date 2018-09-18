package com.google.inject.testing.throwingproviders;

import static com.google.common.base.Strings.lenientFormat;
import static com.google.common.truth.Fact.simpleFact;
import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.ThrowableSubject;
import com.google.inject.throwingproviders.CheckedProvider;
import javax.annotation.Nullable;

/**
 * Truth {@link Subject} for use with {@link CheckedProvider} classes.
 *
 * @author eatnumber1@google.com (Russ Harmon)
 */
public final class CheckedProviderSubject<T, P extends CheckedProvider<T>>
    extends Subject<CheckedProviderSubject<T, P>, P> {

  private static final class CheckedProviderSubjectFactory<T, P extends CheckedProvider<T>>
      implements Subject.Factory<CheckedProviderSubject<T, P>, P> {
    @Override
    public CheckedProviderSubject<T, P> createSubject(
        FailureMetadata failureMetadata, @Nullable P target) {
      return new CheckedProviderSubject<T, P>(failureMetadata, target);
    }
  }

  public static <T, P extends CheckedProvider<T>>
      Subject.Factory<CheckedProviderSubject<T, P>, P> checkedProviders() {
    return new CheckedProviderSubjectFactory<>();
  }

  public static <T, P extends CheckedProvider<T>> CheckedProviderSubject<T, P> assertThat(
      @Nullable P provider) {
    return assertAbout(CheckedProviderSubject.<T, P>checkedProviders()).that(provider);
  }

  private CheckedProviderSubject(FailureMetadata failureMetadata, @Nullable P subject) {
    super(failureMetadata, subject);
  }

  /**
   * Allows for assertions on the value provided by this provider.
   *
   * <p>The value provided by a checked provider is the object returned by a call to {@link
   * CheckedProvider#get}
   *
   * @return a {@link Subject} for asserting against the return value of {@link CheckedProvider#get}
   */
  public Subject<?, Object> providedValue() {
    P provider = actual();
    T got;
    try {
      got = provider.get();
    } catch (Exception e) {
      failWithCauseAndMessage(e, "checked provider <%s> threw an exception", provider);
      return ignoreCheck().that(new Object());
    }
    return check().withMessage("value provided by <%s>", provider).that(got);
  }

  /**
   * Allows for assertions on the exception thrown by this provider.
   *
   * <p>The exception thrown by a checked provider is the {@link Throwable} thrown by a call to
   * {@link CheckedProvider#get}
   *
   * @return a {@link ThrowableSubject} for asserting against the {@link Throwable} thrown by {@link
   *     CheckedProvider#get}
   */
  public ThrowableSubject thrownException() {
    P provider = actual();
    T got;
    try {
      got = provider.get();
    } catch (Throwable e) {
      return check().withMessage("exception thrown by <%s>", provider).that(e);
    }
    failWithBadResults("threw", "an exception", "provided", got);
    return ignoreCheck().that(new Throwable());
  }

  /*
   * Hack to get Truth to include a given exception as the cause of the failure. It works by letting
   * us delegate to a new Subject whose value under test is the exception. Because that makes the
   * assertion "about" the exception, Truth includes it as a cause.
   */

  private void failWithCauseAndMessage(Throwable cause, String format, Object... args) {
    check().about(unexpectedFailures()).that(cause).doFail(format, args);
  }

  private static Factory<UnexpectedFailureSubject, Throwable> unexpectedFailures() {
    return new Factory<UnexpectedFailureSubject, Throwable>() {
      @Override
      public UnexpectedFailureSubject createSubject(FailureMetadata metadata, Throwable actual) {
        return new UnexpectedFailureSubject(metadata, actual);
      }
    };
  }

  private static final class UnexpectedFailureSubject
      extends Subject<UnexpectedFailureSubject, Throwable> {
    UnexpectedFailureSubject(FailureMetadata metadata, @Nullable Throwable actual) {
      super(metadata, actual);
    }

    void doFail(String format, Object... args) {
      failWithoutActual(simpleFact(lenientFormat(format, args)));
    }
  }
}
