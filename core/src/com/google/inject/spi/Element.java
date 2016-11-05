/*
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

package com.google.inject.spi;

import com.google.inject.Binder;

/**
 * A core component of a module or injector.
 *
 * <p>The elements of a module can be inspected, validated and rewritten. Use {@link
 * Elements#getElements(com.google.inject.Module[]) Elements.getElements()} to read the elements
 * from a module, and {@link Elements#getModule(Iterable) Elements.getModule()} to rewrite them.
 * This can be used for static analysis and generation of Guice modules.
 *
 * <p>The elements of an injector can be inspected and exercised. Use {@link
 * com.google.inject.Injector#getBindings Injector.getBindings()} to reflect on Guice injectors.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 * @author crazybob@google.com (Bob Lee)
 * @since 2.0
 */
public interface Element {

  /**
   * Returns an arbitrary object containing information about the "place" where this element was
   * configured. Used by Guice in the production of descriptive error messages.
   *
   * <p>Tools might specially handle types they know about; {@code StackTraceElement} is a good
   * example. Tools should simply call {@code toString()} on the source object if the type is
   * unfamiliar.
   */
  Object getSource();

  /**
   * Accepts an element visitor. Invokes the visitor method specific to this element's type.
   *
   * @param visitor to call back on
   */
  <T> T acceptVisitor(ElementVisitor<T> visitor);

  /**
   * Writes this module element to the given binder (optional operation).
   *
   * @param binder to apply configuration element to
   * @throws UnsupportedOperationException if the {@code applyTo} method is not supported by this
   *     element.
   */
  void applyTo(Binder binder);
}
