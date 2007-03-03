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
 * A mapping from a key (type and optional annotation) to a provider of
 * instances of that type.  This interface is part of the {@link Injector}
 * introspection API and is intended primary for use by tools.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public interface Binding<T> {

  /**
   * Returns the key for this binding.
   */
  Key<T> getKey();

  /**
   * Returns an arbitrary object containing information about the "place"
   * where this binding was configured. Used by Guice in the production of
   * descriptive error messages.
   */
  Object getSource();

  /**
   * Returns the provider guice uses to fulfill requests for this binding.
   */
  Provider<T> getProvider();
}
