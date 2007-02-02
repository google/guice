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

import com.google.inject.spi.Message;

import java.util.Collection;
import java.util.Collections;

/**
 * Thrown when errors occurs while creating a {@link Container}. Includes a
 * list of encountered errors. Typically, a client should catch this exception,
 * log it, and stop execution.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class ContainerCreationException extends Exception {

  final Collection<Message> errorMessages;

  /**
   * Constructs a new exception for the given errors.
   */
  public ContainerCreationException(Collection<Message> errorMessages) {
    super(createErrorMessage(errorMessages));
    this.errorMessages = errorMessages;
  }

  private static String createErrorMessage(Collection<Message> errorMessages) {
    StringBuilder error = new StringBuilder();
    error.append("Guice configuration errors:\n\n");
    int index = 1;
    for (Message errorMessage : errorMessages) {
      error.append(index++)
          .append(") ")
          .append("Error at ")
          .append(errorMessage.getSource())
          .append(':')
          .append('\n')
          .append("  ")
          .append(errorMessage.getMessage())
          .append("\n\n");
    }
    error.append(errorMessages.size()).append(" error[s]\n");
    return error.toString();
  }

  /**
   * Gets the error messages which resulted in this exception.
   */
  public Collection<Message> getErrorMessages() {
    return Collections.unmodifiableCollection(errorMessages);
  }

  public synchronized Throwable fillInStackTrace() {
    // We don't care about this stack trace.
    return null;
  }
}
