package com.google.inject.testing.throwingproviders;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.common.truth.TestVerb;
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

  private final TestVerb assertVerb;

  @VisibleForTesting
  CheckedProviderSubject(FailureStrategy failureStrategy, @Nullable P subject) {
    super(failureStrategy, subject);

    this.assertVerb = new TestVerb(failureStrategy);
  }

  private TestVerb assert_() {
    return assertVerb;
  }

  public static <T, P extends CheckedProvider<T>> CheckedProviderSubject<T, P> assertThat(
      @Nullable P provider) {
    return assertAbout(new CheckedProviderSubjectFactory<T, P>()).that(provider);
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
    P provider = getSubject();
    T got;
    try {
      got = provider.get();
    } catch (Exception e) {
      failureStrategy.fail(String.format("checked provider <%s> threw an exception", provider), e);
      throw new AssertionError(e);
    }
    return assert_().withFailureMessage("value provided by <%s>", provider).that(got);
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
    P provider = getSubject();
    T got;
    try {
      got = provider.get();
    } catch (Throwable e) {
      return assert_().withFailureMessage("exception thrown by <%s>", provider).that(e);
    }
    failWithBadResults("threw", "an exception", "provided", got);
    throw new AssertionError("Impossible, I hope...");
  }
}
