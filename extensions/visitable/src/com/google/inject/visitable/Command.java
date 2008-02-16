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

package com.google.inject.visitable;

/**
 * Immutable snapshot of a binding command.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public interface Command {
  <T> T acceptVisitor(Visitor<T> visitor);

  /**
   * Visit commands.
   */
  public interface Visitor<V> {
    V visitAddMessageError(AddMessageErrorCommand command);
    V visitAddError(AddThrowableErrorCommand command);
    V visitBindInterceptor(BindInterceptorCommand command);
    V visitBindScope(BindScopeCommand command);
    V visitRequestStaticInjection(RequestStaticInjectionCommand command);
    V visitBindConstant(BindConstantCommand command);
    V visitConvertToTypes(ConvertToTypesCommand command);
    <T> V visitBind(BindCommand<T> command);
    <T> V visitGetProvider(GetProviderCommand<T> command);
  }
}
