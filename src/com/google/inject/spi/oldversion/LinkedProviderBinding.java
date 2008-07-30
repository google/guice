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

import com.google.inject.Provider;

/**
 * A binding which links to a provider binding which provides instances for
 * this binding.
 *
 * <p>Example: {@code bind(Foo.class).toProvider(FooProvider.class);}
 *
 * @deprecated replaced with {@link
 * com.google.inject.Binding.TargetVisitor#visitProviderKey(com.google.inject.Key)}
 *
 * @author crazybob@google.com (Bob Lee)
 */
@Deprecated
public interface LinkedProviderBinding<T> extends OldVersionBinding<T> {

  /**
   * Gets the binding for the provider of this binding.
   */
  OldVersionBinding<? extends Provider<? extends T>> getTargetProvider();
}
