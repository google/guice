/**
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

package com.google.inject.spi.oldversion;

import com.google.inject.spi.HasInjections;

/**
 * A binding to a concrete, injectable class. Instantiates new instances of the
 * class and injects its members.
 *
 * <p>Example: {@code bind(Concrete.class);}
 *
 * @deprecated replaced with {@link
 * com.google.inject.spi.BindingTargetVisitor#visitConstructor(java.lang.reflect.Constructor)}
 *
 * @author crazybob@google.com (Bob Lee)
 */
@Deprecated
public interface ClassBinding<T> extends OldVersionBinding<T>, HasInjections {

  /**
   * Gets the class associated with this binding.
   */
  Class<T> getBoundClass();

  // TODO: Expose information about method and constructor interceptors.
}
