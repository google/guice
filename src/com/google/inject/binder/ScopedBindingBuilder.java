/**
 * Copyright (C) 2006 Google Inc.
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

package com.google.inject.binder;

import com.google.inject.Scope;
import java.lang.annotation.Annotation;

/**
 * Specifies the scope for a binding.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public interface ScopedBindingBuilder {

  /**
   * Specifies the scope. References the annotation passed to {@link
   * com.google.inject.Binder#bindScope(Class, com.google.inject.Scope)}.
   */
  void in(Class<? extends Annotation> scopeAnnotation);

  /**
   * Specifies the scope.
   */
  void in(Scope scope);

  /**
   * Instructs the {@link com.google.inject.Injector} to eagerly initialize this
   * singleton-scoped binding upon creation. Useful for application
   * initialization logic.
   */
  void asEagerSingleton();
}
