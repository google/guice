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

package com.google.inject.internal;

import com.google.inject.spi.Dependency;

/**
 * Resolves a single parameter, to be used in a constructor or method invocation.
 */
final class SingleParameterInjector<T> {
  private static final Object[] NO_ARGUMENTS = {}; 

  private final Dependency<T> dependency;
  private final Object source;
  private final InternalFactory<? extends T> factory;

  SingleParameterInjector(Dependency<T> dependency, BindingImpl<? extends T> binding) {
    this.dependency = dependency;
    this.source = binding.getSource();
    this.factory = binding.getInternalFactory();
  }

  T inject(Errors errors, InternalContext context) throws ErrorsException {
    Dependency<T> localDependency = dependency;
    Dependency previous = context.pushDependency(localDependency, source);
    try {
      return factory.get(errors.withSource(localDependency), context, localDependency, false);
    } finally {
      context.popStateAndSetDependency(previous);
    }
  }

  /**
   * Returns an array of parameter values.
   */
  static Object[] getAll(Errors errors, InternalContext context,
      SingleParameterInjector<?>[] parameterInjectors) throws ErrorsException {
    if (parameterInjectors == null) {
      return NO_ARGUMENTS;
    }

    int numErrorsBefore = errors.size();

    int size = parameterInjectors.length;
    Object[] parameters = new Object[size];

    // optimization: use manual for/each to save allocating an iterator here  
    for (int i = 0; i < size; i++) {
      SingleParameterInjector<?> parameterInjector = parameterInjectors[i];
      try {
        parameters[i] = parameterInjector.inject(errors, context);
      } catch (ErrorsException e) {
        errors.merge(e.getErrors());
      }
    }

    errors.throwIfNewErrors(numErrorsBefore);
    return parameters;
  }
}
