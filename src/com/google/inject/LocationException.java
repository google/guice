// Copyright 2007 Google Inc. All Rights Reserved.

package com.google.inject;

import com.google.inject.util.StackTraceElements;
import java.lang.reflect.Member;

/**
 * Used to rethrow exceptions that occur while locating instances, to add
 * additional contextual details.
 */
class LocationException extends RuntimeException {
  public LocationException(ExternalContext<?> externalContext,
      Throwable cause) {
    super(createMessage(externalContext), cause);
  }

  private static String createMessage(ExternalContext<?> externalContext) {
    Key<?> key = externalContext.getKey();
    Member member = externalContext.getMember();
    return String.format(ErrorMessages.EXCEPTION_WHILE_CREATING,
        ErrorMessages.convert(key),
        StackTraceElements.forMember(member));
  }
}
