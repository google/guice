/**
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

import static com.google.inject.internal.BytecodeGen.newFastClass;
import com.google.inject.internal.util.ImmutableList;
import com.google.inject.internal.util.ImmutableMap;
import com.google.inject.internal.util.Lists;
import com.google.inject.internal.util.Maps;
import com.google.inject.spi.InjectionPoint;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.CallbackFilter;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.reflect.FastClass;
import net.sf.cglib.reflect.FastConstructor;
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
  private final ImmutableMap<Method, List<MethodInterceptor>> interceptors;
  private final Class<T> declaringClass;
  private final List<Method> methods;
  private final Callback[] callbacks;

  /**
   * PUBLIC is default; it's used if all the methods we're intercepting are public. This impacts
   * which classloader we should use for loading the enhanced class
   */
  private BytecodeGen.Visibility visibility = BytecodeGen.Visibility.PUBLIC;

  ProxyFactory(InjectionPoint injectionPoint, Iterable<MethodAspect> methodAspects) {
    this.injectionPoint = injectionPoint;

    @SuppressWarnings("unchecked") // the member of injectionPoint is always a Constructor<T>
        Constructor<T> constructor = (Constructor<T>) injectionPoint.getMember();
    declaringClass = constructor.getDeclaringClass();

    // Find applicable aspects. Bow out if none are applicable to this class.
    List<MethodAspect> applicableAspects = Lists.newArrayList();
    for (MethodAspect methodAspect : methodAspects) {
      if (methodAspect.matches(declaringClass)) {
        applicableAspects.add(methodAspect);
      }
    }

    if (applicableAspects.isEmpty()) {
      interceptors = ImmutableMap.of();
      methods = ImmutableList.of();
      callbacks = null;
      return;
    }

    // Get list of methods from cglib.
    methods = Lists.newArrayList();
    Enhancer.getMethods(declaringClass, null, methods);

    // Create method/interceptor holders and record indices.
    List<MethodInterceptorsPair> methodInterceptorsPairs = Lists.newArrayList();
    for (Method method : methods) {
      methodInterceptorsPairs.add(new MethodInterceptorsPair(method));
    }

    // Iterate over aspects and add interceptors for the methods they apply to
    boolean anyMatched = false;
    for (MethodAspect methodAspect : applicableAspects) {
      for (MethodInterceptorsPair pair : methodInterceptorsPairs) {
        if (methodAspect.matches(pair.method)) {
          if(pair.method.isSynthetic()) {
            logger.log(Level.WARNING,
                "Method [{0}] is synthetic and is being intercepted by {1}."
              + " This could indicate a bug.  The method may be intercepted twice,"
              + " or may not be intercepted at all.",
                new Object[] { pair.method, methodAspect.interceptors() });
          }
          visibility = visibility.and(BytecodeGen.Visibility.forMember(pair.method));
          pair.addAll(methodAspect.interceptors());
          anyMatched = true;
        }
      }
    }

    if (!anyMatched) {
      interceptors = ImmutableMap.of();
      callbacks = null;
      return;
    }

    ImmutableMap.Builder<Method, List<MethodInterceptor>> interceptorsMapBuilder = null; // lazy

    callbacks = new Callback[methods.size()];
    for (int i = 0; i < methods.size(); i++) {
      MethodInterceptorsPair pair = methodInterceptorsPairs.get(i);

      if (!pair.hasInterceptors()) {
        callbacks[i] = net.sf.cglib.proxy.NoOp.INSTANCE;
        continue;
      }

      if (interceptorsMapBuilder == null) {
        interceptorsMapBuilder = ImmutableMap.builder();
      }

      interceptorsMapBuilder.put(pair.method, ImmutableList.copyOf(pair.interceptors));
      callbacks[i] = new InterceptorStackCallback(pair.method, pair.interceptors);
    }

    interceptors = interceptorsMapBuilder != null
        ? interceptorsMapBuilder.build()
        : ImmutableMap.<Method, List<MethodInterceptor>>of();
  }

  /**
   * Returns the interceptors that apply to the constructed type.
   */
  public ImmutableMap<Method, List<MethodInterceptor>> getInterceptors() {
    return interceptors;
  }

  public ConstructionProxy<T> create() throws ErrorsException {
    if (interceptors.isEmpty()) {
      return new DefaultConstructionProxyFactory<T>(injectionPoint).create();
    }

    @SuppressWarnings("unchecked")
    Class<? extends Callback>[] callbackTypes = new Class[callbacks.length];
    for (int i = 0; i < callbacks.length; i++) {
      if (callbacks[i] == net.sf.cglib.proxy.NoOp.INSTANCE) {
        callbackTypes[i] = net.sf.cglib.proxy.NoOp.class;
      } else {
        callbackTypes[i] = net.sf.cglib.proxy.MethodInterceptor.class;
      }
    }

    // Create the proxied class. We're careful to ensure that all enhancer state is not-specific
    // to this injector. Otherwise, the proxies for each injector will waste PermGen memory
    try {
    Enhancer enhancer = BytecodeGen.newEnhancer(declaringClass, visibility);
    enhancer.setCallbackFilter(new IndicesCallbackFilter(declaringClass, methods));
    enhancer.setCallbackTypes(callbackTypes);
    return new ProxyConstructor<T>(enhancer, injectionPoint, callbacks, interceptors);
    } catch (Throwable e) {
      throw new Errors().errorEnhancingClass(declaringClass, e).toException();
    }
  }

  private static class MethodInterceptorsPair {
    final Method method;
    List<MethodInterceptor> interceptors; // lazy

    MethodInterceptorsPair(Method method) {
      this.method = method;
    }

    void addAll(List<MethodInterceptor> interceptors) {
      if (this.interceptors == null) {
        this.interceptors = Lists.newArrayList();
      }
      this.interceptors.addAll(interceptors);
    }

    boolean hasInterceptors() {
      return interceptors != null;
    }
  }

  /**
   * A callback filter that maps methods to unique IDs. We define equals and hashCode using the
   * declaring class so that enhanced classes can be shared between injectors.
   */
  private static class IndicesCallbackFilter implements CallbackFilter {
    final Class<?> declaringClass;
    final Map<Method, Integer> indices;

    IndicesCallbackFilter(Class<?> declaringClass, List<Method> methods) {
      this.declaringClass = declaringClass;
      final Map<Method, Integer> indices = Maps.newHashMap();
      for (int i = 0; i < methods.size(); i++) {
        Method method = methods.get(i);
        indices.put(method, i);
      }

      this.indices = indices;
    }

    public int accept(Method method) {
      return indices.get(method);
    }

    @Override public boolean equals(Object o) {
      return o instanceof IndicesCallbackFilter &&
          ((IndicesCallbackFilter) o).declaringClass == declaringClass;
    }

    @Override public int hashCode() {
      return declaringClass.hashCode();
    }
  }

  /**
   * Constructs instances that participate in AOP.
   */
  private static class ProxyConstructor<T> implements ConstructionProxy<T> {
    final Class<?> enhanced;
    final InjectionPoint injectionPoint;
    final Constructor<T> constructor;
    final Callback[] callbacks;

    final FastConstructor fastConstructor;
    final ImmutableMap<Method, List<MethodInterceptor>> methodInterceptors;

    @SuppressWarnings("unchecked") // the constructor promises to construct 'T's
    ProxyConstructor(Enhancer enhancer, InjectionPoint injectionPoint, Callback[] callbacks,
        ImmutableMap<Method, List<MethodInterceptor>> methodInterceptors) {
      this.enhanced = enhancer.createClass(); // this returns a cached class if possible
      this.injectionPoint = injectionPoint;
      this.constructor = (Constructor<T>) injectionPoint.getMember();
      this.callbacks = callbacks;
      this.methodInterceptors = methodInterceptors;

      FastClass fastClass = newFastClass(enhanced, BytecodeGen.Visibility.forMember(constructor));
      this.fastConstructor = fastClass.getConstructor(constructor.getParameterTypes());
    }

    @SuppressWarnings("unchecked") // the constructor promises to produce 'T's
    public T newInstance(Object... arguments) throws InvocationTargetException {
      Enhancer.registerCallbacks(enhanced, callbacks);
      try {
        return (T) fastConstructor.newInstance(arguments);
      } finally {
        Enhancer.registerCallbacks(enhanced, null);
      }
    }

    public InjectionPoint getInjectionPoint() {
      return injectionPoint;
    }

    public Constructor<T> getConstructor() {
      return constructor;
    }

    public ImmutableMap<Method, List<MethodInterceptor>> getMethodInterceptors() {
      return methodInterceptors;
    }
  }
}
