/*
 * Copyright (C) 2009 Google Inc.
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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.inject.spi.InjectionPoint;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.BitSet;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * Builds a construction proxy that can participate in AOP. This class manages applying type and
 * method matchers to come up with the set of intercepted methods.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
final class ProxyFactory<T> implements ConstructionProxyFactory<T> {

  private static final Logger logger = Logger.getLogger(ProxyFactory.class.getName());

  private final InjectionPoint injectionPoint;

  private final Function<String, BiFunction<Object, Object[], Object>> enhancer;
  private final ImmutableMap<Method, List<MethodInterceptor>> interceptors;
  private final InvocationHandler[] callbacks;

  ProxyFactory(InjectionPoint injectionPoint, Iterable<MethodAspect> methodAspects)
      throws ErrorsException {
    this.injectionPoint = injectionPoint;

    Class<?> hostClass = injectionPoint.getMember().getDeclaringClass();

    // Find applicable aspects. Bow out if none are applicable to this class.
    List<MethodAspect> applicableAspects = Lists.newArrayList();
    for (MethodAspect methodAspect : methodAspects) {
      if (methodAspect.matches(hostClass)) {
        applicableAspects.add(methodAspect);
      }
    }

    if (applicableAspects.isEmpty()) {
      enhancer = null;
      interceptors = ImmutableMap.of();
      callbacks = null;
      return;
    }

    BytecodeGen.EnhancerBuilder enhancerBuilder = BytecodeGen.enhancerBuilder(hostClass);

    Method[] methods = enhancerBuilder.getEnhanceableMethods();
    int numMethods = methods.length;

    Multimap<Method, MethodInterceptor> matchedInterceptors = ArrayListMultimap.create();
    BitSet matchedMethodIndices = new BitSet();

    // Iterate over aspects and add interceptors for the methods they apply to
    for (MethodAspect methodAspect : applicableAspects) {
      for (int methodIndex = 0; methodIndex < numMethods; methodIndex++) {
        Method method = methods[methodIndex];
        if (methodAspect.matches(method)) {
          if (method.isSynthetic()) {
            logger.log(
                Level.WARNING,
                "Method [{0}] is synthetic and is being intercepted by {1}."
                    + " This could indicate a bug.  The method may be intercepted twice,"
                    + " or may not be intercepted at all.",
                new Object[] {method, methodAspect.interceptors()});
          }

          matchedInterceptors.putAll(method, methodAspect.interceptors());
          matchedMethodIndices.set(methodIndex);
        }
      }
    }

    if (matchedMethodIndices.isEmpty()) {
      enhancer = null;
      interceptors = ImmutableMap.of();
      callbacks = null;
      return;
    }

    try {
      enhancer = enhancerBuilder.buildEnhancer(matchedMethodIndices);
    } catch (Throwable e) {
      throw new Errors().errorEnhancingClass(hostClass, e).toException();
    }

    callbacks = new InvocationHandler[matchedMethodIndices.cardinality()];

    ImmutableMap.Builder<Method, List<MethodInterceptor>> interceptorsMapBuilder =
        ImmutableMap.builder();

    int callbackIndex = 0;
    for (int methodIndex = matchedMethodIndices.nextSetBit(0);
        methodIndex >= 0;
        methodIndex = matchedMethodIndices.nextSetBit(methodIndex + 1)) {

      Method method = methods[methodIndex];
      List<MethodInterceptor> deDuplicated =
          ImmutableSet.copyOf(matchedInterceptors.get(method)).asList();
      interceptorsMapBuilder.put(method, deDuplicated);

      BiFunction<Object, Object[], Object> superInvoker = BytecodeGen.superMethod(enhancer, method);
      callbacks[callbackIndex++] = new InterceptorStackCallback(method, deDuplicated, superInvoker);
    }

    interceptors = interceptorsMapBuilder.build();
  }

  /** Returns the interceptors that apply to the constructed type. */
  public ImmutableMap<Method, List<MethodInterceptor>> getInterceptors() {
    return interceptors;
  }

  @Override
  public ConstructionProxy<T> create() throws ErrorsException {
    if (interceptors.isEmpty()) {
      return new DefaultConstructionProxyFactory<T>(injectionPoint).create();
    }

    // Create the proxied class. We're careful to ensure that interceptor state is not-specific
    // to this injector. Otherwise, the proxies for each injector will waste PermGen memory
    try {
      return new ProxyConstructor<>(injectionPoint, enhancer, interceptors, callbacks);
    } catch (Throwable e) {
      throw new Errors()
          .errorEnhancingClass(injectionPoint.getMember().getDeclaringClass(), e)
          .toException();
    }
  }

  /** Constructs instances that participate in AOP. */
  private static class ProxyConstructor<T> implements ConstructionProxy<T> {
    final InjectionPoint injectionPoint;
    final Constructor<T> constructor;
    final BiFunction<Object, Object[], Object> enhancedConstructor;
    final ImmutableMap<Method, List<MethodInterceptor>> interceptors;
    final InvocationHandler[] callbacks;

    @SuppressWarnings("unchecked") // the constructor promises to construct 'T's
    ProxyConstructor(
        InjectionPoint injectionPoint,
        Function<String, BiFunction<Object, Object[], Object>> enhancer,
        ImmutableMap<Method, List<MethodInterceptor>> interceptors,
        InvocationHandler[] callbacks) {
      this.injectionPoint = injectionPoint;
      this.constructor = (Constructor<T>) injectionPoint.getMember();
      this.enhancedConstructor = BytecodeGen.enhancedConstructor(enhancer, constructor);
      this.interceptors = interceptors;
      this.callbacks = callbacks;
    }

    @Override
    @SuppressWarnings("unchecked") // the enhancer promises to produce 'T's
    public T newInstance(Object... arguments) throws InvocationTargetException {
      return (T) enhancedConstructor.apply(callbacks, arguments);
    }

    @Override
    public InjectionPoint getInjectionPoint() {
      return injectionPoint;
    }

    @Override
    public Constructor<T> getConstructor() {
      return constructor;
    }

    @Override
    public ImmutableMap<Method, List<MethodInterceptor>> getMethodInterceptors() {
      return interceptors;
    }
  }
}
