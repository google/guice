/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.inject.util;

import com.google.inject.internal.BytecodeGen;
import java.util.Optional;

/**
 * Utilities for checking if classes are enhanced and/or getting the original un-enhanced class.
 *
 * @since 6.0
 */
public final class Enhanced {
  private Enhanced() {}

  /** Returns true if this is a class that Guice enhanced with AOP functionality. */
  public static boolean isEnhanced(Class<?> clazz) {
    return clazz.getSimpleName().contains(BytecodeGen.ENHANCER_BY_GUICE_MARKER);
  }

  /**
   * If the input class is a class that {@link #isEnhanced} is true for, returns the un-enhanced
   * version of the class. Otherwise returns an empty optional.
   */
  public static <T> Optional<Class<? super T>> unenhancedClass(Class<T> clazz) {
    return isEnhanced(clazz) ? Optional.of(clazz.getSuperclass()) : Optional.empty();
  }
}
