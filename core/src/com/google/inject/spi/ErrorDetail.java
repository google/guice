package com.google.inject.spi;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import java.io.Serializable;
import java.util.Formatter;
import java.util.List;

/**
 * Details about a single Guice error and supports formatting itself in the context of other Guice
 * errors.
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
   * Formats this error message along with other errors that are mergeable with this error.
   *
   * <p>{@code mergeableErrors} is a list that contains all other errors that are reported in the
   * same exception that are considered to be mergable with this error base on result of calling
   * {@link #isMergeable}. The list will be empty if non of the other errors are mergable with this
   * error.
   *
   * @param index index for this error
   * @param mergeableErrors list of errors that are mergeable with this error
   * @param formatter for printing the error message
   */
  public abstract void format(int index, List<ErrorDetail<?>> mergeableErrors, Formatter formatter);

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
