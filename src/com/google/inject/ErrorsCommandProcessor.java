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
import com.google.inject.internal.Errors;

/**
 * Handles {@link Binder#addError} commands.
 *
 * @author crazybob@google.com (Bob Lee)
 * @author jessewilson@google.com (Jesse Wilson)
 */
class ErrorsCommandProcessor extends CommandProcessor {

  ErrorsCommandProcessor(Errors errors) {
    super(errors);
  }

  @Override public Boolean visitAddMessageError(AddMessageErrorCommand command) {
    errors.userReportedError(command.getMessage(), command.getArguments());
    return true;
  }

  @Override public Boolean visitAddError(AddThrowableErrorCommand command) {
    errors.exceptionReportedByModuleSeeLogs(command.getThrowable());
    return true;
  }
}
