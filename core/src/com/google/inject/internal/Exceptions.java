/*
 * Copyright (C) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.inject.internal;

/**
 * Rethrows user-code exceptions in wrapped exceptions so that Errors can target the correct
 * exception.
 *
 * @author sameb@google.com (Sam Berlin)
 */
class Exceptions {

  /**
   * Rethrows the exception (or it's cause, if it has one) directly if possible. If it was a checked
   * exception, this wraps the exception in a stack trace with no frames, so that the exception is
   * shown immediately with no frames above it.
   */
  public static RuntimeException rethrowCause(Throwable throwable) {
    Throwable cause = throwable;
    if (cause.getCause() != null) {
      cause = cause.getCause();
    }
    return rethrow(cause);
  }

  /** Rethrows the exception. */
  public static RuntimeException rethrow(Throwable throwable) {
    if (throwable instanceof RuntimeException) {
      throw (RuntimeException) throwable;
    } else if (throwable instanceof Error) {
      throw (Error) throwable;
    } else {
      throw new UnhandledCheckedUserException(throwable);
    }
  }

  /**
   * A marker exception class that we look for in order to unwrap the exception into the user
   * exception, to provide a cleaner stack trace.
   */
  static class UnhandledCheckedUserException extends RuntimeException {
    public UnhandledCheckedUserException(Throwable cause) {
      super(cause);
    }
  }
}
