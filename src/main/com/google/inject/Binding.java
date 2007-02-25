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
 * A binding from a {@link Key} (type and name) to a provider.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public interface Binding<T> {

  /**
   * Gets the key for this binding.
   */
  Key<T> getKey();

  /**
   * Gets the source object, an arbitrary object which points back to the
   * configuration which resulted in this binding.
   */
  Object getSource();

  /**
   * Gets the provider which returns instances of {@code T}.
   */
  Provider<T> getProvider();
}
