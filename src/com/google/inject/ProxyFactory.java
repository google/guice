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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.internal.BytecodeGen.Visibility;
import static com.google.inject.internal.BytecodeGen.newEnhancer;
import static com.google.inject.internal.BytecodeGen.newFastClass;
import com.google.inject.internal.ReferenceCache;
import com.google.inject.spi.InjectionPoint;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
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
 * Proxies classes applying interceptors to methods.
 *
 * @author crazybob@google.com (Bob Lee)
 */
class ProxyFactory implements ConstructionProxyFactory {

  final List<MethodAspect> methodAspects;
  final ConstructionProxyFactory defaultFactory;

  ProxyFactory(List<MethodAspect> methodAspects) {
    this.methodAspects = methodAspects;
    defaultFactory = new DefaultConstructionProxyFactory();
  }

  @SuppressWarnings("unchecked") // the constructed T is the same as the injection point's T
  public <T> ConstructionProxy<T> get(InjectionPoint injectionPoint) {
    return (ConstructionProxy<T>) constructionProxies.get(injectionPoint);
  }

  /** Cached construction proxies for each injection point */
  ReferenceCache<InjectionPoint, ConstructionProxy> constructionProxies
      = new ReferenceCache<InjectionPoint, ConstructionProxy>() {
    protected ConstructionProxy create(InjectionPoint key) {
      return createConstructionProxy(key);
    }
  };

  <T> ConstructionProxy<T> createConstructionProxy(InjectionPoint injectionPoint) {
    @SuppressWarnings("unchecked") // the member of injectionPoint is always a Constructor<T>
    Constructor<T> constructor = (Constructor<T>) injectionPoint.getMember();
    Class<T> declaringClass = constructor.getDeclaringClass();

    // Find applicable aspects. Bow out if none are applicable to this class.
    List<MethodAspect> applicableAspects = Lists.newArrayList();
    for (MethodAspect methodAspect : methodAspects) {
      if (methodAspect.matches(declaringClass)) {
        applicableAspects.add(methodAspect);
      }
    }
    if (applicableAspects.isEmpty()) {
      return defaultFactory.get(injectionPoint);
    }

    // Get list of methods from cglib.
    List<Method> methods = Lists.newArrayList();
    Enhancer.getMethods(declaringClass, null, methods);
    final Map<Method, Integer> indices = Maps.newHashMap();

    // Create method/interceptor holders and record indices.
    List<MethodInterceptorsPair> methodInterceptorsPairs = Lists.newArrayList();
    for (int i = 0; i < methods.size(); i++) {
      Method method = methods.get(i);
      methodInterceptorsPairs.add(new MethodInterceptorsPair(method));
      indices.put(method, i);
    }

    // PUBLIC if all the methods we're intercepting are public. This impacts which classloader we
    // should use for loading the enhanced class
    Visibility visibility = Visibility.PUBLIC;

    // Iterate over aspects and add interceptors for the methods they apply to
    boolean anyMatched = false;
    for (MethodAspect methodAspect : applicableAspects) {
      for (MethodInterceptorsPair pair : methodInterceptorsPairs) {
        if (methodAspect.matches(pair.method)) {
          visibility = visibility.and(Visibility.forMember(pair.method));
          pair.addAll(methodAspect.interceptors());
          anyMatched = true;
        }
      }
    }
    if (!anyMatched) {
      // not test-covered
      return defaultFactory.get(injectionPoint);
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
      } else {
        callbacks[i] = new InterceptorStackCallback(pair.method, pair.interceptors);
        callbackTypes[i] = net.sf.cglib.proxy.MethodInterceptor.class;
      }
    }

    // Create the proxied class.
    Enhancer enhancer = newEnhancer(declaringClass, visibility);
    enhancer.setCallbackFilter(new CallbackFilter() {
      public int accept(Method method) {
        return indices.get(method);
      }
    });
    enhancer.setCallbackTypes(callbackTypes);

    Class<?> proxied = enhancer.createClass();

    // Store callbacks.
    Enhancer.registerStaticCallbacks(proxied, callbacks);

    return createConstructionProxy(proxied, injectionPoint);
  }

  /**
   * Creates a construction proxy given a class and parameter types.
   */
  private <T> ConstructionProxy<T> createConstructionProxy(final Class<?> clazz,
      final InjectionPoint injectionPoint) {
    @SuppressWarnings("unchecked") // injection point's member must be a Constructor<T>
    final Constructor<T> standardConstructor = (Constructor<T>) injectionPoint.getMember();
    FastClass fastClass = newFastClass(clazz, Visibility.forMember(standardConstructor));
    final FastConstructor fastConstructor
        = fastClass.getConstructor(standardConstructor.getParameterTypes());

    return new ConstructionProxy<T>() {
      @SuppressWarnings("unchecked")
      public T newInstance(Object... arguments) throws InvocationTargetException {
        return (T) fastConstructor.newInstance(arguments);
      }
      public InjectionPoint getInjectionPoint() {
        return injectionPoint;
      }
      public Constructor<T> getConstructor() {
        return standardConstructor;
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
}
