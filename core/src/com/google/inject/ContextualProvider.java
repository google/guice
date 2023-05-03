/*
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

package com.google.inject;

import com.google.inject.spi.InjectionContext;

/**
 * An object capable of providing instances of type {@code T} based on context
 * obtained from an {@link InjectionContext}.
 *
 * @param <T> the type of object this provides
 */
public interface ContextualProvider<T> {
  /**
   * Provides an instance of {@code T}.
   *
   * @param context the {@code InjectionContext} where a value is to be provided for
   * @throws OutOfScopeException when an attempt is made to access a scoped object while the scope
   *     in question is not currently active
   * @throws ProvisionException if an instance cannot be provided. Such exceptions include messages
   *     and throwables to describe why provision failed.
   */
  T get(InjectionContext context);
}
