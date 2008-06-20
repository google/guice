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


package com.google.inject.commands;

/**
 * No-op visitor for subclassing. All interface methods simply delegate to
 * {@link #visitCommand(Command)}, returning its result.
 *
 * @author sberlin@gmail.com (Sam Berlin)
 */
public class DefaultCommandVisitor<V> implements Command.Visitor<V> {

  protected DefaultCommandVisitor() {}

  /**
   * Visit {@code command} and return a result.
   */
  public V visitCommand(Command command) {
    return null;
  }

  public V visitAddMessage(AddMessageCommand command) {
    return visitCommand(command);
  }

  public <T> V visitBind(BindCommand<T> command) {
    return visitCommand(command);
  }

  public V visitBindConstant(BindConstantCommand command) {
    return visitCommand(command);
  }

  public V visitBindInterceptor(BindInterceptorCommand command) {
    return visitCommand(command);
  }

  public V visitBindScope(BindScopeCommand command) {
    return visitCommand(command);
  }

  public V visitConvertToTypes(ConvertToTypesCommand command) {
    return visitCommand(command);
  }

  public <T> V visitGetProvider(GetProviderCommand<T> command) {
    return visitCommand(command);
  }

  public V visitRequestStaticInjection(
      RequestStaticInjectionCommand command) {
    return visitCommand(command);
  }
}
