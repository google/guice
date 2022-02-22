package com.google.inject.internal.util;

import java.util.function.Predicate;

/**
 * An interface around finding the caller of the stack trace, so we can have different strategies
 * for implementing it.
 */
interface CallerFinder {
  StackTraceElement findCaller(Predicate<String> shouldBeSkipped);
}
