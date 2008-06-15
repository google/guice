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

package com.google.inject.spi;

import com.google.inject.Key;
import java.lang.reflect.Member;

/**
 * Represents a single dependency. Composed of where the dependency comes from
 * and how it's fulfilled. Each injectable field has a corresponding Dependency.
 * Each parameter in an injectable method or constructor has its own Dependency
 * instance.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public interface Dependency<T> {

  /**
   * Gets the key used to resolve this dependency. Equivalent to
   * {@code getBinding().getKey()}.
   */
  Key<T> getKey();

  /**
   * Gets the member (constructor, method, or field) which has the dependency.
   */
  Member getMember();

  /**
   * If the member is a constructor or method, you'll have one Dependency per
   * parameter, and this method returns the index of the parameter represented
   * by this Dependency. If the member is a field, this method returns
   * {@code -1}.
   */
  int getParameterIndex();

  /**
   * Returns true if the member accepts nulls, false otherwise.
   */
  boolean allowsNull();
}
