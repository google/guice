/**
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

import java.lang.reflect.Member;

/**
 * Context of the current injection.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public interface Context {

  /**
   * Gets the {@link Container}.
   */
  Container getContainer();

  /**
   * Gets the current scope strategy. See {@link
   * Container#setScopeStrategy(Scope.Strategy)}.
   *
   * @throws IllegalStateException if no strategy has been set
   */
  Scope.Strategy getScopeStrategy();

  /**
   * Gets the field, method or constructor which is being injected. Returns
   * {@code null} if the object currently being constructed is pre-loaded as
   * a singleton or requested from {@link Container#getInstance(Class)}.
   */
  Member getMember();

  /**
   * Gets the type of the field or parameter which is being injected.
   */
  Class<?> getType();

  /**
   * Gets the name of the injection specified by {@link Inject#value()}.
   */
  String getName();
}
