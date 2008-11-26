/**
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

import com.google.inject.Binder;
import com.google.inject.Exposed;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.binder.PrivateBinder;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * A provider that invokes a method and returns its result.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class ProviderMethod<T> implements Provider<T> {
  // TODO: this should be a top-level implementation of Binding

  private final Key<T> key;
  private final Class<? extends Annotation> scopeAnnotation;
  private final Object instance;
  private final Method method;
  private final List<Provider<?>> parameterProviders;
  private final boolean exposed;

  /**
   * @param method the method to invoke. It's return type must be the same type as {@code key}.
   */
  ProviderMethod(Key<T> key, Method method, Object instance,
      List<Provider<?>> parameterProviders, Class<? extends Annotation> scopeAnnotation) {
    this.key = key;
    this.scopeAnnotation = scopeAnnotation;
    this.instance = instance;
    this.method = method;
    this.parameterProviders = parameterProviders;
    this.exposed = method.isAnnotationPresent(Exposed.class);

    method.setAccessible(true);
  }

  public Key<T> getKey() {
    return key;
  }

  public Method getMethod() {
    return method;
  }

  public void configure(Binder binder) {
    binder = binder.withSource(method);

    if (scopeAnnotation != null) {
      binder.bind(key).toProvider(this).in(scopeAnnotation);
    } else {
      binder.bind(key).toProvider(this);
    }

    if (exposed) {
      ((PrivateBinder) binder).expose(key);
    }
  }

  public T get() {
    Object[] parameters = new Object[parameterProviders.size()];
    for (int i = 0; i < parameters.length; i++) {
      parameters[i] = parameterProviders.get(i).get();
    }

    try {
      // We know this cast is safe becase T is the method's return type.
      @SuppressWarnings({ "unchecked", "UnnecessaryLocalVariable" })
      T result = (T) method.invoke(instance, parameters);
      return result;
    } catch (IllegalAccessException e) {
      throw new AssertionError(e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }
}
