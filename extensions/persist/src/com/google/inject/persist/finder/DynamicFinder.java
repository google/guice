/**
 * Copyright (C) 2010 Google, Inc.
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

package com.google.inject.persist.finder;

import java.lang.reflect.Method;

/**
 * Utility that helps you introspect dynamic finder methods.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public final class DynamicFinder {
  private final Method method;
  private final Finder finder;

  public DynamicFinder(Method method) {
    this.method = method;
    this.finder = method.getAnnotation(Finder.class);
  }

  /**
   * Returns some metadata if the method is annotated {@code @Finder} or null.
   *
   * @param method a method you want to test as a dynamic finder
   */
  public static DynamicFinder from(Method method) {
    return method.isAnnotationPresent(Finder.class) ? new DynamicFinder(method) : null;
  }

  public Finder metadata() {
    return finder;
  }
}
