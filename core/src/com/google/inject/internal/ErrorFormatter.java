package com.google.inject.internal;

import java.util.Formatter;
import java.util.List;

/** Helper for formatting Guice errors. */
final class ErrorFormatter {
  private ErrorFormatter() {}

  static void formatSources(int index, List<Object> sources, Formatter formatter) {
    for (int i = 0; i < sources.size(); i++) {
      Object source = sources.get(i);
      if (i == 0) {
        formatter.format("%-3s: ", index);
      } else {
        formatter.format("%s", SourceFormatter.INDENT);
      }
      new SourceFormatter(source, formatter, i == 0).format();
    }
  }
}
