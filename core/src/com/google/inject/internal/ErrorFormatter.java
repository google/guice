package com.google.inject.internal;

import java.util.Formatter;
import java.util.List;

/** Helper for formatting Guice errors. */
final class ErrorFormatter {
  private ErrorFormatter() {}

  /**
   * Format a list of sources to the given {@code formatter}, prefixed by the give {@code index}.
   */
  static void formatSources(int index, List<Object> sources, Formatter formatter) {
    for (int i = 0; i < sources.size(); i++) {
      Object source = sources.get(i);
      if (i == 0) {
        formatter.format("%-3s: ", index);
      } else {
        formatter.format(SourceFormatter.INDENT);
      }
      new SourceFormatter(source, formatter, i == 0).format();
    }
  }

  /** Format a list of sources to the given {@code formatter}. */
  static void formatSources(List<Object> sources, Formatter formatter) {
    for (int i = 0; i < sources.size(); i++) {
      Object source = sources.get(i);
      formatter.format("  ");
      new SourceFormatter(source, formatter, i == 0).format();
    }
  }
}
