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

/**
 * Thrown when errors occurs while creating a {@link Container}. Includes a
 * list of encountered errors. Typically, a client should catch this exception,
 * log it, and stop execution.
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
