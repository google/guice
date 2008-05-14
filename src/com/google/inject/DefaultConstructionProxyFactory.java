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

import com.google.inject.internal.ErrorHandler;
import com.google.inject.internal.GuiceFastClass;
import net.sf.cglib.reflect.FastClass;
import net.sf.cglib.reflect.FastConstructor;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * Default {@link ConstructionProxyFactory} implementation. Simply invokes the
 * constructor. Can be reused by other {@code ConstructionProxyFactory}
 * implementations.
 *
 * @author crazybob@google.com (Bob Lee)
 */
class DefaultConstructionProxyFactory implements ConstructionProxyFactory {

  private final ErrorHandler errorHandler;

  DefaultConstructionProxyFactory(ErrorHandler errorHandler) {
    this.errorHandler = errorHandler;
  }

  public <T> ConstructionProxy<T> get(final Constructor<T> constructor) {
    // We can't use FastConstructor if the constructor is private or protected.
    if (Modifier.isPrivate(constructor.getModifiers())
        || Modifier.isProtected(constructor.getModifiers())) {
      constructor.setAccessible(true);
      return new ConstructionProxy<T>() {
        public T newInstance(Object... arguments) throws
            InvocationTargetException {
          try {
            return constructor.newInstance(arguments);
          }
          catch (InstantiationException e) {
            throw new RuntimeException(e);
          }
          catch (IllegalAccessException e) {
            throw new AssertionError(e);
          }
        }
        public List<Parameter<?>> getParameters() {
          return Parameter.forConstructor(errorHandler, constructor);
        }
        public Member getMember() {
          return constructor;
        }
      };
    }

    Class<T> classToConstruct = constructor.getDeclaringClass();
    FastClass fastClass = GuiceFastClass.create(classToConstruct);
    final FastConstructor fastConstructor
        = fastClass.getConstructor(constructor);
    return new ConstructionProxy<T>() {
      @SuppressWarnings("unchecked")
      public T newInstance(Object... arguments)
          throws InvocationTargetException {
        return (T) fastConstructor.newInstance(arguments);
      }
      public List<Parameter<?>> getParameters() {
        return Parameter.forConstructor(errorHandler, constructor);
      }
      public Member getMember() {
        return constructor;
      }
    };
  }
}
