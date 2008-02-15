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

import com.google.inject.Binder;

/**
 * Executes recorded binding commands on a binder.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public abstract class ExecutingVisitor implements BinderVisitor<Void> {
  public abstract Binder binder();

  public Void visitAddMessageError(AddMessageErrorCommand command) {
    command.execute(binder());
    return null;
  }

  public Void visitAddError(AddThrowableErrorCommand command) {
    command.execute(binder());
    return null;
  }

  public Void visitBindInterceptor(BindInterceptorCommand command) {
    command.execute(binder());
    return null;
  }

  public Void visitBindScope(BindScopeCommand command) {
    command.execute(binder());
    return null;
  }

  public Void visitRequestStaticInjection(RequestStaticInjectionCommand command) {
    command.execute(binder());
    return null;
  }

  public Void visitConstantBinding(BindConstantCommand command) {
    command.execute(binder());
    return null;
  }

  public Void visitConvertToTypes(ConvertToTypesCommand command) {
    command.execute(binder());
    return null;
  }

  public <T> Void visitBinding(BindCommand<T> command) {
    command.execute(binder());
    return null;
  }

  public <T> Void visitGetProviderCommand(GetProviderCommand<T> command) {
    command.execute(binder());
    return null;
  }
}
