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

import com.google.inject.commands.BindScopeCommand;
import com.google.inject.internal.Annotations;
import static com.google.inject.internal.Objects.nonNull;
import com.google.inject.internal.StackTraceElements;
import com.google.inject.internal.ErrorMessages;

import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * Handles {@link Binder#bindScope} commands.
 *
 * @author crazybob@google.com (Bob Lee)
 * @author jessewilson@google.com (Jesse Wilson)
 */
class ScopesCommandProcessor extends CommandProcessor {

  private final Map<Class<? extends Annotation>, Scope> scopes;

  ScopesCommandProcessor(Map<Class<? extends Annotation>, Scope> scopes) {
    this.scopes = scopes;
  }

  @Override public Boolean visitBindScope(BindScopeCommand command) {
    Scope scope = command.getScope();
    Class<? extends Annotation> annotationType = command.getAnnotationType();

    if (!Scopes.isScopeAnnotation(annotationType)) {
      addError(StackTraceElements.forType(annotationType),
          ErrorMessages.MISSING_SCOPE_ANNOTATION);
      // Go ahead and bind anyway so we don't get collateral errors.
    }

    if (!Annotations.isRetainedAtRuntime(annotationType)) {
      addError(StackTraceElements.forType(annotationType),
          ErrorMessages.MISSING_RUNTIME_RETENTION, command.getSource());
      // Go ahead and bind anyway so we don't get collateral errors.
    }

    Scope existing = scopes.get(nonNull(annotationType, "annotation type"));
    if (existing != null) {
      addError(command.getSource(), ErrorMessages.DUPLICATE_SCOPES, existing,
          annotationType, scope);
    } else {
      scopes.put(annotationType, nonNull(scope, "scope"));
    }

    return true;
  }
}