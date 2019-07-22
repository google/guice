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

import static com.google.common.truth.Truth.assert_;

import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.inject.Binding;
import com.google.inject.spi.ElementSource;
import java.lang.reflect.Method;
import java.util.Arrays;

// TODO(ronshapiro): consider exposing this in a Guice-Truth extension for SPI users?
final class BindingSubject extends Subject {
  private final Binding<?> actual;

  static BindingSubject assertThat(Binding<?> binding) {
    return assert_().<BindingSubject, Binding<?>>about(BindingSubject::new).that(binding);
  }

  private BindingSubject(FailureMetadata metadata, Binding<?> actual) {
    super(metadata, actual);
    this.actual = actual;
  }

  void hasSource(Class<?> moduleClass, String moduleMethod, Class<?>... moduleMethodArgs) {
    Method method;
    try {
      method = moduleClass.getDeclaredMethod(moduleMethod, moduleMethodArgs);
    } catch (NoSuchMethodException e) {
      throw new AssertionError(
          String.format(
              "Expected %s.%s(%s) to exist",
              moduleClass.getSimpleName(), moduleMethod, Arrays.toString(moduleMethodArgs)),
          e);
    }

    Object source = actual.getSource();
    if (source instanceof ElementSource) {
      source = ((ElementSource) source).getDeclaringSource();
    }

    if (!source.equals(method)) {
      failWithoutActual(Fact.fact("source", method));
    }
  }

  Subject hasProvidedValueThat() {
    return check("provided value").that(actual.getProvider().get());
  }
}
