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

package com.google.inject.spi.oldversion;

import com.google.inject.Binding;
import com.google.inject.Scope;

/**
 * A mapping from a key (type and optional annotation) to a provider of
 * instances of that type.  This interface is part of the {@link com.google.inject.Injector}
 * introspection API and is intended primary for use by tools.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public interface OldVersionBinding<T> extends Binding<T> {

  /**
   * Gets the synthetic binding to this binding's Provider.
   */
  ProviderBinding<T> getProviderBinding();

  /**
   * Returns the scope applied by this binding.
   */
  Scope getScope();

  /**
   * Accepts a binding visitor. Invokes the visitor method specific to this
   * binding's type.
   *
   * @param visitor to call back on
   */
  void accept(BindingVisitor<? super T> visitor);
}
