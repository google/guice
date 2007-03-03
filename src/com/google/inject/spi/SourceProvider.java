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

package com.google.inject.spi;

/**
 * Provides source objects to the {@link com.google.inject.Binder}.
 * A source object is any object which points back to the current location
 * within the configuration. Guice uses source objects in error messages
 * and associates them with bindings.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public interface SourceProvider {

  /**
   * Creates an object pointing to the current location within the
   * configuration. If we run into a problem later, we'll be able to trace it
   * back to the original source. Useful for debugging.
   */
  Object source();
}
