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


package com.google.inject.multibindings;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.inject.BindingAnnotation;

import java.lang.annotation.Retention;

/**
 * An internal binding annotation applied to each element in a multibinding.
 * Breaks the Java annotation rules for equals and hashCode, instead defining
 * equality based on whether the associated bindings match. This allows
 * different modules to contribute multibindings independently, while still
 * supporting SPI-based module manipulations like
 * {@link com.google.inject.util.Modules#override Modules.override}.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
@Retention(RUNTIME) @BindingAnnotation
@interface Element {

  enum Type {
    MAPBINDER,
    MULTIBINDER,
    OPTIONALBINDER;
  }

  String setName();
  int uniqueId();
  Type type();
}
