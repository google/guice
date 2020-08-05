/*
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

package com.google.inject.internal.util;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.util.List;

/**
 * Provides access to the calling line of code.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public final class SourceProvider {

  /** Indicates that the source is unknown. */
  public static final Object UNKNOWN_SOURCE = "[unknown source]";

  private final SourceProvider parent;
  private final ImmutableSet<String> classNamesToSkip;

  public static final SourceProvider DEFAULT_INSTANCE =
      new SourceProvider(ImmutableSet.of(SourceProvider.class.getName()));

  private SourceProvider(Iterable<String> classesToSkip) {
    this(null, classesToSkip);
  }

  private SourceProvider(SourceProvider parent, Iterable<String> classesToSkip) {
    this.parent = parent;

    ImmutableSet.Builder<String> classNamesToSkipBuilder = ImmutableSet.builder();
    for (String classToSkip : classesToSkip) {
      if (parent == null || !parent.shouldBeSkipped(classToSkip)) {
        classNamesToSkipBuilder.add(classToSkip);
      }
    }
    this.classNamesToSkip = classNamesToSkipBuilder.build();
  }

  /** Returns a new instance that also skips {@code moreClassesToSkip}. */
  public SourceProvider plusSkippedClasses(Class<?>... moreClassesToSkip) {
    return new SourceProvider(this, asStrings(moreClassesToSkip));
  }

  /** Returns true if the className should be skipped. */
  private boolean shouldBeSkipped(String className) {
    return (parent != null && parent.shouldBeSkipped(className))
        || classNamesToSkip.contains(className);
  }

  /** Returns the class names as Strings */
  private static List<String> asStrings(Class<?>... classes) {
    List<String> strings = Lists.newArrayList();
    for (Class<?> c : classes) {
      strings.add(c.getName());
    }
    return strings;
  }

  /**
   * Returns the calling line of code. The selected line is the nearest to the top of the stack that
   * is not skipped.
   */
  public StackTraceElement get(StackTraceElement[] stackTraceElements) {
    Preconditions.checkNotNull(stackTraceElements, "The stack trace elements cannot be null.");
    for (final StackTraceElement element : stackTraceElements) {
      String className = element.getClassName();

      if (!shouldBeSkipped(className)) {
        return element;
      }
    }
    throw new AssertionError();
  }

  /** Returns the non-skipped module class name. */
  public Object getFromClassNames(List<String> moduleClassNames) {
    Preconditions.checkNotNull(moduleClassNames, "The list of module class names cannot be null.");
    for (final String moduleClassName : moduleClassNames) {
      if (!shouldBeSkipped(moduleClassName)) {
        return new StackTraceElement(moduleClassName, "configure", null, -1);
      }
    }
    return UNKNOWN_SOURCE;
  }
}
