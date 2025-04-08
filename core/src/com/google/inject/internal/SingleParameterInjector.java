/*
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

import com.google.errorprone.annotations.concurrent.LazyInit;
import com.google.inject.spi.Dependency;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

/** Resolves a single parameter, to be used in a constructor or method invocation. */
final class SingleParameterInjector<T> {
  private static final Object[] NO_ARGUMENTS = {};
  private static final MethodHandle[] NO_ARGUMENTS_HANDLES = {};

  private final Dependency<T> dependency;

  private final InternalFactory<? extends T> factory;
  @LazyInit private MethodHandle handle;

  SingleParameterInjector(Dependency<T> dependency, BindingImpl<? extends T> binding) {
    this.dependency = dependency;
    this.factory = binding.getInternalFactory();
  }

  T inject(InternalContext context) throws InternalProvisionException {
    Dependency<T> localDependency = dependency;
    try {
      return factory.get(context, localDependency, false);
    } catch (InternalProvisionException ipe) {
      throw ipe.addSource(localDependency);
    }
  }

  /**
   * Returns a method handle for the injection.
   *
   * <p>The returned handle has the type `(InternalContext)->Object`
   */
  MethodHandle getInjectHandle(LinkageContext context) {
    var handle = this.handle;
    if (handle == null) {
      handle =
          InternalMethodHandles.catchInternalProvisionExceptionAndRethrowWithSource(
              MethodHandles.insertArguments(
                  factory.getHandle(context, /* linked= */ false), 1, dependency),
              dependency);
      this.handle = handle;
    }
    return handle;
  }

  // TODO(lukes): inline into callers to decrease stack depth

  /** Returns an array of parameter values. */
  static Object[] getAll(InternalContext context, SingleParameterInjector<?>[] parameterInjectors)
      throws InternalProvisionException {
    if (parameterInjectors == null) {
      return NO_ARGUMENTS;
    }

    int size = parameterInjectors.length;
    Object[] parameters = new Object[size];

    // optimization: use manual for/each to save allocating an iterator here
    Dependency<?> dependency = null;
    try {
    for (int i = 0; i < size; i++) {
        SingleParameterInjector<?> injector = parameterInjectors[i];
        dependency = injector.dependency;
        parameters[i] = injector.factory.get(context, dependency, false);
      }
    } catch (InternalProvisionException ipe) {
      throw ipe.addSource(dependency);
    }
    return parameters;
  }

  /** Returns an array of handles for all parameters. */
  static MethodHandle[] getAllHandles(
      LinkageContext context, SingleParameterInjector<?>[] parameterInjectors) {
    if (parameterInjectors == null) {
      return NO_ARGUMENTS_HANDLES;
    }
    MethodHandle[] parameters = new MethodHandle[parameterInjectors.length];
    for (int i = 0; i < parameterInjectors.length; i++) {
      parameters[i] = parameterInjectors[i].getInjectHandle(context);
    }
    return parameters;
  }
}

