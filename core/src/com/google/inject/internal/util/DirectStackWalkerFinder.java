package com.google.inject.internal.util;

import java.util.function.Predicate;

/** A CallerFinder directly compiled against StackWalker. Requires compiling against jdk11+. */
final class DirectStackWalkerFinder implements CallerFinder {
  private static final StackWalker WALKER =
      StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

  @Override
  public StackTraceElement findCaller(Predicate<String> shouldBeSkipped) {
    return WALKER
        .walk(s -> s.skip(2).filter(f -> !shouldBeSkipped.test(f.getClassName())).findFirst())
        .map(StackWalker.StackFrame::toStackTraceElement)
        .orElseThrow(AssertionError::new);
  }
}
