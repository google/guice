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

import com.google.inject.Scope;
import com.google.inject.binder.ScopedBindingBuilder;

import java.lang.annotation.Annotation;


/**
 * Immutable snapshot of a binding scope.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public interface BindScoping {
  void execute(ScopedBindingBuilder scopedBindingBuilder);
  boolean isEagerSingleton();
  Scope getScope(Scope defaultValue);
  Class<? extends Annotation> getScopeAnnotation(Class<? extends Annotation> defaultValue);
}
