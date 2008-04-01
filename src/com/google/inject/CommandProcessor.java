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

import com.google.inject.commands.*;

import java.util.Iterator;
import java.util.List;

/**
 * Abstract base class for executing commands to creating an injector.
 *
 * <p>Extending classes must return {@code true} from any overridden
 * {@code visit*()} methods, in order for the command processor to remove the
 * handled command.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
abstract class CommandProcessor implements Command.Visitor<Boolean> {

  private ErrorHandler errorHandler;

  public void processCommands(List<Command> commands, ErrorHandler errorHandler) {
    this.errorHandler = errorHandler;
    try {
      for (Iterator<Command> i = commands.iterator(); i.hasNext(); ) {
        Boolean allDone = i.next().acceptVisitor(this);
        if (allDone) {
          i.remove();
        }
      }
    } finally {
      this.errorHandler = null;
    }
  }

  protected void addError(Object source, String message, Object... arguments) {
    errorHandler.handle(source, message, arguments);
  }

  protected void addError(Object source, String message) {
    errorHandler.handle(source, message);
  }

  public Boolean visitAddMessageError(AddMessageErrorCommand command) {
    return false;
  }

  public Boolean visitAddError(AddThrowableErrorCommand command) {
    return false;
  }

  public Boolean visitBindInterceptor(BindInterceptorCommand command) {
    return false;
  }

  public Boolean visitBindScope(BindScopeCommand command) {
    return false;
  }

  public Boolean visitRequestStaticInjection(RequestStaticInjectionCommand command) {
    return false;
  }

  public Boolean visitBindConstant(BindConstantCommand command) {
    return false;
  }

  public Boolean visitConvertToTypes(ConvertToTypesCommand command) {
    return false;
  }

  public <T> Boolean visitBind(BindCommand<T> command) {
    return false;
  }

  public <T> Boolean visitGetProvider(GetProviderCommand<T> command) {
    return false;
  }

  public Boolean visitSubModule(SubModuleCommand command) {
    return false;
  }
}
