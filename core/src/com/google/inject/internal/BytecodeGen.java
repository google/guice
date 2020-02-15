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

/*if[AOP]*/
import static com.google.common.base.Preconditions.checkState;
import static com.google.inject.internal.aop.ClassBuilding.canEnhance;
import static com.google.inject.internal.aop.ClassBuilding.canFastInvoke;
import static com.google.inject.internal.aop.ClassBuilding.signature;
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

  /** Is the given object a circular proxy? */
  public static boolean isCircularProxy(Object object) {
    return object != null && CIRCULAR_PROXY_TYPE_CACHE.containsKey(object.getClass());
  }

  /** Creates a new circular proxy for the given type. */
  public static <T> T newCircularProxy(Class<T> type, InvocationHandler handler) {
    Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class[] {type}, handler);
    CIRCULAR_PROXY_TYPE_CACHE.put(proxy.getClass(), Boolean.TRUE);
    return type.cast(proxy);
  }

  /*if[AOP]*/

  /** Builder of enhanced classes. */
  public interface EnhancerBuilder {
    String ENHANCER_BY_GUICE_MARKER = "$EnhancerByGuice$";

    /** Lists the methods in the host class that can be enhanced. */
    Method[] getEnhanceableMethods();

    /** Generates an enhancer for the selected subset of methods. */
    Function<String, ?> buildEnhancer(BitSet methodIndices);
  }

  /** Create a builder of enhancers for the given class. */
  public static EnhancerBuilder enhancerBuilder(Class<?> hostClass) {
    return ENHANCER_BUILDERS.getUnchecked(hostClass);
  }

  /**
   * Returns an invoker for the original unenhanced constructor that creates an enhanced instance.
   * The invoker function accepts an array of callbacks plus an array of arguments for the original
   * constructor.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static BiFunction<InvocationHandler[], Object[], Object> enhancedInvoker(
      Function<String, ?> enhancer, Constructor<?> constructor) {
    checkState(canEnhance(constructor), "Constructor is not visible");
    return (BiFunction) enhancer.apply(signature(constructor));
  }

  /**
   * Returns an invoker that calls the original unenhanced method. The invoker function accepts an
   * enhanced instance plus an array of arguments for the original method.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static BiFunction<Object, Object[], Object> superInvoker(
      Function<String, ?> enhancer, Method method) {
    // no need to check 'canEnhance', ProxyFactory will only pick methods from enhanceable list
    return (BiFunction) enhancer.apply(signature(method));
  }

  /**
   * Returns a fast invoker for the given constructor. The invoker function accepts an array of
   * arguments for the constructor and invokes it using bytecode generation.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Function<Object[], Object> fastInvoker(Constructor<?> constructor) {
    if (canFastInvoke(constructor)) {
      return (Function) fastClass(constructor).apply(signature(constructor));
    }
    return null;
  }

  /**
   * Returns a fast invoker for the given method. The invoker function accepts an instance, which
   * will be {@code null} for static methods, and an array of arguments for the method and invokes
   * it using bytecode generation.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static BiFunction<Object, Object[], Object> fastInvoker(Method method) {
    if (canFastInvoke(method)) {
      return (BiFunction) fastClass(method).apply(signature(method));
    }
    return null;
  }

  /**
   * Prepares the class containing the given member for fast invocation using bytecode generation.
   */
  private static Function<String, ?> fastClass(Executable member) {
    Class<?> hostClass = member.getDeclaringClass();
    if (hostClass.getSimpleName().contains(EnhancerBuilder.ENHANCER_BY_GUICE_MARKER)) {
      hostClass = hostClass.getSuperclass();
    }
    return FAST_CLASSES.getUnchecked(hostClass);
  }

  /** Weak cache of enhancer builders; values must be weak/soft as they refer back to keys. */
  private static final LoadingCache<Class<?>, EnhancerBuilder> ENHANCER_BUILDERS =
      CacheBuilder.newBuilder()
          .weakKeys()
          .softValues()
          .build(CacheLoader.from(com.google.inject.internal.aop.ClassBuilding::enhancerBuilder));

  /** Weak cache of fast-classes; values must be weak/soft as they refer back to keys. */
  private static final LoadingCache<Class<?>, Function<String, ?>> FAST_CLASSES =
      CacheBuilder.newBuilder()
          .weakKeys()
          .softValues()
          .build(CacheLoader.from(com.google.inject.internal.aop.ClassBuilding::buildFastClass));

  /*end[AOP]*/
}
