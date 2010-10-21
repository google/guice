/**
 * Copyright (C) 2008 Google Inc.
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

import com.google.inject.Binding;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A binding to the constructor of a concrete clss. To resolve injections, an instance is
 * instantiated by invoking the constructor.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 * @since 2.0
 */
public interface ConstructorBinding<T> extends Binding<T>, HasDependencies {

  /**
   * Gets the constructor this binding injects.
   */
  InjectionPoint getConstructor();

  /**
   * Returns all instance method and field injection points on {@code type}.
   *
   * @return a possibly empty set of injection points. The set has a specified iteration order. All
   *      fields are returned and then all methods. Within the fields, supertype fields are returned
   *      before subtype fields. Similarly, supertype methods are returned before subtype methods.
   */
  Set<InjectionPoint> getInjectableMembers();

  /*if[AOP]*/
  /**
   * Returns the interceptors applied to each method, in the order that they will be applied.
   *
   * @return a possibly empty map
   */
  Map<Method, List<org.aopalliance.intercept.MethodInterceptor>> getMethodInterceptors();
  /*end[AOP]*/
}