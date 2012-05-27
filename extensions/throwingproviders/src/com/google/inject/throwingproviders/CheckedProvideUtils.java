// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.inject.throwingproviders;

import com.google.inject.Binder;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.Annotations;
import com.google.inject.internal.Errors;
import com.google.inject.spi.Message;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;

/**
 * Utilities for the throwing provider module.
 * 
 * @author sameb@google.com (Sam Berlin)
 */
class CheckedProvideUtils {
  
  private CheckedProvideUtils() {}
  
  private static final String CONSTRUCTOR_RULES =
      "Classes must have either one (and only one) constructor annotated with @ThrowingInject.";
  
  @SuppressWarnings("unchecked") // safe because it's a constructor of the typeLiteral
  static <T> Constructor<? extends T> findThrowingConstructor(
      TypeLiteral<? extends T> typeLiteral, Binder binder) {
    
    Class<?> rawType = typeLiteral.getRawType();
    Errors errors = new Errors(rawType);
    Constructor<?> cxtor = null;
    for (Constructor<?> constructor : rawType.getDeclaredConstructors()) {
      if (constructor.isAnnotationPresent(ThrowingInject.class)) {
        if (cxtor != null) {
          errors.addMessage("%s has more than one constructor annotated with @ThrowingInject. "
              + CONSTRUCTOR_RULES, rawType);
        }

        cxtor = constructor;
        Annotation misplacedBindingAnnotation = Annotations.findBindingAnnotation(
            errors, cxtor, ((AnnotatedElement) cxtor).getAnnotations());
        if (misplacedBindingAnnotation != null) {
          errors.misplacedBindingAnnotation(cxtor, misplacedBindingAnnotation);
        }
      }
    }
    
    if (cxtor == null) {
      errors.addMessage(
          "Could not find a suitable constructor in %s. " + CONSTRUCTOR_RULES, rawType);
    }

    for (Message msg : errors.getMessages()) {
      binder.addError(msg);
    }
    return (Constructor<? extends T>) cxtor;
  }
  
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
