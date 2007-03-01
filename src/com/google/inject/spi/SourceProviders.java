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

package com.google.inject.spi;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Provides access to the default {@link SourceProvider} implementation and
 * common controls for certain implementations.
 * 
 * @author crazybob@google.com (Bob Lee)
 */
public class SourceProviders {

  private SourceProviders() {}

  public static final Object UNKNOWN_SOURCE = "[unknown source]";
  
  static final SourceProvider DEFAULT_INSTANCE = new StacktraceSourceProvider();

  static Set<String> skippedClassNames = Collections.emptySet();

  static {
    skip(SourceProviders.class);
    skip(StacktraceSourceProvider.class);
  }

  /**
   * Instructs stacktrace-based providers to skip the given class in the stack
   * trace when determining the source. Use this to keep the binder from
   * logging utility methods as the sources of bindings (i.e. it will skip to
   * the utility methods' callers instead).
   *
   * <p>Skipping only takes place after this method is called.
   */
  public synchronized static void skip(Class<?> clazz) {
    Set<String> copy = new HashSet<String>();
    copy.addAll(skippedClassNames);
    copy.add(clazz.getName());
    skippedClassNames = Collections.unmodifiableSet(copy);
  }

  /**
   * Gets the set of class names which should be skipped by stacktrace-based
   * providers.
   */
  public synchronized static Set<String> getSkippedClassNames() {
    return skippedClassNames;
  }

  static ThreadLocal<SourceProvider[]> localSourceProvider =
      new ThreadLocal<SourceProvider[]>() {
    protected SourceProvider[] initialValue() {
      return new SourceProvider[] { DEFAULT_INSTANCE };
    }
  };

  /**
   * Returns the current source obtained from the default provider.
   */
  public static Object defaultSource() {
    return localSourceProvider.get()[0].source();
  }

  /**
   * Sets the default source provider, runs the given command, and then
   * restores the previous default source provider.
   */
  public static void withDefault(
      SourceProvider sourceProvider, Runnable r) {
    // We use a holder so we perform only 1 thread local access instead of 3.
    SourceProvider[] holder = localSourceProvider.get();
    SourceProvider previous = holder[0];
    try {
      holder[0] = sourceProvider;
      r.run();
    } finally {
      holder[0] = previous;
    }
  }

  static class StacktraceSourceProvider implements SourceProvider {
    public Object source() {
      // Search up the stack until we find a class outside of this one.
      Set<String> skippedClassNames = getSkippedClassNames();
      for (final StackTraceElement element : new Throwable().getStackTrace()) {
        String className = element.getClassName();
        if (!skippedClassNames.contains(className)) {
          return element;
        }
      }
      throw new AssertionError();
    }
  }
}
