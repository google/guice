package com.google.inject.internal.util;

import java.util.function.Predicate;

/**
 * An interface around finding the caller of the stack trace, so we can have different strategies
 * for implementing it.
 */
interface CallerFinder {
  /**
   * A Tuple to hold the stack element alongside the class it originated from if StackWalker
   * captured it.
   */
  final class Caller {
    final StackTraceElement element;
    final Class<?> clazz;

    Caller(StackTraceElement element, Class<?> clazz) {
      this.element = element;
      this.clazz = clazz;
    }
  }

  Caller findCaller(Predicate<String> shouldBeSkipped);
}
