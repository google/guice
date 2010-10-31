/**
 * Copyright (C) 2010 Google Inc.
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

package com.google.inject.assistedinject;

import com.google.inject.Key;

import java.util.Collection;

/**
 * A binding for a factory created by FactoryModuleBuilder.
 * 
 * @param <T> The fully qualified type of the factory.
 *  
 * @since 3.0
 * @author ramakrishna@google.com (Ramakrishna Rajanna)
 */
public interface AssistedInjectBinding<T> {
  
  /** Returns the {@link Key} for the factory binding. */
  Key<T> getKey();

  /** Returns an {@link AssistedMethod} for each method in the factory. */
  Collection<AssistedMethod> getAssistedMethods();
}
