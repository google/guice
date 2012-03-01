// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.inject.throwingproviders;

import com.google.inject.Binder;
import com.google.inject.TypeLiteral;

/**
 * Utilities for the throwing provider module.
 * 
 * @author sameb@google.com (Sam Berlin)
 */
class CheckedProvideUtils {
  
  private CheckedProvideUtils() {}
  
  /** Adds errors to the binder if the exceptions aren't valid. */
  static void validateExceptions(Binder binder,
      Iterable<TypeLiteral<?>> actualExceptionTypes,
      Iterable<Class<? extends Throwable>> expectedExceptionTypes,
      Class<? extends CheckedProvider> checkedProvider) {
    // Validate the exceptions in the method match the exceptions
    // in the CheckedProvider.
    for (TypeLiteral<?> exType : actualExceptionTypes) {
      Class<?> exActual = exType.getRawType();
      // Ignore runtime exceptions & errors.
      if (RuntimeException.class.isAssignableFrom(exActual)
          || Error.class.isAssignableFrom(exActual)) {
        continue;
      }

      boolean notAssignable = true;
      for (Class<? extends Throwable> exExpected : expectedExceptionTypes) {
        if (exExpected.isAssignableFrom(exActual)) {
          notAssignable = false;
          break;
        }
      }
      if (notAssignable) {
        binder.addError(
            "%s is not compatible with the exceptions (%s) declared in " 
            + "the CheckedProvider interface (%s)",
            exActual, expectedExceptionTypes, checkedProvider);
      }
    }
  }

}
