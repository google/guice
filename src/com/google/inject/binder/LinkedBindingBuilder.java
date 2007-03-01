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

package com.google.inject.binder;

import com.google.inject.Key;
import com.google.inject.TypeLiteral;

/**
 * Javadoc.
 *
 */
public interface LinkedBindingBuilder<T> {

  /**
   * Links to another binding with the given key.
   */
  void to(Key<? extends T> destination);

  /**
   * Links to another binding with the given type.
   */
  void to(Class<? extends T> destination);

  /**
   * Links to another binding with the given type.
   */
  void to(TypeLiteral<? extends T> destination);
}
