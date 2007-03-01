/**
 * Copyright (C) 2006 Google Inc.
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

package com.google.inject;

import com.google.inject.util.GuiceFastClass;
import com.google.inject.util.GuiceNamingPolicy;
import com.google.inject.util.Objects;
import com.google.inject.util.ReferenceCache;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.CallbackFilter;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.NoOp;
import net.sf.cglib.reflect.FastClass;
import net.sf.cglib.reflect.FastConstructor;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * Proxies classes applying interceptors to methods as specified in
 * {@link ProxyFactoryBuilder}.
 *
 * @author crazybob@google.com (Bob Lee)
 */
class ProxyFactory implements ConstructionProxyFactory {

  final List<MethodAspect> methodAspects;
  final ConstructionProxyFactory defaultFactory
      = new DefaultConstructionProxyFactory();

  ProxyFactory(List<MethodAspect> methodAspects) {
    this.methodAspects = methodAspects;
  }

  Map<Constructor<?>, ConstructionProxy<?>> constructionProxies
      = new ReferenceCache<Constructor<?>, ConstructionProxy<?>>() {
    protected ConstructionProxy<?> create(Constructor<?> constructor) {
      return createConstructionProxy(constructor);
    }
  };

  /**
   * Gets a factory for the given type. Uses the zero-arg constructor. Wraps
   * exceptions in {@link RuntimeException} including
   * {@link InvocationTargetException}.
   */
  public <T> Provider<T> getFactory(Class<T> type) throws NoSuchMethodException {
    final ConstructionProxy<T> constructionProxy
        = createConstructionProxy(type.getDeclaredConstructor());
    return new Provider<T>() {
      public T get() {
        try {
          return constructionProxy.newInstance();
        }
        catch (InvocationTargetException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  <T> ConstructionProxy<T> createConstructionProxy(Constructor<T> constructor) {
    Class<T> declaringClass = constructor.getDeclaringClass();

    // Find applicable aspects. Bow out if none are applicable to this class.
    List<MethodAspect> applicableAspects = new ArrayList<MethodAspect>();
    for (MethodAspect methodAspect : methodAspects) {
      if (methodAspect.matches(declaringClass)) {
        applicableAspects.add(methodAspect);
      }
    }
    if (applicableAspects.isEmpty()) {
      return defaultFactory.get(constructor);
    }

    // Get list of methods from cglib.
    List<Method> methods = new ArrayList<Method>();
    Enhancer.getMethods(declaringClass, null, methods);
    final Map<Method, Integer> indices = new HashMap<Method, Integer>();

    // Create method/interceptor holders and record indices.
    List<MethodInterceptorsPair> methodInterceptorsPairs
        = new ArrayList<MethodInterceptorsPair>();
    for (int i = 0; i < methods.size(); i++) {
      Method method = methods.get(i);
      methodInterceptorsPairs.add(new MethodInterceptorsPair(method));
      indices.put(method, i);
    }

    // Iterate over aspects and add interceptors for the methods they apply to
    boolean anyMatched = false;
    for (MethodAspect methodAspect : applicableAspects) {
      for (MethodInterceptorsPair pair : methodInterceptorsPairs) {
        if (methodAspect.matches(pair.method)) {
          pair.addAll(methodAspect.interceptors());
          anyMatched = true;
        }
      }
    }
    if (!anyMatched) {
      // not test-covered
      return defaultFactory.get(constructor);
    }

    // Create callbacks.
    Callback[] callbacks = new Callback[methods.size()];

    @SuppressWarnings("unchecked")
    Class<? extends Callback>[] callbackTypes = new Class[methods.size()];
    for (int i = 0; i < methods.size(); i++) {
      MethodInterceptorsPair pair = methodInterceptorsPairs.get(i);
      if (!pair.hasInterceptors()) {
        callbacks[i] = NoOp.INSTANCE;
        callbackTypes[i] = NoOp.class;
      }
      else {
        callbacks[i] = new InterceptorStackCallback(
            pair.method, pair.interceptors);
        callbackTypes[i] = net.sf.cglib.proxy.MethodInterceptor.class;
      }
    }

    // Create the proxied class.
    Enhancer enhancer = new Enhancer();
    enhancer.setSuperclass(declaringClass);
    enhancer.setUseCache(false); // We do enough caching.
    enhancer.setCallbackFilter(new CallbackFilter() {
      public int accept(Method method) {
        return indices.get(method);
      }
    });
    enhancer.setCallbackTypes(callbackTypes);
    enhancer.setUseFactory(false);
    enhancer.setNamingPolicy(new GuiceNamingPolicy());

    Class<?> proxied = enhancer.createClass();

    // Store callbacks.
    Enhancer.registerStaticCallbacks(proxied, callbacks);

    return createConstructionProxy(proxied, constructor.getParameterTypes());
  }

  /**
   * Creates a construction proxy given a class and parameter types.
   */
  <T> ConstructionProxy<T> createConstructionProxy(Class<?> clazz,
      Class[] parameterTypes) {
    FastClass fastClass = GuiceFastClass.create(clazz);
    final FastConstructor fastConstructor
        = fastClass.getConstructor(parameterTypes);
    return new ConstructionProxy<T>() {
      @SuppressWarnings("unchecked")
      public T newInstance(Object... arguments)
          throws InvocationTargetException {
        Objects.assertNoNulls(arguments);
        return (T) fastConstructor.newInstance(arguments);
      }
    };
  }

  static class MethodInterceptorsPair {

    final Method method;
    List<MethodInterceptor> interceptors;

    public MethodInterceptorsPair(Method method) {
      this.method = method;
    }

    void addAll(List<MethodInterceptor> interceptors) {
      if (this.interceptors == null) {
        this.interceptors = new ArrayList<MethodInterceptor>();
      }
      this.interceptors.addAll(interceptors);
    }

    boolean hasInterceptors() {
      return interceptors != null;
    }
  }

  @SuppressWarnings("unchecked")
  public <T> ConstructionProxy<T> get(Constructor<T> constructor) {
    return (ConstructionProxy<T>) constructionProxies.get(constructor);
  }
}
