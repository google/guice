package com.google.inject.internal;


/**
 * Contains flags for Guice.
 */
public class InternalFlags {

  /**
   * The options for Guice stack trace collection.
   */
  public enum IncludeStackTraceOption {
    // No stack trace collection
    OFF,
    // Minimum stack trace collection (Default)
    ONLY_FOR_DECLARING_SOURCE,
    // Full stack trace for everything
    COMPLETE
  }


  public static IncludeStackTraceOption getIncludeStackTraceOption() {
    String propertyValue = System.getProperty(
        "guice_include_stack_traces");
    if (IncludeStackTraceOption.OFF.name().equals(propertyValue)) {
        return IncludeStackTraceOption.OFF;
    }
    if (IncludeStackTraceOption.COMPLETE.name().equals(propertyValue)) {
        return IncludeStackTraceOption.COMPLETE;
    }
    return IncludeStackTraceOption.ONLY_FOR_DECLARING_SOURCE;
  }
}
