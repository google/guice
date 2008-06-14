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

import com.google.inject.commands.AddMessageErrorCommand;
import com.google.inject.commands.AddThrowableErrorCommand;
import com.google.inject.internal.ErrorHandler;
import com.google.inject.internal.ErrorMessage;
import com.google.inject.spi.Message;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles {@link Binder#addError} commands.
 *
 * @author crazybob@google.com (Bob Lee)
 * @author jessewilson@google.com (Jesse Wilson)
 */
class ErrorsCommandProcessor extends CommandProcessor {

  private static final Logger logger
      = Logger.getLogger(ErrorsCommandProcessor.class.getName());

  ErrorsCommandProcessor(ErrorHandler errorHandler) {
    super(errorHandler);
  }

  @Override public Boolean visitAddMessageError(AddMessageErrorCommand command) {
    String message = String.format(command.getMessage(), command.getArguments());
    errorHandler.handle(new Message(command.getSource(), message));
    return true;
  }

  @Override public Boolean visitAddError(AddThrowableErrorCommand command) {
    Object source = command.getSource();
    String message = ErrorMessage.getRootMessage(command.getThrowable());
    String logMessage = ErrorMessage.exceptionReportedByModules(message).toString();
    logger.log(Level.INFO, logMessage, command.getThrowable());
    addError(source, ErrorMessage.exceptionReportedByModuleSeeLogs(message));
    return true;
  }
}
