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
import com.google.common.collect.ImmutableList;
import com.google.inject.internal.util.StackTraceElements;
import com.google.inject.internal.util.StackTraceElements.InMemoryStackTraceElement;
import java.util.List;

/**
 * Contains information about where and how an {@link Element element} was bound.
 *
 * <p>The {@link #getDeclaringSource() declaring source} refers to a location in source code that
 * defines the Guice {@link Element element}. For example, if the element is created from a method
 * annotated by {@literal @Provides}, the declaring source of element would be the method itself.
 *
 * <p>The {@link #getStackTrace()} refers to the sequence of calls ends at one of {@link
 * com.google.inject.Binder} {@code bindXXX()} methods and eventually defines the element. Note that
 * {@link #getStackTrace()} lists {@link StackTraceElement StackTraceElements} in reverse
 * chronological order. The first element (index zero) is the last method call and the last element
 * is the first method invocation. By default, the stack trace is not collected. The default
 * behavior can be changed by setting the {@code guice_include_stack_traces} flag value. The value
 * can be either {@code OFF}, {@code ONLY_FOR_DECLARING_SOURCE} or {@code COMPLETE}. Note that
 * collecting stack traces for every binding can cause a performance hit when the injector is
 * created.
 *
 * <p>The sequence of class names of {@link com.google.inject.Module modules} involved in the
 * element creation can be retrieved by {@link #getModuleClassNames()}. Similar to {@link
 * #getStackTrace()}, the order is reverse chronological. The first module (index 0) is the module
 * that installs the {@link Element element}. The last module is the root module.
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
   * The partial call stack that starts at the last module {@link Module#Configure(Binder)
   * configure(Binder)} call. The value is empty if stack trace collection is off.
   */
  final InMemoryStackTraceElement[] partialCallStack;

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
   * @param originalElementSource The source of element that this element created from (if there is
   *     any), otherwise {@code null}.
   * @param declaringSource the source (in)directly declared the element.
   * @param moduleSource the moduleSource when the element is bound
   * @param partialCallStack the partial call stack from the top module to where the element is
   *     bound
   */
  ElementSource(
      /* @Nullable */ ElementSource originalSource,
      boolean trustedOriginalSource,
      Object declaringSource,
      ModuleSource moduleSource,
      StackTraceElement[] partialCallStack,
      ModuleAnnotatedMethodScanner scanner) {
    Preconditions.checkNotNull(declaringSource, "declaringSource cannot be null.");
    Preconditions.checkNotNull(moduleSource, "moduleSource cannot be null.");
    Preconditions.checkNotNull(partialCallStack, "partialCallStack cannot be null.");
    this.originalElementSource = originalSource;
    this.trustedOriginalElementSource = trustedOriginalSource;
    this.declaringSource = declaringSource;
    this.moduleSource = moduleSource;
    this.partialCallStack = StackTraceElements.convertToInMemoryStackTraceElement(partialCallStack);
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

  /**
   * Returns the position of {@link com.google.inject.Module#configure configure(Binder)} method
   * call in the {@link #getStackTrace stack trace} for modules that their classes returned by
   * {@link #getModuleClassNames}. For example, if the stack trace looks like the following:
   *
   * <ol>
   *   <li>{@code Binder.bind()}
   *   <li>{@code ModuleTwo.configure()}
   *   <li>{@code Binder.install()}
   *   <li>{@code ModuleOne.configure()}
   *   <li>{@code theRest().
   * </ol>
   *
   * <p>1 and 3 are returned.
   *
   * <p>In the cases where stack trace is not available (i.e., the stack trace was not collected),
   * it returns -1 for all module positions.
   */
  public List<Integer> getModuleConfigurePositionsInStackTrace() {
    int size = moduleSource.size();
    Integer[] positions = new Integer[size];
    int chunkSize = partialCallStack.length;
    positions[0] = chunkSize - 1;
    ModuleSource current = moduleSource;
    for (int cursor = 1; cursor < size; cursor++) {
      chunkSize = current.getPartialCallStackSize();
      positions[cursor] = positions[cursor - 1] + chunkSize;
      current = current.getParent();
    }
    return ImmutableList.<Integer>copyOf(positions);
  }

  /**
   * Returns the sequence of method calls that ends at one of {@link com.google.inject.Binder}
   * {@code bindXXX()} methods and eventually defines the element. Note that {@link #getStackTrace}
   * lists {@link StackTraceElement StackTraceElements} in reverse chronological order. The first
   * element (index zero) is the last method call and the last element is the first method
   * invocation. In the cases where stack trace is not available (i.e.,the stack trace was not
   * collected), it returns an empty array.
   */
  public StackTraceElement[] getStackTrace() {
    int modulesCallStackSize = moduleSource.getStackTraceSize();
    int chunkSize = partialCallStack.length;
    int size = moduleSource.getStackTraceSize() + chunkSize;
    StackTraceElement[] callStack = new StackTraceElement[size];
    System.arraycopy(
        StackTraceElements.convertToStackTraceElement(partialCallStack),
        0,
        callStack,
        0,
        chunkSize);
    System.arraycopy(moduleSource.getStackTrace(), 0, callStack, chunkSize, modulesCallStackSize);
    return callStack;
  }

  /** Returns {@code getDeclaringSource().toString()} value. */
  @Override
  public String toString() {
    return getDeclaringSource().toString();
  }
}
