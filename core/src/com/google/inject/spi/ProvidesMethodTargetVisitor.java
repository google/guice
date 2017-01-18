/*
 * Copyright (C) 2014 Google Inc.
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

import com.google.inject.Provides;

/**
 * A visitor for the {@literal @}{@link Provides} bindings.
 *
 * <p>If your {@link com.google.inject.spi.BindingTargetVisitor} implements this interface, bindings
 * created by using {@code @Provides} will be visited through this interface.
 *
 * @since 4.0
 * @author sameb@google.com (Sam Berlin)
 */
public interface ProvidesMethodTargetVisitor<T, V> extends BindingTargetVisitor<T, V> {

  /**
   * Visits an {@link ProvidesMethodBinding} created with an {@literal @}{@link Provides} method.
   */
  V visit(ProvidesMethodBinding<? extends T> providesMethodBinding);
}
