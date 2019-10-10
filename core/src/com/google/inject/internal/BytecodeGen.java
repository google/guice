/*
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

package com.google.inject.internal;

import com.google.common.cache.CacheBuilder;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Utility methods for circular proxies, faster reflection, and method interception.
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 * @author jessewilson@google.com (Jesse Wilson)
 */
public final class BytecodeGen {

  private static final Map<Class<?>, Boolean> CIRCULAR_PROXY_TYPE_CACHE =
      CacheBuilder.newBuilder().weakKeys().<Class<?>, Boolean>build().asMap();

  /** Is the given object a circular proxy? */
  public static boolean isCircularProxy(Object object) {
    return CIRCULAR_PROXY_TYPE_CACHE.containsKey(object.getClass());
  }

  /** Creates a new circular proxy for the given type. */
  public static <T> T newCircularProxy(Class<T> type, InvocationHandler handler) {
    Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class[] {type}, handler);
    CIRCULAR_PROXY_TYPE_CACHE.put(proxy.getClass(), Boolean.TRUE);
    return type.cast(proxy);
  }

  /*if[AOP]*/

  /** Lists the methods in the given type that can be enhanced. */
  public static Method[] getEnhanceableMethods(Class<?> hostClass) {
    throw new UnsupportedOperationException();
  }

  /** Prepares the given type and methods for enhancement using bytecode generation. */
  public static Object prepareEnhancer(Class<?> hostClass, Method[] methods) {
    throw new UnsupportedOperationException();
  }

  /**
   * Creates an enhancer for the original unenhanced constructor using bytecode generation.
   *
   * @return enhancer that takes method callbacks (in the same order as the list of enhanceable
   *     methods returned by {@link #prepareEnhancer}) plus arguments for the original constructor
   *     and returns the enhanced instance
   */
  public static BiFunction<InvocationHandler[], Object[], Object> newEnhancer(
      Object enhancerGlue, Constructor<?> constructor) {
    throw new UnsupportedOperationException();
  }

  /**
   * Creates an invoker that calls the original unenhanced method using bytecode generation.
   *
   * @return invoker that takes an enhanced instance plus method arguments and returns the result
   */
  public static BiFunction<Object, Object[], Object> newSuperInvoker(
      Object enhancerGlue, Method method) {
    throw new UnsupportedOperationException();
  }

  /**
   * Creates a fast invoker for the given constructor using bytecode generation.
   *
   * @return invoker that takes constructor arguments and returns the constructed instance
   */
  public static Function<Object[], Object> newFastInvoker(Constructor<?> constructor) {
    throw new UnsupportedOperationException();
  }

  /**
   * Creates a fast invoker for the given method using bytecode generation.
   *
   * @return invoker that takes an instance to call plus method arguments and returns the result
   */
  public static BiFunction<Object, Object[], Object> newFastInvoker(Method method) {
    throw new UnsupportedOperationException();
  }

  /*end[AOP]*/
}
