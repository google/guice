package com.google.inject.internal.util;

import java.util.function.Predicate;

/** A CallerFinder that construcst a new Throwable and iterates through its stack trace. */
class NewThrowableFinder implements CallerFinder {
  @Override
  public StackTraceElement findCaller(Predicate<String> shouldBeSkipped) {
    StackTraceElement[] stackTraceElements = new Throwable().getStackTrace();
    for (StackTraceElement element : stackTraceElements) {
      String className = element.getClassName();
      if (!shouldBeSkipped.test(className)) {
        return element;
      }
    }
    throw new AssertionError();
  }
}
