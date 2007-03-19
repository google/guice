package com.google.inject;

/**
 * Thrown from {@link Provider#get} when an attempt is made to access a scoped
 * object while the scope in question is not currently active.
 *
 * @author kevinb@google.com (Kevin Bourrillion)
 */
public class OutOfScopeException extends RuntimeException {

  public OutOfScopeException(String message) {
    super(message);
  }

  public OutOfScopeException(String message, Throwable cause) {
    super(message, cause);
  }

  public OutOfScopeException(Throwable cause) {
    super(cause);
  }
}
