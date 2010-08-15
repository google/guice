// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.inject.persist.finder;

import java.lang.reflect.Method;

/**
 * Utility that helps you discover metadata about dynamic finder methods.
 *
 * @author dhanji@google.com (Dhanji R. Prasanna)
 */
public final class DynamicFinder {

  /**
   * Tests if {@code method} is a dynamic finder method.
   *
   * @param method a method you want to test as a dynamic finder
   * @return {@code true} if the method is annotated {@code @Finder}
   */
  public static boolean isFinder(Method method) {
    return method.isAnnotationPresent(Finder.class);
  }
}
