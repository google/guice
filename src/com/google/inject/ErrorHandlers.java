/**
 * Copyright (C) 2008 Google Inc.
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

import com.google.inject.internal.ErrorHandler;
import com.google.inject.internal.ErrorMessages;

/**
 * @author crazybob@google.com (Bob Lee)
 */
class ErrorHandlers {
  static ErrorHandler INVALID = new AbstractErrorHandler() {
    public void handle(Object source, String message) {
      throw new AssertionError(message);
    }
  };

  static ErrorHandler RUNTIME = new AbstractErrorHandler() {
    public void handle(Object source, String message) {
      throw new ConfigurationException("Error at " + source + " " + message);
    }
  };

  /**
   * Implements formatting. Converts known types to readable strings.
   */
  abstract static class AbstractErrorHandler implements ErrorHandler {
    public final void handle(Object source, String message, Object... arguments) {
      for (int i = 0; i < arguments.length; i++) {
        arguments[i] = ErrorMessages.convert(arguments[i]);
      }
      handle(source, String.format(message, arguments));
    }
  }
}
