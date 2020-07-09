/*
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

package com.google.inject.internal;

import com.google.common.collect.ImmutableMap;
import com.google.inject.spi.InjectionPoint;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Produces construction proxies that invoke the class constructor.
 *
 * @author crazybob@google.com (Bob Lee)
 */
final class DefaultConstructionProxyFactory<T> implements ConstructionProxyFactory<T> {

  private final InjectionPoint injectionPoint;

  /** @param injectionPoint an injection point whose member is a constructor of {@code T}. */
  DefaultConstructionProxyFactory(InjectionPoint injectionPoint) {
    this.injectionPoint = injectionPoint;
  }

  @Override
  public ConstructionProxy<T> create() {
    @SuppressWarnings("unchecked") // the injection point is for a constructor of T
    final Constructor<T> constructor = (Constructor<T>) injectionPoint.getMember();

    /*if[AOP]*/
    try {
      BiFunction<Object, Object[], Object> fastConstructor =
          BytecodeGen.fastConstructor(constructor);
      if (fastConstructor != null) {
        return new FastClassProxy<T>(injectionPoint, constructor, fastConstructor);
      }
    } catch (Exception | LinkageError e) {
      /* fall-through */
    }
    /*end[AOP]*/

    return new ReflectiveProxy<T>(injectionPoint, constructor);
  }

  /*if[AOP]*/
  /** A {@link ConstructionProxy} that uses bytecode generation to invoke the constructor. */
  private static final class FastClassProxy<T> implements ConstructionProxy<T> {
    final InjectionPoint injectionPoint;
    final Constructor<T> constructor;
    final BiFunction<Object, Object[], Object> fastConstructor;

    FastClassProxy(
        InjectionPoint injectionPoint,
        Constructor<T> constructor,
        BiFunction<Object, Object[], Object> fastConstructor) {
      this.injectionPoint = injectionPoint;
      this.constructor = constructor;
      this.fastConstructor = fastConstructor;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T newInstance(Object... arguments) throws InvocationTargetException {
      try {
        return (T) fastConstructor.apply(null, arguments);
      } catch (Throwable e) {
        throw new InvocationTargetException(e); // match JDK reflection behaviour
      }
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
    public ImmutableMap<Method, List<org.aopalliance.intercept.MethodInterceptor>>
        getMethodInterceptors() {
      return ImmutableMap.of();
    }
  }
  /*end[AOP]*/

  private static final class ReflectiveProxy<T> implements ConstructionProxy<T> {
    final Constructor<T> constructor;
    final InjectionPoint injectionPoint;

    ReflectiveProxy(InjectionPoint injectionPoint, Constructor<T> constructor) {
      if (!Modifier.isPublic(constructor.getDeclaringClass().getModifiers())
          || !Modifier.isPublic(constructor.getModifiers())) {
        constructor.setAccessible(true);
      }
      this.injectionPoint = injectionPoint;
      this.constructor = constructor;
    }

    @Override
    public T newInstance(Object... arguments) throws InvocationTargetException {
      try {
        return constructor.newInstance(arguments);
      } catch (InstantiationException e) {
        throw new AssertionError(e); // shouldn't happen, we know this is a concrete type
      } catch (IllegalAccessException e) {
        throw new AssertionError(e); // a security manager is blocking us, we're hosed
      }
    }

    @Override
    public InjectionPoint getInjectionPoint() {
      return injectionPoint;
    }

    @Override
    public Constructor<T> getConstructor() {
      return constructor;
    }

    /*if[AOP]*/
    @Override
    public ImmutableMap<Method, List<org.aopalliance.intercept.MethodInterceptor>>
        getMethodInterceptors() {
      return ImmutableMap.of();
    }
    /*end[AOP]*/
  }
}
