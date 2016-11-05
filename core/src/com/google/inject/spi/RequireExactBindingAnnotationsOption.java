/*
 * Copyright (C) 2010 Google Inc.
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

package com.google.inject.spi;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.inject.Binder;

/**
 * A request to require exact binding annotations.
 *
 * @author sameb@google.com (Sam Berlin)
 * @since 4.0
 */
public final class RequireExactBindingAnnotationsOption implements Element {
  private final Object source;

  RequireExactBindingAnnotationsOption(Object source) {
    this.source = checkNotNull(source, "source");
  }

  @Override
  public Object getSource() {
    return source;
  }

  @Override
  public void applyTo(Binder binder) {
    binder.withSource(getSource()).requireExactBindingAnnotations();
  }

  @Override
  public <T> T acceptVisitor(ElementVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
