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

package com.google.inject.spi;

import net.sf.cglib.reflect.FastClass;
import net.sf.cglib.reflect.FastConstructor;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Default {@link ConstructionProxyFactory} implementation. Simply invokes the
 * constructor. Can be reused by other {@code ConstructionProxyFactory}
 * implementations.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class DefaultConstructionProxyFactory
    implements ConstructionProxyFactory {

  public <T> ConstructionProxy<T> get(Constructor<T> constructor) {
    FastClass fastClass = FastClass.create(constructor.getDeclaringClass());
    final FastConstructor fastConstructor =
        fastClass.getConstructor(constructor);
    return new ConstructionProxy<T>() {
      @SuppressWarnings({"unchecked"})
      public T newInstance(Object... arguments)
          throws InvocationTargetException {
        return (T) fastConstructor.newInstance(arguments);
      }
    };
  }
}
