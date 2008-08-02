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

package com.google.inject.spi.oldversion;

import com.google.inject.Provider;

/**
 * A synthetic binding to {@code Provider<T>} which exists for any binding to
 * {@code T}.
 *
 * @deprecated replaced with {@link 
 * com.google.inject.spi.BindTargetVisitor#visitProviderBinding(com.google.inject.Key)}
 *
 * @author crazybob@google.com (Bob Lee)
 */
@Deprecated
public interface ProviderBinding<T> extends OldVersionBinding<Provider<T>> {

  /**
   * Gets the binding from which the provider comes.
   */
  OldVersionBinding<T> getTargetBinding();
}
