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

import com.google.inject.Scope;

import java.lang.annotation.Annotation;

/**
 * Immutable snapshot of a request to bind a scope.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public final class BindScopeCommand implements Command {
  private final Class<? extends Annotation> annotationType;
  private final Scope scope;

  BindScopeCommand(
      Class<? extends Annotation> annotationType, Scope scope) {
    this.annotationType = annotationType;
    this.scope = scope;
  }

  public Class<? extends Annotation> getAnnotationType() {
    return annotationType;
  }

  public Scope getScope() {
    return scope;
  }

  public <T> T acceptVisitor(Visitor<T> visitor) {
    return visitor.visitBindScope(this);
  }
}
