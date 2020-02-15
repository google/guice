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

import com.google.inject.internal.InternalFlags;
import com.google.inject.internal.InternalFlags.CustomClassLoadingOption;
import java.util.logging.Logger;

/**
 * Entry-point for defining dynamically generated classes.
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
public final class ClassDefining {
  private static final Logger logger = Logger.getLogger(ClassDefining.class.getName());

  private static final String CLASS_DEFINING_UNSUPPORTED =
      "Unsafe is not accessible and custom classloading is turned OFF.";

  // initialization-on-demand...
  private static class ClassDefinerHolder {
    static final ClassDefiner INSTANCE = bindClassDefiner();
    static final boolean HAS_PACKAGE_ACCESS = INSTANCE instanceof UnsafeClassDefiner;
  }

  /** Defines a new class relative to the host. */
  public static Class<?> define(Class<?> hostClass, byte[] bytecode) throws Exception {
    return ClassDefinerHolder.INSTANCE.define(hostClass, bytecode);
  }

  /** Does the definer have access to package-private members? */
  public static boolean hasPackageAccess() {
    return ClassDefinerHolder.HAS_PACKAGE_ACCESS;
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
      logger.warning(CLASS_DEFINING_UNSUPPORTED);
      return (hostClass, bytecode) -> {
        throw new UnsupportedOperationException(
            "Cannot define class, " + CLASS_DEFINING_UNSUPPORTED);
      };
    }
  }
}
