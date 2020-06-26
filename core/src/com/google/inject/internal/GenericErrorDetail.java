package com.google.inject.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.inject.spi.ErrorDetail;
import java.io.Serializable;
import java.util.Formatter;
import java.util.List;

/** Generic error message representing a Guice error. */
public final class GenericErrorDetail extends ErrorDetail<GenericErrorDetail>
    implements Serializable {
  public GenericErrorDetail(String message, List<Object> sources, Throwable cause) {
    super(checkNotNull(message, "message"), sources, cause);
  }

  @Override
  public void format(int index, List<ErrorDetail<?>> mergeableErrors, Formatter formatter) {
    Preconditions.checkArgument(mergeableErrors.isEmpty(), "Unexpected mergeable errors");
    formatter.format("%s) %s%n", index, getMessage());

    List<Object> dependencies = getSources();
    for (Object source : Lists.reverse(dependencies)) {
      Messages.formatSource(formatter, source);
    }
  }

  @Override
  public GenericErrorDetail withSources(List<Object> newSources) {
    return new GenericErrorDetail(getMessage(), newSources, getCause());
  }
}
