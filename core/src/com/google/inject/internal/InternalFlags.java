/**
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

package com.google.inject.internal;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * Contains flags for Guice.
 */
public class InternalFlags {
  private final static Logger logger = Logger.getLogger(InternalFlags.class.getName());

  private static final IncludeStackTraceOption INCLUDE_STACK_TRACES
      = parseIncludeStackTraceOption();

  private static final CustomClassLoadingOption CUSTOM_CLASS_LOADING
      = parseCustomClassLoadingOption();

  /**
   * The options for Guice stack trace collection.
   */
  public enum IncludeStackTraceOption {
    /** No stack trace collection */
    OFF,
    /**  Minimum stack trace collection (Default) */
    ONLY_FOR_DECLARING_SOURCE,
    /** Full stack trace for everything */
    COMPLETE
  }

  /**
   * The options for Guice custom class loading.
   */
  public enum CustomClassLoadingOption {
    /** No custom class loading */
    OFF,
    /** Automatically bridge between class loaders (Default) */
    BRIDGE
  }

  public static IncludeStackTraceOption getIncludeStackTraceOption() {
    return INCLUDE_STACK_TRACES;
  }

  public static CustomClassLoadingOption getCustomClassLoadingOption() {
    return CUSTOM_CLASS_LOADING;
  }

  private static IncludeStackTraceOption parseIncludeStackTraceOption() {
    String flag = null;
    try {
      flag = getSystemFlag("guice_include_stack_traces");
      return (flag == null || flag.length() == 0)
          ? IncludeStackTraceOption.ONLY_FOR_DECLARING_SOURCE
          : IncludeStackTraceOption.valueOf(flag);
    } catch (SecurityException e) {
      return IncludeStackTraceOption.ONLY_FOR_DECLARING_SOURCE;
    } catch (IllegalArgumentException e) {
      logger.warning(flag
          + " is not a valid flag value for guice_include_stack_traces. "
          + " Values must be one of " + Arrays.asList(IncludeStackTraceOption.values()));
      return IncludeStackTraceOption.ONLY_FOR_DECLARING_SOURCE;
    }
  }

  private static CustomClassLoadingOption parseCustomClassLoadingOption() {
    String flag = null;
    try {
      flag = getSystemFlag("guice_custom_class_loading");
      return (flag == null || flag.length() == 0)
          ? CustomClassLoadingOption.BRIDGE
          : CustomClassLoadingOption.valueOf(flag);
    } catch (SecurityException e) {
      return CustomClassLoadingOption.OFF; // assume custom loading is also disallowed
    } catch (IllegalArgumentException e) {
      logger.warning(flag
          + " is not a valid flag value for guice_custom_class_loading. "
          + " Values must be one of " + Arrays.asList(CustomClassLoadingOption.values()));
      return CustomClassLoadingOption.BRIDGE;
    }
  }

  private static String getSystemFlag(final String name) throws SecurityException {
    return AccessController.doPrivileged(new PrivilegedAction<String>() {
      public String run() {
        return System.getProperty(name);
      }
    });
  }
}
