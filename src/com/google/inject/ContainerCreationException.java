// Copyright 2006 Google Inc. All Rights Reserved.

package com.google.inject;

/**
 * Thrown when errors occurs while creating a {@link Container}. Includes a
 * list of encountered errors.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class ContainerCreationException extends Exception {

  public ContainerCreationException(String message) {
    super(message);
  }

  public synchronized Throwable fillInStackTrace() {
    // We don't care about this stack trace.
    return null;
  }
}
