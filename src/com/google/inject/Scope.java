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
 * A scope which bound objects can reside in. Add a new scope using {@link
 * com.google.inject.ContainerBuilder#put(String, Scope)} and reference it from
 * bindings using its name.
 *
 * <p>Scope implementations should override {@code toString()} and include the
 * creator's {@code toString()} output. Doing so aids debugging.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public interface Scope {

  /**
   * Scopes a factory. The returned factory returns objects from this scope.
   * If an object does not exist in this scope, the factory can use the given
   * creator to create one.
   *
   * @param key binding key
   * @param creator creates new instances as needed
   * @return a new factory which only delegates to the given factory when an
   *  instance of the requested object doesn't already exist in the scope
   */
  public <T> Factory<T> scope(Key<T> key, Factory<T> creator);
}
