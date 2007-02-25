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

/**
 * A scope which bound objects can reside in. Scopes a given {@link Provider}.
 *
 * <p>Scope implementations should override {@code toString()} in the returned
 * provider and include the unscoped provider's {@code toString()} output. Doing
 * so aids debugging. They should also override their own {@code toString()}
 * method.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public interface Scope {

  /**
   * Scopes a provider. The returned locator returns objects from this scope. If
   * an object does not exist in this scope, the provider can use the given
   * unscoped provider to retrieve one.
   *
   * @param key binding key
   * @param unscoped locates an instance when one doesn't already exist in this
   *  scope.
   * @return a new provider which only delegates to the given unscoped provider
   *  when an instance of the requested object doesn't already exist in this
   *  scope
   */
  public <T> Provider<T> scope(Key<T> key, Provider<T> unscoped);
}
