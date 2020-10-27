/*
 * Copyright (C) 2012 Google Inc.
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

package com.google.inject.throwingproviders;

import com.google.inject.Binder;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.Annotations;
import com.google.inject.internal.ErrorId;
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
          errors.addMessage(
              ErrorId.MISSING_CONSTRUCTOR,
              "%s has more than one constructor annotated with @ThrowingInject. "
                  + CONSTRUCTOR_RULES,
              rawType);
        }

        cxtor = constructor;
        Annotation misplacedBindingAnnotation =
            Annotations.findBindingAnnotation(
                errors, cxtor, ((AnnotatedElement) cxtor).getAnnotations());
        if (misplacedBindingAnnotation != null) {
          errors.misplacedBindingAnnotation(cxtor, misplacedBindingAnnotation);
        }
      }
    }

    if (cxtor == null) {
      errors.addMessage(
          ErrorId.MISSING_CONSTRUCTOR,
          "Could not find a suitable constructor in %s. " + CONSTRUCTOR_RULES,
          rawType);
    }

    for (Message msg : errors.getMessages()) {
      binder.addError(msg);
    }
    return (Constructor<? extends T>) cxtor;
  }

  /** Adds errors to the binder if the exceptions aren't valid. */
  @SuppressWarnings("rawtypes") // Class literal uses raw type.
  static void validateExceptions(
      Binder binder,
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
