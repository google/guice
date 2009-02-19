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
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * A binding to the constructor of a concrete clss. To resolve injections, an instance is
 * instantiated by invoking the constructor.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 * @since 2.0
 */
public interface ConstructorBinding<T> extends Binding<T>, HasDependencies {

  /**
   * Returns the {@link com.google.inject.Inject annotated} or default constructor that is invoked
   * for creating values.
   */
  Constructor<? extends T> getConstructor();

  /**
   * Returns the constructor, field and method injection points to create and populate a new
   * instance. The set contains exactly one constructor injection point.
   */
  Set<InjectionPoint> getInjectionPoints();

  /*if[AOP]*/
  /**
   * Returns the interceptors applied to each method, in the order that they will be applied.
   *
   * @return a possibly empty map
   */
  Map<Method, List<MethodInterceptor>> getMethodInterceptors();
  /*end[AOP]*/

}