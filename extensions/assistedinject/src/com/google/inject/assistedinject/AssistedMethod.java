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

import com.google.inject.TypeLiteral;
import com.google.inject.spi.Dependency;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * Details about how a method in an assisted inject factory will be assisted.
 * 
 * @since 3.0
 * @author ramakrishna@google.com (Ramakrishna Rajanna)
 */
public interface AssistedMethod {
  
  /**
   * Returns the factory method that is being assisted.
   */
  Method getFactoryMethod();
  
  /**
   * Returns the implementation type that will be created when the method is
   * used.
   */
  TypeLiteral<?> getImplementationType();

  /**
   * Returns the constructor that will be used to construct instances of the 
   * implementation.
   */
  Constructor<?> getImplementationConstructor();
  
  /**
   * Returns all non-assisted dependencies required to construct and inject
   * the implementation.
   */
  Set<Dependency<?>> getDependencies();
}
