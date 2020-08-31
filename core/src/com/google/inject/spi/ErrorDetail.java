package com.google.inject.spi;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.inject.internal.InternalFlags;
import com.google.inject.internal.Messages;
import java.io.Serializable;
import java.util.Formatter;
import java.util.List;
import java.util.Optional;

/**
 * Details about a single Guice error and supports formatting itself in the context of other Guice
 * errors.
 *
 * <p>WARNING: The class and its APIs are still experimental and subject to change.
 */
public abstract class ErrorDetail<SelfT extends ErrorDetail<SelfT>> implements Serializable {
  private final String message;
  private final ImmutableList<Object> sources;
  private final Throwable cause;

  protected ErrorDetail(String message, List<Object> sources, Throwable cause) {
    this.message = message;
    this.sources = ImmutableList.copyOf(sources);
    this.cause = cause;
  }

  /**
   * Returns true if this error can be merged with the {@code otherError} and formatted together.
   *
   * <p>By default this return false and implementations that support merging with other errors
   * should override this method.
   */
  public boolean isMergeable(ErrorDetail<?> otherError) {
    return false;
  }

  /**
   * Formats this error along with other errors that are mergeable with this error.
   *
   * <p>{@code mergeableErrors} is a list that contains all other errors that are reported in the
   * same exception that are considered to be mergable with this error base on result of calling
   * {@link #isMergeable}. The list will be empty if non of the other errors are mergable with this
   * error.
   *
   * <p>Formatted error has the following structure:
   *
   * <ul>
   *   <li>Summary of the error
   *   <li>Details about the error such as the source of the error
   *   <li>Hints for fixing the error if available
   *   <li>Link to the documentation on this error in greater detail
   *
   * @param index index for this error
   * @param mergeableErrors list of errors that are mergeable with this error
   * @param formatter for printing the error message
   */
  public final void format(int index, List<ErrorDetail<?>> mergeableErrors, Formatter formatter) {
    if (InternalFlags.enableExperimentalErrorMessages()) {
      String id = getErrorIdentifier().map(s -> "[" + Messages.redBold(s) + "]: ").orElse("");
      formatter.format("%s) %s%s%n", index, id, getMessage());
      formatDetail(mergeableErrors, formatter);
      // TODO(b/151482394): Output potiential fixes for the error
      Optional<String> learnMoreLink = getLearnMoreLink();
      if (learnMoreLink.isPresent()) {
        formatter.format("%n%s%n", Messages.bold("Learn more:"));
        formatter.format("  %s%n", Messages.underline(learnMoreLink.get()));
      }
    } else {
      // TODO(b/151482394): Remove this once the new error messages are enabled.
      formatter.format("%s) %s%n", index, getMessage());
      formatDetail(mergeableErrors, formatter);
      return;
    }
  }

  /**
   * Formats the detail of this error message along with other errors that are mergeable with this
   * error. This is called from {@link #format}.
   *
   * <p>{@code mergeableErrors} is a list that contains all other errors that are reported in the
   * same exception that are considered to be mergable with this error base on result of calling
   * {@link #isMergeable}. The list will be empty if non of the other errors are mergable with this
   * error.
   *
   * @param mergeableErrors list of errors that are mergeable with this error
   * @param formatter for printing the error message
   */
  protected abstract void formatDetail(List<ErrorDetail<?>> mergeableErrors, Formatter formatter);

  /**
   * Returns an optional link to additional documentation about this error to be included in the
   * formatted error message.
   */
  protected Optional<String> getLearnMoreLink() {
    return Optional.empty();
  }

  /** Returns an optional string identifier for this error. */
  protected Optional<String> getErrorIdentifier() {
    return Optional.empty();
  }

  public String getMessage() {
    return message;
  }

  public List<Object> getSources() {
    return sources;
  }

  public Throwable getCause() {
    return cause;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(message, cause, sources);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ErrorDetail)) {
      return false;
    }
    ErrorDetail<?> e = (ErrorDetail<?>) o;
    return message.equals(e.message) && Objects.equal(cause, e.cause) && sources.equals(e.sources);
  }

  /** Returns a new instance of the same {@link ErrorDetail} with updated sources. */
  public abstract SelfT withSources(List<Object> newSources);
}
