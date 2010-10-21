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

package com.google.inject.internal;

import com.google.inject.internal.BytecodeGen.Visibility;
import com.google.inject.internal.util.ImmutableMap;
import com.google.inject.spi.InjectionPoint;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * Produces construction proxies that invoke the class constructor.
 *
 * @author crazybob@google.com (Bob Lee)
 */
final class DefaultConstructionProxyFactory<T> implements ConstructionProxyFactory<T> {

  private final InjectionPoint injectionPoint;

  /**
   * @param injectionPoint an injection point whose member is a constructor of {@code T}.
   */
  DefaultConstructionProxyFactory(InjectionPoint injectionPoint) {
    this.injectionPoint = injectionPoint;
  }

  public ConstructionProxy<T> create() {
    @SuppressWarnings("unchecked") // the injection point is for a constructor of T
    final Constructor<T> constructor = (Constructor<T>) injectionPoint.getMember();

    // Use FastConstructor if the constructor is public.
    if (Modifier.isPublic(constructor.getModifiers())) {
      Class<T> classToConstruct = constructor.getDeclaringClass();
      /*if[AOP]*/
      try {
        final net.sf.cglib.reflect.FastConstructor fastConstructor
            = BytecodeGen.newFastClass(classToConstruct, Visibility.forMember(constructor))
                .getConstructor(constructor);

      return new ConstructionProxy<T>() {
        @SuppressWarnings("unchecked")
        public T newInstance(Object... arguments) throws InvocationTargetException {
          return (T) fastConstructor.newInstance(arguments);
        }
        public InjectionPoint getInjectionPoint() {
          return injectionPoint;
        }
        public Constructor<T> getConstructor() {
          return constructor;
        }
        public ImmutableMap<Method, List<org.aopalliance.intercept.MethodInterceptor>>
            getMethodInterceptors() {
          return ImmutableMap.of();
        }
      };
      } catch (net.sf.cglib.core.CodeGenerationException e) {/* fall-through */}
      /*end[AOP]*/
      if (!Modifier.isPublic(classToConstruct.getModifiers())) {
        constructor.setAccessible(true);
      }
    } else {
      constructor.setAccessible(true);
    }

    return new ConstructionProxy<T>() {
      public T newInstance(Object... arguments) throws InvocationTargetException {
        try {
          return constructor.newInstance(arguments);
        } catch (InstantiationException e) {
          throw new AssertionError(e); // shouldn't happen, we know this is a concrete type
        } catch (IllegalAccessException e) {
          throw new AssertionError(e); // a security manager is blocking us, we're hosed
        }
      }
      public InjectionPoint getInjectionPoint() {
        return injectionPoint;
      }
      public Constructor<T> getConstructor() {
        return constructor;
      }
      /*if[AOP]*/
      public ImmutableMap<Method, List<org.aopalliance.intercept.MethodInterceptor>>
          getMethodInterceptors() {
        return ImmutableMap.of();
      }
      /*end[AOP]*/
    };
  }
}
