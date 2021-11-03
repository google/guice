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
package com.google.inject.internal;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.logging.Logger;

/** Contains flags for Guice. */
public final class InternalFlags {
  private static final Logger logger = Logger.getLogger(InternalFlags.class.getName());

  private static final IncludeStackTraceOption INCLUDE_STACK_TRACES =
      getSystemOption(
          "guice_include_stack_traces",
          IncludeStackTraceOption.ONLY_FOR_DECLARING_SOURCE);

  private static final CustomClassLoadingOption CUSTOM_CLASS_LOADING =
      getSystemOption(
          "guice_custom_class_loading",
          CustomClassLoadingOption.BRIDGE,
          CustomClassLoadingOption.OFF);

  private static final NullableProvidesOption NULLABLE_PROVIDES =
      getSystemOption("guice_check_nullable_provides_params", NullableProvidesOption.ERROR);

  private static final BytecodeGenOption BYTECODE_GEN_OPTION =
      getSystemOption("guice_bytecode_gen_option", BytecodeGenOption.ENABLED);

  private static final ColorizeOption COLORIZE_OPTION =
      getSystemOption("guice_colorize_error_messages", ColorizeOption.OFF);

  /** The options for Guice stack trace collection. */
  public enum IncludeStackTraceOption {
    /** No stack trace collection */
    OFF,
    /**  Minimum stack trace collection (Default) */
    ONLY_FOR_DECLARING_SOURCE,
  }

  /** The options for Guice custom class loading. */
  public enum CustomClassLoadingOption {
    /**
     * Define fast/enhanced types in the same class loader as their original type, never creates
     * class loaders. Uses {@link sun.misc.Unsafe} to gain access to existing class loaders.
     */
    OFF,

    /**
     * Define fast/enhanced types anonymously as hidden nest-mates, never creates class loaders.
     * This is faster than regular class loading and the resulting classes are easier to unload.
     *
     * <p>Note: with this option you cannot look up fast/enhanced types by name or mock/spy them.
     */
    ANONYMOUS,

    /**
     * Attempt to define fast/enhanced types in the same class loader as their original type.
     * Otherwise creates a child class loader whose parent is the original class loader. (Default)
     */
    BRIDGE,

    /**
     * Define fast/enhanced types in a child class loader whose parent is the original class loader.
     *
     * <p>Note: with this option you cannot intercept package-private methods.
     */
    CHILD
  }

  /** Options for handling nullable parameters used in provides methods. */
  public enum NullableProvidesOption {
    /** Ignore null parameters to @Provides methods. */
    IGNORE,
    /** Warn if null parameters are passed to non-@Nullable parameters of provides methods. */
    WARN,
    /** Error if null parameters are passed to non-@Nullable parameters of provides parameters */
    ERROR
  }

  /**
   * Options for controlling whether Guice uses bytecode generation at runtime. When bytecode
   * generation is enabled, the following features will be enabled in Guice:
   *
   * <ul>
   *   <li>Runtime bytecode generation (instead of reflection) will be used when Guice need to
   *       invoke application code.
   *   <li>Method interception.
   * </ul>
   *
   * <p>Bytecode generation is generally faster than using reflection when invoking application
   * code, however, it can use more memory and slower in certain cases due to the time spent in
   * generating the classes. If you prefer to use reflection over bytecode generation then set
   * {@link BytecodeGenOption} to {@code DISABLED}.
   */
  public enum BytecodeGenOption {
    /**
     * Bytecode generation is disabled and using features that require it such as method
     * interception will throw errors at run time.
     */
    DISABLED,
    /** Bytecode generation is enabled. */
    ENABLED,
  }

  /** Options for enable or disable using ansi color in error messages. */
  public enum ColorizeOption {
    AUTO {
      @Override
      boolean enabled() {
        return System.console() != null && System.getenv("TERM") != null;
      }
    },
    ON {
      @Override
      boolean enabled() {
        return true;
      }
    },
    OFF {
      @Override
      boolean enabled() {
        return false;
      }
    };

    abstract boolean enabled();
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

  public static boolean isBytecodeGenEnabled() {
    return BYTECODE_GEN_OPTION == BytecodeGenOption.ENABLED;
  }

  public static boolean enableColorizeErrorMessages() {
    return COLORIZE_OPTION.enabled();
  }

  /**
   * Gets the system option indicated by the specified key; runs as a privileged action.
   *
   * @param name of the system option
   * @param defaultValue if the option is not set
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
   * @return value of the option, defaultValue if not set, secureValue if no access
   */
  private static <T extends Enum<T>> T getSystemOption(
      final String name, T defaultValue, T secureValue) {
    Class<T> enumType = defaultValue.getDeclaringClass();
    String value = null;
    try {
      value =
          AccessController.doPrivileged(
              new PrivilegedAction<String>() {
                @Override
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

  private InternalFlags() {}
}
