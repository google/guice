/**
 * Copyright (C) 2006 Google Inc.
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

package com.google.inject.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;

/**
 * Enables stand-in annotations. Keeps you from having to repeat the same
 * parameters over and over everywhere you use an annotation.
 *
 * <p>For example, if an annotation {@code A} annotates an annotation named
 * {@code Surrogate}, and {@code Surrogate} annotates a given element, {@code
 * findAnnotation(element, A.class)} will return the instance of {@code A}
 * from {@code Surrogate}.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class SurrogateAnnotations {

  /**
   * Finds an annotation of the given type on the given element. Looks for the
   * annotation directly on the element as well as on other annotations on the
   * element.
   *
   * @returns an instance of the annotation or {@code null} if none is found
   * @throws DuplicateAnnotationException if more than one annotation is found
   */
  public static <A extends Annotation> A findAnnotation(
      Class<A> annotationType, AnnotatedElement element)
      throws DuplicateAnnotationException {
    return findAnnotation(annotationType, element.getAnnotations());
  }

  /**
   * Finds an annotation of the given type in the given array of annotations.
   * Looks for the annotation in the array as well as on other annotations in
   * the array.
   *
   * @returns an instance of the annotation or {@code null} if none is found
   * @throws DuplicateAnnotationException if more than one annotation is found
   */
  public static <A extends Annotation> A findAnnotation(
      Class<A> annotationType, Annotation[] annotations)
      throws DuplicateAnnotationException {
    Annotation firstAnnotation = null;
    A firstFound = null;

    for (Annotation annotation : annotations) {
      A found = findAnnotation(annotationType, annotation);
      if (found != null) {
        if (firstFound == null) {
          firstAnnotation = annotation;
          firstFound = found;
        } else {
          throw new DuplicateAnnotationException(firstAnnotation, annotation);
        }
      }
    }

    return firstFound;
  }

  /**
   * Finds an annotation of the given type.
   *
   * @returns the given annotation if it is of the given type, an annotation
   *  of the given type on the given annotation's type, or {@code null} if an
   *  annotation isn't found.
   */
  static <A extends Annotation> A findAnnotation(Class<A> annotationType,
      Annotation annotation) {
    return annotation.annotationType() == annotationType
        ? annotationType.cast(annotation)
        : annotation.annotationType().getAnnotation(annotationType);
  }
}
