/*
 * Copyright (C) 2007 Google Inc.
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

package com.google.inject;

import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.spi.SourceProviders;

/**
 * Links one binding to another.
 */
class LinkedBindingBuilderImpl<T> implements LinkedBindingBuilder<T> {

  final Key<T> key;
  Key<? extends T> destination;
  Object source = SourceProviders.UNKNOWN_SOURCE;
  private BinderImpl binder;

  LinkedBindingBuilderImpl(BinderImpl binder, Key<T> key) {
    this.binder = binder;
    this.key = key;
  }

  Object getSource() {
    return source;
  }

  Key<T> getKey() {
    return key;
  }

  Key<? extends T> getDestination() {
    return destination;
  }

  LinkedBindingBuilderImpl<T> from(Object source) {
    this.source = source;
    return this;
  }

  public void to(Key<? extends T> destination) {
    if (this.destination != null) {
      binder.addError(source, ErrorMessages.LINK_DESTINATION_ALREADY_SET);
    } else {
      this.destination = destination;
    }
  }

  public void to(Class<? extends T> destination) {
    to(Key.get(destination));
  }

  public void to(TypeLiteral<? extends T> destination) {
    to(Key.get(destination));
  }
}
