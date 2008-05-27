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

import java.util.*;

/**
 * Thrown when errors occur while creating a {@link Injector}. Includes a list
 * of encountered errors. Typically, a client should catch this exception, log
 * it, and stop execution.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class CreationException extends RuntimeException {

  final List<? extends Message> errorMessages;

  /**
   * Constructs a new exception for the given errors.
   */
  public CreationException(Collection<? extends Message> errorMessages) {
    super();

    // Sort the messages by source. 
    this.errorMessages = new ArrayList<Message>(errorMessages);
    Collections.sort(this.errorMessages, new Comparator<Message>() {
      public int compare(Message a, Message b) {
        return a.getSource().compareTo(b.getSource());
      }
    });
  }

  public String getMessage() {
    return createErrorMessage(errorMessages);
  }

  private static String createErrorMessage(
      Collection<? extends Message> errorMessages) {
    Formatter fmt = new Formatter().format("Guice configuration errors:%n%n");
    int index = 1;
    for (Message errorMessage : errorMessages) {
      fmt.format("%s) Error at %s:%n", index++, errorMessage.getSource())
         .format(" %s%n%n", errorMessage.getMessage());
    }
    return fmt.format("%s error[s]", errorMessages.size()).toString();
  }

  /**
   * Gets the error messages which resulted in this exception.
   */
  public Collection<Message> getErrorMessages() {
    return Collections.unmodifiableCollection(errorMessages);
  }
}
