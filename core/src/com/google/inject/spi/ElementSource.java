/*
 * Copyright (C) 2013 Google Inc.
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

import com.google.common.base.Preconditions;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Contains information about where and how an {@link Element element} was bound.
 *
 * <p>The {@link #getDeclaringSource() declaring source} refers to a location in source code that
 * defines the Guice {@link Element element}. For example, if the element is created from a method
 * annotated by {@literal @Provides}, the declaring source of element would be the method itself.
 *
 * <p>The sequence of class names of {@link com.google.inject.Module modules} involved in the
 * element creation can be retrieved by {@link #getModuleClassNames()}. The order of the module
 * class names is reverse chronological. The first module (index 0) is the module that installs the
 * {@link Element element}. The last module is the root module.
 *
 * <p>In order to support the cases where a Guice {@link Element element} is created from another
 * Guice {@link Element element} (original) (e.g., by {@link Element#applyTo}), it also provides a
 * reference to the original element source ({@link #getOriginalElementSource()}).
 *
 * @since 4.0
 */
public final class ElementSource {

  /**
   * The {@link ElementSource source} of element that this element created from (if there is any),
   * otherwise {@code null}.
   */
  final ElementSource originalElementSource;

  /**
   * Wheather the originalElementSource was set externaly (untrusted) or by Guice internals
   * (trusted).
   *
   * <p>External code can set the originalElementSource to an arbitrary ElementSource via
   * Binder.withSource(ElementSource), thereby spoofing the element origin.
   */
  final boolean trustedOriginalElementSource;

  /** The {@link ModuleSource source} of module creates the element. */
  final ModuleSource moduleSource;

  /**
   * Refers to a single location in source code that causes the element creation. It can be any
   * object such as {@link Constructor}, {@link Method}, {@link Field}, {@link StackTraceElement},
   * etc. For example, if the element is created from a method annotated by {@literal @Provides},
   * the declaring source of element would be the method itself.
   */
  final Object declaringSource;

  /** The scanner that created this binding (if it was created by a scanner). */
  final ModuleAnnotatedMethodScanner scanner;

  /**
   * Creates a new {@ElementSource} from the given parameters.
   *
   * @param originalSource The source of element that this element was created from (if there is
   *     any), otherwise {@code null}.
   * @param declaringSource the source (in)directly declared the element.
   * @param moduleSource the moduleSource when the element is bound
   * @param partialCallStack the partial call stack from the top module to where the element is
   *     bound
   */
  ElementSource(
      @Nullable ElementSource originalSource,
      boolean trustedOriginalSource,
      Object declaringSource,
      ModuleSource moduleSource,
      ModuleAnnotatedMethodScanner scanner) {
    Preconditions.checkNotNull(declaringSource, "declaringSource cannot be null.");
    Preconditions.checkNotNull(moduleSource, "moduleSource cannot be null.");
    this.originalElementSource = originalSource;
    this.trustedOriginalElementSource = trustedOriginalSource;
    this.declaringSource = declaringSource;
    this.moduleSource = moduleSource;
    this.scanner = scanner;
  }

  /**
   * Returns the {@link ElementSource} of the element this was created or copied from. If this was
   * not created or copied from another element, returns {@code null}.
   */
  public ElementSource getOriginalElementSource() {
    return originalElementSource;
  }

  /**
   * Returns a single location in source code that defines the element. It can be any object such as
   * {@link java.lang.reflect.Constructor}, {@link java.lang.reflect.Method}, {@link
   * java.lang.reflect.Field}, {@link StackTraceElement}, etc. For example, if the element is
   * created from a method annotated by {@literal @Provides}, the declaring source of element would
   * be the method itself.
   */
  public Object getDeclaringSource() {
    return declaringSource;
  }

  /**
   * Returns the class names of modules involved in creating this {@link Element}. The first element
   * (index 0) is the class name of module that defined the element, and the last element is the
   * class name of root module.
   */
  public List<String> getModuleClassNames() {
    return moduleSource.getModuleClassNames();
  }

  /** Returns {@code getDeclaringSource().toString()} value. */
  @Override
  public String toString() {
    return getDeclaringSource().toString();
  }
}
