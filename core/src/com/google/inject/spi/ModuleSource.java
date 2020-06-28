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
import com.google.inject.Module;
import com.google.inject.internal.util.StackTraceElements;
import com.google.inject.internal.util.StackTraceElements.InMemoryStackTraceElement;
import java.util.List;

/**
 * Associated to a {@link Module module}, provides the module class name, the parent module {@link
 * ModuleSource source}, and the call stack that ends just before the module {@link
 * Module#configure(Binder) configure(Binder)} method invocation.
 */
final class ModuleSource {

  /** The class name of module that this {@link ModuleSource} associated to. */
  private final String moduleClassName;

  /** The parent {@link ModuleSource module source}. */
  private final ModuleSource parent;

  /**
   * Permit map created by the binder that installed this module.
   *
   * <p>The permit map is a binder-scoped object, but it's saved here because these maps have to
   * outlive the binders that created them in order to be used at injector creation, and there isn't
   * a 'BinderSource' object.
   */
  private final BindingSourceRestriction.PermitMap permitMap;

  /**
   * The chunk of call stack that starts from the parent module {@link Module#configure(Binder)
   * configure(Binder)} call and ends just before the module {@link Module#configure(Binder)
   * configure(Binder)} method invocation. For a module without a parent module the chunk starts
   * from the bottom of call stack. The array is non-empty if stack trace collection is on.
   */
  private final InMemoryStackTraceElement[] partialCallStack;

  /**
   * Creates a new {@link ModuleSource} with a {@literal null} parent.
   *
   * @param moduleClass the corresponding module
   * @param partialCallStack the chunk of call stack that starts from the parent module {@link
   *     Module#configure(Binder) configure(Binder)} call and ends just before the module {@link
   *     Module#configure(Binder) configure(Binder)} method invocation
   */
  ModuleSource(
      Class<?> moduleClass,
      StackTraceElement[] partialCallStack,
      BindingSourceRestriction.PermitMap permitMap) {
    this(null, moduleClass, partialCallStack, permitMap);
  }

  /**
   * Creates a new {@link ModuleSource} Object.
   *
   * @param parent the parent module {@link ModuleSource source}
   * @param moduleClass the corresponding module
   * @param partialCallStack the chunk of call stack that starts from the parent module {@link
   *     Module#configure(Binder) configure(Binder)} call and ends just before the module {@link
   *     Module#configure(Binder) configure(Binder)} method invocation
   */
  private ModuleSource(
      /* @Nullable */ ModuleSource parent,
      Class<?> moduleClass,
      StackTraceElement[] partialCallStack,
      BindingSourceRestriction.PermitMap permitMap) {
    Preconditions.checkNotNull(moduleClass, "module cannot be null.");
    Preconditions.checkNotNull(partialCallStack, "partialCallStack cannot be null.");
    this.parent = parent;
    this.moduleClassName = moduleClass.getName();
    this.partialCallStack = StackTraceElements.convertToInMemoryStackTraceElement(partialCallStack);
    this.permitMap = permitMap;
  }

  /**
   * Returns the corresponding module class name.
   *
   * @see Class#getName()
   */
  String getModuleClassName() {
    return moduleClassName;
  }

  /**
   * Returns the chunk of call stack that starts from the parent module {@link
   * Module#configure(Binder) configure(Binder)} call and ends just before the module {@link
   * Module#configure(Binder) configure(Binder)} method invocation. The return array is non-empty
   * only if stack trace collection is on.
   */
  StackTraceElement[] getPartialCallStack() {
    return StackTraceElements.convertToStackTraceElement(partialCallStack);
  }

  /** Returns the size of partial call stack if stack trace collection is on otherwise zero. */
  int getPartialCallStackSize() {
    return partialCallStack.length;
  }

  /**
   * Creates and returns a child {@link ModuleSource} corresponding to the {@link Module module}.
   *
   * @param moduleClass the corresponding module
   * @param partialCallStack the chunk of call stack that starts from the parent module {@link
   *     Module#configure(Binder) configure(Binder)} call and ends just before the module {@link
   *     Module#configure(Binder) configure(Binder)} method invocation
   */
  ModuleSource createChild(Class<?> moduleClass, StackTraceElement[] partialCallStack) {
    return new ModuleSource(this, moduleClass, partialCallStack, permitMap);
  }

  /** Returns the parent module {@link ModuleSource source}. */
  ModuleSource getParent() {
    return parent;
  }

  /**
   * Returns the class names of modules in this module source. The first element (index 0) is filled
   * by this object {@link #getModuleClassName()}. The second element is filled by the parent's
   * {@link #getModuleClassName()} and so on.
   */
  List<String> getModuleClassNames() {
    ImmutableList.Builder<String> classNames = ImmutableList.builder();
    ModuleSource current = this;
    while (current != null) {
      String className = current.moduleClassName;
      classNames.add(className);
      current = current.parent;
    }
    return classNames.build();
  }

  /**
   * Returns the size of {@link ModuleSource ModuleSources} chain (all parents) that ends at this
   * object.
   */
  int size() {
    if (parent == null) {
      return 1;
    }
    return parent.size() + 1;
  }

  /**
   * Returns the size of call stack that ends just before the module {@link Module#configure(Binder)
   * configure(Binder)} method invocation (see {@link #getStackTrace()}).
   */
  int getStackTraceSize() {
    if (parent == null) {
      return partialCallStack.length;
    }
    return parent.getStackTraceSize() + partialCallStack.length;
  }

  /**
   * Returns the full call stack that ends just before the module {@link Module#configure(Binder)
   * configure(Binder)} method invocation. The return array is non-empty if stack trace collection
   * on.
   */
  StackTraceElement[] getStackTrace() {
    int stackTraceSize = getStackTraceSize();
    StackTraceElement[] callStack = new StackTraceElement[stackTraceSize];
    int cursor = 0;
    ModuleSource current = this;
    while (current != null) {
      StackTraceElement[] chunk =
          StackTraceElements.convertToStackTraceElement(current.partialCallStack);
      int chunkSize = chunk.length;
      System.arraycopy(chunk, 0, callStack, cursor, chunkSize);
      current = current.parent;
      cursor = cursor + chunkSize;
    }
    return callStack;
  }

  /** Returns the permit map created by the binder that installed this module. */
  BindingSourceRestriction.PermitMap getPermitMap() {
    return permitMap;
  }
}
