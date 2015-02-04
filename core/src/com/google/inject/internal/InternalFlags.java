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
  private static final Logger logger = Logger.getLogger(InternalFlags.class.getName());

  private static final IncludeStackTraceOption INCLUDE_STACK_TRACES
      = parseIncludeStackTraceOption();

  private static final CustomClassLoadingOption CUSTOM_CLASS_LOADING
      = parseCustomClassLoadingOption();

  private static final NullableProvidesOption NULLABLE_PROVIDES
      = parseNullableProvidesOption(NullableProvidesOption.ERROR);


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

  public enum NullableProvidesOption {
    /** Ignore null parameters to @Provides methods. */
    IGNORE,
    /** Warn if null parameters are passed to non-@Nullable parameters of provides methods. */
    WARN,
    /** Error if null parameters are passed to non-@Nullable parameters of provides parameters */
    ERROR
  }

  public static IncludeStackTraceOption getIncludeStackTraceOption() {
    return INCLUDE_STACK_TRACES;
  }

  public static CustomClassLoadingOption getCustomClassLoadingOption() {
    return CUSTOM_CLASS_LOADING;
  }

  public static NullableProvidesOption getNullableProvidesOption() {
    return NULLABLE_PROVIDES;
  }

  private static IncludeStackTraceOption parseIncludeStackTraceOption() {
    return getSystemOption("guice_include_stack_traces",
        IncludeStackTraceOption.ONLY_FOR_DECLARING_SOURCE);
  }

  private static CustomClassLoadingOption parseCustomClassLoadingOption() {
    return getSystemOption("guice_custom_class_loading",
        CustomClassLoadingOption.BRIDGE, CustomClassLoadingOption.OFF);
  }

  private static NullableProvidesOption parseNullableProvidesOption(
      NullableProvidesOption defaultValue) {
    return getSystemOption("guice_check_nullable_provides_params", defaultValue);
  }

  /**
   * Gets the system option indicated by the specified key; runs as a privileged action.
   *
   * @param name of the system option
   * @param defaultValue if the option is not set
   *
   * @return value of the option, defaultValue if not set
   */
  private static <T extends Enum<T>> T getSystemOption(final String name, T defaultValue) {
    return getSystemOption(name, defaultValue, defaultValue);
  }

  /**
   * Gets the system option indicated by the specified key; runs as a privileged action.
   *
   * @param name of the system option
   * @param defaultValue if the option is not set
   * @param secureValue if the security manager disallows access to the option
   *
   * @return value of the option, defaultValue if not set, secureValue if no access
   */
  private static <T extends Enum<T>> T getSystemOption(final String name, T defaultValue,
      T secureValue) {
    Class<T> enumType = defaultValue.getDeclaringClass();
    String value = null;
    try {
      value = AccessController.doPrivileged(new PrivilegedAction<String>() {
        public String run() {
          return System.getProperty(name);
        }
      });
      return (value != null && value.length() > 0) ? Enum.valueOf(enumType, value) : defaultValue;
    } catch (SecurityException e) {
      return secureValue;
    } catch (IllegalArgumentException e) {
      logger.warning(value + " is not a valid flag value for " + name + ". "
          + " Values must be one of " + Arrays.asList(enumType.getEnumConstants()));
      return defaultValue;
    }
  }
}
