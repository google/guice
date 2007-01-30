// Copyright 2006 Google Inc. All Rights Reserved.

package com.google.inject;

/**
 * Handles errors in the container.
 *
 * @author crazybob@google.com (Bob Lee)
 */
interface ErrorHandler {

  /**
   * Handles an error.
   */
  void handle(String message);

  /**
   * Handles an error.
   */
  void handle(Throwable t);
}
