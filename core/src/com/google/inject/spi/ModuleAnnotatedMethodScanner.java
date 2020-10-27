/*
 * Copyright (C) 2015 Google Inc.
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

package com.google.inject.spi;

import com.google.inject.Binder;
import com.google.inject.Key;
import java.lang.annotation.Annotation;
import java.util.Set;

/**
 * Allows extensions to scan modules for annotated methods and bind those methods as providers,
 * similar to {@code @Provides} methods.
 *
 * @since 4.0
 */
public abstract class ModuleAnnotatedMethodScanner {

  /**
   * Returns the annotations this should scan for. Every method in the module that has one of these
   * annotations will create a Provider binding, with the return value of the binding being what's
   * provided and the parameters of the method being dependencies of the provider.
   */
  public abstract Set<? extends Class<? extends Annotation>> annotationClasses();

  /**
   * Prepares a method for binding. This {@code key} parameter is the key discovered from looking at
   * the binding annotation and return value of the method. Implementations can modify the key to
   * instead bind to another key. For example, Multibinder may want to change
   * {@code @ProvidesIntoSet String provideFoo()} to bind into a unique Key within the multibinder
   * instead of binding {@code String}.
   *
   * <p>The injection point and annotation are provided in case the implementation wants to set the
   * key based on the property of the annotation or if any additional preparation is needed for any
   * of the dependencies. The annotation is guaranteed to be an instance of one the classes returned
   * by {@link #annotationClasses}.
   *
   * <p>Returning null will cause Guice to skip this method, so that it is not bound to any key.
   *
   * <p>If {@code injectionPoint} represents an {@code abstract} method, {@code null} must be
   * returned from this method. This scanner can use {@code binder} to bind alternative bindings in
   * place of the abstract method.
   */
  public abstract <T> Key<T> prepareMethod(
      Binder binder, Annotation annotation, Key<T> key, InjectionPoint injectionPoint);
}
