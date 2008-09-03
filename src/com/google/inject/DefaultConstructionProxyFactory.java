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

import com.google.inject.internal.BytecodeGen.Visibility;
import static com.google.inject.internal.BytecodeGen.newFastClass;
import com.google.inject.internal.Errors;
import com.google.inject.spi.InjectionPoint;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import net.sf.cglib.reflect.FastClass;
import net.sf.cglib.reflect.FastConstructor;

/**
 * Default {@link ConstructionProxyFactory} implementation. Simply invokes the
 * constructor. Can be reused by other {@code ConstructionProxyFactory}
 * implementations.
 *
 * @author crazybob@google.com (Bob Lee)
 */
class DefaultConstructionProxyFactory implements ConstructionProxyFactory {

  public <T> ConstructionProxy<T> get(Errors errors, final InjectionPoint injectionPoint) {
    @SuppressWarnings("unchecked") // the injection point is for a constructor of T
    final Constructor<T> constructor = (Constructor<T>) injectionPoint.getMember();

    // We can't use FastConstructor if the constructor is non-public.
    if (!Modifier.isPublic(constructor.getModifiers())) {
      constructor.setAccessible(true);
      return new ConstructionProxy<T>() {
        public T newInstance(Object... arguments) throws
            InvocationTargetException {
          try {
            return constructor.newInstance(arguments);
          } catch (InstantiationException e) {
            throw new RuntimeException(e);
          } catch (IllegalAccessException e) {
            throw new AssertionError(e);
          }
        }
        public InjectionPoint getInjectionPoint() {
          return injectionPoint;
        }
        public Constructor<T> getConstructor() {
          return constructor;
        }
      };
    }

    return new ConstructionProxy<T>() {
      Class<T> classToConstruct = constructor.getDeclaringClass();
      FastClass fastClass = newFastClass(classToConstruct, Visibility.PUBLIC);
      final FastConstructor fastConstructor = fastClass.getConstructor(constructor);

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
    };
  }
}
