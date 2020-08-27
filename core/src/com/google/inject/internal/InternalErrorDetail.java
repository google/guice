package com.google.inject.internal;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableSet;
import com.google.inject.spi.ErrorDetail;
import java.util.List;
import java.util.Optional;

/**
 * Represents an error created by Guice as opposed to custom error added to the binder from
 * application code.
 */
abstract class InternalErrorDetail<T extends ErrorDetail<T>> extends ErrorDetail<T> {
  // A list of errors that have help documentation.
  private static final ImmutableSet<ErrorId> DOCUMENTED_ERRORS =
      ImmutableSet.<ErrorId>builder()
          .add(ErrorId.BINDING_ALREADY_SET)
          .add(ErrorId.CAN_NOT_PROXY_CLASS)
          .add(ErrorId.CIRCULAR_PROXY_DISABLED)
          .add(ErrorId.DUPLICATE_BINDING_ANNOTATIONS)
          .add(ErrorId.DUPLICATE_ELEMENT)
          .add(ErrorId.DUPLICATE_SCOPES)
          .add(ErrorId.ERROR_INJECTING_CONSTRUCTOR)
          .add(ErrorId.ERROR_INJECTING_METHOD)
          .add(ErrorId.ERROR_IN_CUSTOM_PROVIDER)
          .add(ErrorId.INJECT_INNER_CLASS)
          .add(ErrorId.MISSING_CONSTRUCTOR)
          .add(ErrorId.MISSING_IMPLEMENTATION)
          .add(ErrorId.NULL_INJECTED_INTO_NON_NULLABLE)
          .add(ErrorId.NULL_VALUE_IN_MAP)
          .add(ErrorId.SCOPE_NOT_FOUND)
          .add(ErrorId.TOO_MANY_CONSTRUCTORS)
          .build();

  private static final String DOC_BASE_URL = "https://github.com/google/guice/wiki/";

  protected final ErrorId errorId;

  protected InternalErrorDetail(
      ErrorId errorId, String message, List<Object> sources, Throwable cause) {
    super(message, sources, cause);
    this.errorId = errorId;
  }

  @Override
  protected final Optional<String> getLearnMoreLink() {
    if (DOCUMENTED_ERRORS.contains(errorId)) {
      return Optional.of(DOC_BASE_URL + errorId.name());
    }
    return Optional.empty();
  }

  @Override
  protected final Optional<String> getErrorIdentifier() {
    if (errorId == ErrorId.OTHER) {
      return Optional.empty();
    }
    String id = "Guice/" + CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, errorId.name());
    return Optional.of(id);
  }
}
