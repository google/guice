package com.google.inject.testing.throwingproviders;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
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
      extends SubjectFactory<CheckedProviderSubject<T, P>, P> {
    @Override
    public CheckedProviderSubject<T, P> getSubject(
        FailureStrategy failureStrategy, @Nullable P target) {
      return new CheckedProviderSubject<T, P>(failureStrategy, target);
    }
  }

  public static <T, P extends CheckedProvider<T>>
      SubjectFactory<CheckedProviderSubject<T, P>, P> checkedProviders() {
    return new CheckedProviderSubjectFactory<>();
  }

  public static <T, P extends CheckedProvider<T>> CheckedProviderSubject<T, P> assertThat(
      @Nullable P provider) {
    return assertAbout(CheckedProviderSubject.<T, P>checkedProviders()).that(provider);
  }

  private CheckedProviderSubject(FailureStrategy failureStrategy, @Nullable P subject) {
    super(failureStrategy, subject);
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
      failureStrategy.fail(String.format("checked provider <%s> threw an exception", provider), e);
      return ignoreCheck().that(new Object());
    }
    return check().withFailureMessage("value provided by <%s>", provider).that(got);
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
      return check().withFailureMessage("exception thrown by <%s>", provider).that(e);
    }
    failWithBadResults("threw", "an exception", "provided", got);
    return ignoreCheck().that(new Throwable());
  }
}
