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

import static com.google.common.base.Preconditions.checkArgument;
/*if[AOP]*/
import static com.google.inject.internal.aop.ClassBuilding.buildFastClass;
import static com.google.inject.internal.aop.ClassBuilding.canEnhance;
import static com.google.inject.internal.aop.ClassBuilding.canFastInvoke;
import static com.google.inject.internal.aop.ClassBuilding.signature;

import com.google.inject.internal.aop.ClassBuilding;
/*end[AOP]*/
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.BitSet;
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

  /** Returns true if the given object is a circular proxy. */
  public static boolean isCircularProxy(Object object) {
    return object != null && CIRCULAR_PROXY_TYPE_CACHE.containsKey(object.getClass());
  }

  /** Creates a new circular proxy for the given type. */
  static <T> T newCircularProxy(Class<T> type, InvocationHandler handler) {
    Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class[] {type}, handler);
    CIRCULAR_PROXY_TYPE_CACHE.put(proxy.getClass(), Boolean.TRUE);
    return type.cast(proxy);
  }

  /*if[AOP]*/

  public static final String ENHANCER_BY_GUICE_MARKER = "$$EnhancerByGuice$$";

  public static final String FASTCLASS_BY_GUICE_MARKER = "$$FastClassByGuice$$";

  /** Builder of enhanced classes. */
  public interface EnhancerBuilder {
    /** Lists the methods in the host class that can be enhanced. */
    Method[] getEnhanceableMethods();

    /** Generates an enhancer for the selected subset of methods. */
    Function<String, BiFunction> buildEnhancer(BitSet methodIndices);
  }

  /** Create a builder of enhancers for the given class. */
  static EnhancerBuilder enhancerBuilder(Class<?> hostClass) {
    return ENHANCER_BUILDERS.getUnchecked(hostClass);
  }

  /**
   * Returns an invoker that constructs an enhanced instance. The invoker function accepts an array
   * of invocation handlers plus an array of arguments for the original constructor.
   */
  @SuppressWarnings("unchecked")
  static BiFunction<InvocationHandler[], Object[], Object> enhancedConstructor(
      Function<String, BiFunction> enhancer, Constructor<?> constructor) {
    checkArgument(canEnhance(constructor), "Constructor is not visible");
    return enhancer.apply(signature(constructor));
  }

  /**
   * Returns an invoker that calls the original unenhanced method. The invoker function accepts an
   * enhanced instance plus an array of arguments for the original method.
   */
  @SuppressWarnings("unchecked")
  static BiFunction<Object, Object[], Object> superMethod(
      Function<String, BiFunction> enhancer, Method method) {
    // no need to check 'canEnhance', ProxyFactory will only pick methods from enhanceable list
    return enhancer.apply(signature(method));
  }

  /**
   * Returns a fast invoker for the given constructor. The invoker function ignores the first
   * parameter and accepts an array of arguments for the constructor in the second parameter.
   *
   * Returns {@code null} if the constructor cannot be "fast-invoked" due to visibility issues.
   */
  @SuppressWarnings("unchecked")
  static BiFunction<Object, Object[], Object> fastConstructor(Constructor<?> constructor) {
    if (canFastInvoke(constructor)) {
      return fastClass(constructor).apply(signature(constructor));
    }
    return null;
  }

  /**
   * Returns a fast invoker for the given method. The invoker function accepts an instance, which
   * will be {@code null} for static methods, and an array of arguments for the method.
   *
   * Returns {@code null} if the method cannot be "fast-invoked" due to visibility issues.
   */
  @SuppressWarnings("unchecked")
  static BiFunction<Object, Object[], Object> fastMethod(Method method) {
    if (canFastInvoke(method)) {
      return fastClass(method).apply(signature(method));
    }
    return null;
  }

  /**
   * Prepares the class declaring the given member for fast invocation using bytecode generation.
   */
  private static Function<String, BiFunction> fastClass(Executable member) {
    return FAST_CLASSES.get(member.getDeclaringClass());
  }

  /**
   * Cache of recent {@link EnhancerBuilder}s.
   *
   * <p>Uses weak values so builders can be collected after they're done enhancing.
   */
  private static final LoadingCache<Class<?>, EnhancerBuilder> ENHANCER_BUILDERS =
      CacheBuilder.newBuilder()
          .weakKeys()
          .weakValues()
          .build(CacheLoader.from(ClassBuilding::buildEnhancerBuilder));

  /** Lazy association between classes and their generated fast-classes. */
  private static final ClassValue<Function<String, BiFunction>> FAST_CLASSES =
      new ClassValue<Function<String, BiFunction>>() {
        @Override
        protected Function<String, BiFunction> computeValue(Class<?> hostClass) {
          return buildFastClass(hostClass);
        }
      };

  /*end[AOP]*/
}
