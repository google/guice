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

import java.util.Arrays;
import java.util.logging.Logger;
/**
 * Contains flags for Guice.
 */
public class InternalFlags {
  private final static Logger logger = Logger.getLogger(InternalFlags.class.getName());
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


  public static IncludeStackTraceOption getIncludeStackTraceOption() {
    String flag = System.getProperty("guice_include_stack_traces");
    try {
      return (flag == null || flag.length() == 0)
          ? IncludeStackTraceOption.ONLY_FOR_DECLARING_SOURCE
          : IncludeStackTraceOption.valueOf(flag);
    } catch (IllegalArgumentException e) {
      logger.warning(flag
          + " is not a valid flag value for guice_include_stack_traces. "
          + " Values must be one of " + Arrays.asList(IncludeStackTraceOption.values()));
      return IncludeStackTraceOption.ONLY_FOR_DECLARING_SOURCE;
    }
  }
}
