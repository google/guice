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

import static com.google.inject.daggeradapter.Annotations.getAnnotatedAnnotation;

import com.google.inject.Key;
import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Optional;
import javax.inject.Qualifier;

/** Utility methods for creating {@link Key}s. */
final class Keys {
  static Key<?> parameterKey(Parameter parameter) {
    Optional<Annotation> qualifier = getAnnotatedAnnotation(parameter, Qualifier.class);
    Type type = parameter.getParameterizedType();
    return qualifier.isPresent() ? Key.get(type, qualifier.get()) : Key.get(type);
  }
}
