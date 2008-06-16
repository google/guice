/**
 * Copyright (C) 2008 Google Inc.
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


package com.google.inject.internal;

import com.google.inject.BindingAnnotation;
import com.google.inject.Key;
import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.lang.reflect.Type;

public class Keys {

  /**
   * Gets a key for the given type, member and annotations.
   */
  public static Key<?> get(Type type, Member member, Annotation[] annotations, Errors errors)
      throws ErrorsException {
    Annotation found = findBindingAnnotation(errors, member, annotations);
    errors.throwIfNecessary();
    return found == null ? Key.get(type) : Key.get(type, found);
  }

  /**
   * Returns the binding annotation on {@code member}, or null if there isn't one.
   */
  public static Annotation findBindingAnnotation(
      Errors errors, Member member, Annotation[] annotations) {
    Annotation found = null;

    for (Annotation annotation : annotations) {
      if (annotation.annotationType().isAnnotationPresent(BindingAnnotation.class)) {
        if (found != null) {
          errors.duplicateBindingAnnotations(member,
              found.annotationType(), annotation.annotationType());
        } else {
          found = annotation;
        }
      }
    }

    return found;
  }
}
