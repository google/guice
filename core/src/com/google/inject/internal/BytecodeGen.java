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
import com.google.common.collect.MapMaker;
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
 * <p>This class makes heavy use of {@link Function} and {@link BiFunction} types when interacting
 * with generated fast-classes and enhanced proxies. This is a deliberate design decision to avoid
 * using Guice-specific types in the generated classes. This means generated classes can be defined
 * in the same {@link ClassLoader} as their host class without needing access to Guice's own {@link
 * ClassLoader}. (In other words it removes any need for bridge {@link ClassLoader}s.)
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 * @author jessewilson@google.com (Jesse Wilson)
 */
public final class BytecodeGen {

  private static final Map<Class<?>, Boolean> circularProxyTypeCache =
      new MapMaker().weakKeys().makeMap();

  /** Returns true if the given object is a circular proxy. */
  public static boolean isCircularProxy(Object object) {
    return object != null && circularProxyTypeCache.containsKey(object.getClass());
  }

  /** Creates a new circular proxy for the given type. */
  static <T> T newCircularProxy(Class<T> type, InvocationHandler handler) {
    Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    circularProxyTypeCache.put(proxy.getClass(), Boolean.TRUE);
    return type.cast(proxy);
  }

  /*if[AOP]*/

  public static final String ENHANCER_BY_GUICE_MARKER = "$$EnhancerByGuice$$";

  public static final String FASTCLASS_BY_GUICE_MARKER = "$$FastClassByGuice$$";

  /** Builder of enhanced classes. */
  public interface EnhancerBuilder {
    /**
     * Lists the methods in the host class that can be enhanced.
     *
     * <p>This always includes public and protected methods that are neither static nor final.
     *
     * <p>Package-private methods can only be enhanced if they're in the same package as the host
     * and we can define types in the same class loader with Unsafe. The {@link #finalize} method
     * can never be enhanced.
     */
    Method[] getEnhanceableMethods();

    /**
     * Generates an enhancer for the selected subset of methods.
     *
     * <p>The enhancer maps constructor and method signatures to invokers, where each invoker is
     * represented as a {@link BiFunction} that accepts a context object and an argument array.
     *
     * <p>Constructor invokers take an array of {@link InvocationHandler}s as their context object.
     * This is stored in the enhanced class before the original host class constructor is called,
     * with arguments unpacked from the argument array. The enhanced instance is then returned.
     *
     * <p>Method invokers take an enhanced instance as their context object and call the original
     * super-method with arguments unpacked from the argument array, ie. provides super-invocation.
     */
    Function<String, BiFunction<Object, Object[], Object>> buildEnhancer(BitSet methodIndices);
  }

  /** Create a builder of enhancers for the given class. */
  static EnhancerBuilder enhancerBuilder(Class<?> hostClass) {
    return ENHANCER_BUILDERS.getUnchecked(hostClass);
  }

  /**
   * Returns an invoker that constructs an enhanced instance. The invoker function accepts an array
   * of invocation handlers plus an array of arguments for the original constructor.
   */
  static BiFunction<Object, Object[], Object> enhancedConstructor(
      Function<String, BiFunction<Object, Object[], Object>> enhancer, Constructor<?> constructor) {
    checkArgument(canEnhance(constructor), "Constructor is not visible");
    return enhancer.apply(signature(constructor));
  }

  /**
   * Returns an invoker that calls the original unenhanced method. The invoker function accepts an
   * enhanced instance plus an array of arguments for the original method.
   */
  static BiFunction<Object, Object[], Object> superMethod(
      Function<String, BiFunction<Object, Object[], Object>> enhancer, Method method) {
    // no need to check 'canEnhance', ProxyFactory will only pick methods from enhanceable list
    return enhancer.apply(signature(method));
  }

  /**
   * Returns a fast invoker for the given constructor. The invoker function ignores the first
   * parameter and accepts an array of arguments for the constructor in the second parameter.
   *
   * <p>Returns {@code null} if the constructor cannot be "fast-invoked" due to visibility issues.
   */
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
   * <p>Returns {@code null} if the method cannot be "fast-invoked" due to visibility issues.
   */
  static BiFunction<Object, Object[], Object> fastMethod(Method method) {
    if (canFastInvoke(method)) {
      return fastClass(method).apply(signature(method));
    }
    return null;
  }

  /**
   * Prepares the class declaring the given member for fast invocation using bytecode generation.
   */
  private static Function<String, BiFunction<Object, Object[], Object>> fastClass(Executable member) {
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
  private static final ClassValue<Function<String, BiFunction<Object, Object[], Object>>> FAST_CLASSES =
      new ClassValue<Function<String, BiFunction<Object, Object[], Object>>>() {
        @Override
        protected Function<String, BiFunction<Object, Object[], Object>> computeValue(Class<?> hostClass) {
          return buildFastClass(hostClass);
        }
      };

  /*end[AOP]*/
}
