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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ExecutionException;
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

  /**
   * The required visibility of a user's class from a Guice-generated class. Visibility of
   * package-private members depends on the loading classloader: only if two classes were loaded by
   * the same classloader can they see each other's package-private members. We need to be careful
   * when choosing which classloader to use for generated classes.
   */
  public enum Visibility {

    /**
     * Indicates Guice-generated classes only call or override public members of the target class.
     * They may be loaded by a different classloader to the target class.
     */
    PUBLIC {
      @Override
      public Visibility and(Visibility that) {
        return that;
      }
    },

    /**
     * Indicates Guice-generated classes call or override at least one package-private member. They
     * must be loaded in the same classloader as the target class.
     */
    SAME_PACKAGE {
      @Override
      public Visibility and(Visibility that) {
        return this;
      }
    };

    public static Visibility forMember(Executable member) {
      if ((member.getModifiers() & (Modifier.PROTECTED | Modifier.PUBLIC)) == 0) {
        return SAME_PACKAGE;
      }

      if (member instanceof Method && forType(((Method) member).getReturnType()) == SAME_PACKAGE) {
        return SAME_PACKAGE;
      }

      for (Class<?> type : member.getParameterTypes()) {
        if (forType(type) == SAME_PACKAGE) {
          return SAME_PACKAGE;
        }
      }

      return PUBLIC;
    }

    public static Visibility forType(Class<?> type) {
      return (type.getModifiers() & (Modifier.PROTECTED | Modifier.PUBLIC)) == 0
          ? SAME_PACKAGE
          : PUBLIC;
    }

    public abstract Visibility and(Visibility that);
  }

  /** Describes a class that can be enhanced with new behaviour. */
  public interface EnhancerTarget {
    /** The class to be enhanced. */
    Class<?> getHostClass();

    /** Lists the methods in the host class that can be enhanced. */
    Method[] getEnhanceableMethods();
  }

  /** Collects details describing the class about to be enhanced. */
  public static EnhancerTarget enhancerTarget(Class<?> hostClass) {
    return new com.google.inject.internal.aop.MethodResolver(hostClass).buildEnhancerTarget();
  }

  /** Prepares the given class and methods for enhancement using bytecode generation. */
  public static Object prepareEnhancer(EnhancerTarget target, Visibility visibility) {
    try {
      return ENHANCER_GLUE.get(target.getHostClass(), () -> newEnhancerGlue(target, visibility));
    } catch (ExecutionException e) {
      throw new UncheckedExecutionException(e.getCause());
    }
  }

  /**
   * Creates an enhancer for the original unenhanced constructor using bytecode generation.
   *
   * @return enhancer that takes method callbacks (in the same order as the list of enhanceable
   *     methods returned by {@link #enhancerTarget}) plus arguments for the original constructor
   *     and returns the enhanced instance
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static BiFunction<InvocationHandler[], Object[], Object> newEnhancer(
      Object enhancerGlue, Constructor<?> constructor) {
    return ((Function<String, BiFunction>) enhancerGlue).apply(signature(constructor));
  }

  /**
   * Creates an invoker that calls the original unenhanced method using bytecode generation.
   *
   * @return invoker that takes an enhanced instance plus method arguments and returns the result
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static BiFunction<Object, Object[], Object> newSuperInvoker(
      Object enhancerGlue, Method method) {
    return ((Function<String, BiFunction>) enhancerGlue).apply(signature(method));
  }

  /**
   * Creates a fast invoker for the given constructor using bytecode generation.
   *
   * @return invoker that takes constructor arguments and returns the constructed instance
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Function<Object[], Object> newFastInvoker(Constructor<?> constructor) {
    return (Function) prepareFastClass(constructor).apply(signature(constructor));
  }

  /**
   * Creates a fast invoker for the given method using bytecode generation.
   *
   * @return invoker that takes an instance to call plus method arguments and returns the result
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static BiFunction<Object, Object[], Object> newFastInvoker(Method method) {
    return (BiFunction) prepareFastClass(method).apply(signature(method));
  }

  /** Prepares the given executable member for fast invocation using bytecode generation. */
  private static Function<String, ?> prepareFastClass(Executable member) {
    return FAST_CLASS_GLUE.getUnchecked(member.getDeclaringClass());
  }

  /** Minimum signature needed to disambiguate constructors from the same host class. */
  private static String signature(Constructor<?> constructor) {
    return Arrays.toString(constructor.getParameterTypes());
  }

  /** Minimum signature needed to disambiguate methods from the same host class. */
  private static String signature(Method method) {
    return method.getName() + Arrays.toString(method.getParameterTypes());
  }

  /**
   * Weak cache of enhancer glue; both keys and values must be weak as values refer back to keys.
   */
  private static final Cache<Class<?>, Function<String, ?>> ENHANCER_GLUE =
      CacheBuilder.newBuilder().weakKeys().weakValues().build();

  /**
   * Weak cache of fast-class glue; both keys and values must be weak as values refer back to keys.
   */
  private static final LoadingCache<Class<?>, Function<String, ?>> FAST_CLASS_GLUE =
      CacheBuilder.newBuilder()
          .weakKeys()
          .weakValues()
          .build(CacheLoader.from(BytecodeGen::newFastClassGlue));

  /** Generate glue that maps signatures to various enhancer invokers. */
  private static Function<String, ?> newEnhancerGlue(EnhancerTarget target, Visibility visibility) {
    throw new UnsupportedOperationException();
  }

  /** Generate glue that maps signatures to various fast-class invokers. */
  private static Function<String, ?> newFastClassGlue(Class<?> hostClass) {
    throw new UnsupportedOperationException();
  }

  /*end[AOP]*/
}
