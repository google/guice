/*
 * Copyright (C) 2019 Google Inc.
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

package com.google.inject.internal.aop;

import com.google.inject.internal.BytecodeGen;
import com.google.inject.internal.InternalFlags;
import com.google.inject.internal.InternalFlags.CustomClassLoadingOption;
import java.util.logging.Logger;

/**
 * Utility methods for dealing with {@link ClassDefiner}s.
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
public final class ClassDefiners {
  private static final Logger logger = Logger.getLogger(ClassDefiners.class.getName());

  // initialization-on-demand...
  private static class ClassDefinerHolder {
    static final ClassDefiner INSTANCE = bindClassDefiner();
  }

  /** The preferred class definer. */
  public static ClassDefiner getClassDefiner() {
    return ClassDefinerHolder.INSTANCE;
  }

  /** The minimum visibility supported by the preferred class definer. */
  public static BytecodeGen.Visibility minimumVisibility() {
    return getClassDefiner() instanceof UnsafeClassDefiner
        ? BytecodeGen.Visibility.SAME_PACKAGE
        : BytecodeGen.Visibility.PUBLIC;
  }

  /** Binds the preferred {@link ClassDefiner} instance. */
  static ClassDefiner bindClassDefiner() {
    CustomClassLoadingOption loadingOption = InternalFlags.getCustomClassLoadingOption();
    if (loadingOption == CustomClassLoadingOption.CHILD) {
      return new ChildClassDefiner(); // override default choice
    } else if (UnsafeClassDefiner.isAccessible()) {
      return new UnsafeClassDefiner(); // preferred if available
    } else if (loadingOption != CustomClassLoadingOption.OFF) {
      return new ChildClassDefiner(); // second choice unless forbidden
    } else {
      logger.warning("Unsafe is not accessible and custom classloading is turned OFF");
      return (hostClass, bytecode) -> {
        throw new UnsupportedOperationException(
            "Cannot define class, custom classloading is turned OFF");
      };
    }
  }
}
