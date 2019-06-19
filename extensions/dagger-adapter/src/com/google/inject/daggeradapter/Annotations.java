/*
 * Copyright (C) 2019 Google Inc.
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

package com.google.inject.daggeradapter;

import static com.google.common.collect.MoreCollectors.toOptional;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;
import java.util.Optional;

/** Extensions for {@link Annotation}. */
final class Annotations {
  static Optional<Annotation> getAnnotatedAnnotation(
      AnnotatedElement element, Class<? extends Annotation> annotationClass) {
    return Arrays.stream(element.getAnnotations())
        .filter(annotation -> annotation.annotationType().isAnnotationPresent(annotationClass))
        .collect(toOptional());
  }
}
