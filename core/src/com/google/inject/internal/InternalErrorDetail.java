package com.google.inject.internal;

import com.google.common.base.CaseFormat;
import com.google.inject.spi.ErrorDetail;
import java.util.List;
import java.util.Optional;

/**
 * Represents an error created by Guice as opposed to custom error added to the binder from
 * application code.
 */
abstract class InternalErrorDetail<T extends ErrorDetail<T>> extends ErrorDetail<T> {
  private static final String DOC_BASE_URL = "https://github.com/googel/guice/wiki";

  protected final ErrorId errorId;

  protected InternalErrorDetail(
      ErrorId errorId, String message, List<Object> sources, Throwable cause) {
    super(
        String.format(
            "Guice/%s", CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, errorId.name())),
        message,
        sources,
        cause);
    this.errorId = errorId;
  }

  @Override
  protected final Optional<String> getLearnMoreLink() {
    return Optional.of(String.format("%s/%s", DOC_BASE_URL, errorId.name()));
  }
}
