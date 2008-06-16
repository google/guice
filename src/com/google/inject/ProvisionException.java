/**
 * Copyright (C) 2006 Google Inc.
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

package com.google.inject;

import com.google.inject.internal.Errors;
import com.google.inject.spi.Message;
import java.util.List;

/**
 * Indicates a failure to provide an instance.
 *
 * @author kevinb@google.com (Kevin Bourrillion)
 * @author jessewilson@google.com (Jesse Wilson)
 */
public final class ProvisionException extends RuntimeException {

  /** non-null for Guice-created ProvisionExceptions */
  private final Errors errors;

  /**
   * Creates a ProvisionException containing {@code errors}. As a side effect,
   * the errors are made immutable.
   */
  ProvisionException(Errors errors) {
    errors.makeImmutable();
    this.errors = errors;

    // find a cause
    List<Message> messages = errors.getMessages();
    for (Message message : messages) {
      if (message.getCause() != null) {
        initCause(message.getCause());
        break;
      }
    }
  }

  public ProvisionException(String message, Throwable cause) {
    super(message, cause);
    this.errors = null;
  }

  public ProvisionException(String message) {
    super(message);
    this.errors = null;
  }

  @Override public String getMessage() {
    return errors != null 
        ? Errors.format("Guice provision errors", errors.getMessages())
        : super.getMessage();
  }

  /**
   * Returns any {@code errors} on {@code userException} if it is a
   * ProvisionException.
   */
  static Errors getErrors(RuntimeException userException) {
    return userException instanceof ProvisionException
        ? ((ProvisionException) userException).errors
        : null;
  }

  /**
   * Throws a new provision exception if {@code errors} contains any error
   * messages.
   */
  static void throwNewIfNonEmpty(Errors errors) {
    if (errors.hasErrors()) {
      throw new ProvisionException(errors.makeImmutable());
    }
  }
}
