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

import com.google.inject.spi.Message;

import java.util.Collection;
import java.util.ArrayList;

/**
 * A stateful error handler that can be used at both configuration time and
 * at runtime. By using the same error handler in both situations, a reference
 * to this error handler will work in both situations.
 *
 * @author crazybob@google.com (Bob Lee)
 * @author jessewilson@google.com (Jesse Wilson)
 */
class DefaultErrorHandler extends ErrorHandlers.AbstractErrorHandler {
  State state = State.CONFIGURATION_TIME;
  final Collection<Message> errorMessages = new ArrayList<Message>();

  public void handle(Object source, String message) {
    if (state == State.RUNTIME) {
      throw new ConfigurationException("Error at " + source + " " + message);

    } else if (state == State.CONFIGURATION_TIME) {
      errorMessages.add(new Message(source, message));

    } else {
      throw new AssertionError();
    }
  }

  void switchToRuntime() {
    state = State.RUNTIME;
    errorMessages.clear();
  }

  void blowUpIfErrorsExist() {
    if (!errorMessages.isEmpty()) {
      throw new CreationException(errorMessages);
    }
  }

  enum State {
    CONFIGURATION_TIME, RUNTIME
  }
}
