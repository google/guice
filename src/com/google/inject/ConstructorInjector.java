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

import com.google.inject.spi.ConstructionProxy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Injects constructors.
 *
 * @author crazybob@google.com (Bob Lee)
*/
class ConstructorInjector<T> implements Factory<T> {

  final Class<T> implementation;
  final ContainerImpl.Injector[] injectors;
  final ContainerImpl.ParameterInjector<?>[] parameterInjectors;
  final ConstructionProxy<T> constructionProxy;

  private ContainerImpl container;

  ConstructorInjector(ContainerImpl container, Class<T> implementation) {
    this.container = container;
    this.implementation = implementation;
    Constructor<T> constructor = findConstructorIn(implementation);
    parameterInjectors = createParameterInjector(constructor);
    injectors = container.injectors.get(implementation)
        .toArray(new ContainerImpl.Injector[0]);
    constructionProxy = container.constructionProxyFactory.get(constructor);
  }

  ContainerImpl.ParameterInjector<?>[] createParameterInjector(
      Constructor<T> constructor) {
    try {
      Inject inject = constructor.getAnnotation(Inject.class);
      return inject == null
          ? null // default constructor.
          : container.getParametersInjectors(
              constructor,
              constructor.getParameterAnnotations(),
              constructor.getGenericParameterTypes(),
              inject.value()
          );
    } catch (ContainerImpl.MissingDependencyException e) {
      e.handle(container.errorHandler);
      return null;
    }
  }

  @SuppressWarnings({"unchecked"})
  private Constructor<T> findConstructorIn(Class<T> implementation) {
    Constructor<T> found = null;
    for (Constructor<T> constructor
        : implementation.getDeclaredConstructors()) {
      if (constructor.getAnnotation(Inject.class) != null) {
        if (found != null) {
          container.errorHandler.handle(
              ErrorMessages.TOO_MANY_CONSTRUCTORS, implementation);
          return ContainerImpl.invalidConstructor();
        }
        found = constructor;
      }
    }
    if (found != null) {
      return found;
    }

    // If no annotated constructor is found, look for a no-arg constructor
    // instead.
    try {
      return implementation.getDeclaredConstructor();
    } catch (NoSuchMethodException e) {
      container.errorHandler.handle(ErrorMessages.MISSING_CONSTRUCTOR, implementation);
      return ContainerImpl.invalidConstructor();
    }
  }

  /**
   * Construct an instance. Returns {@code Object} instead of {@code T}
   * because it may return a proxy.
   */
  Object construct(InternalContext context, Class<? super T> expectedType) {
    ConstructionContext<T> constructionContext =
        context.getConstructionContext(this);

    // We have a circular reference between constructors. Return a proxy.
    if (constructionContext.isConstructing()) {
      // TODO (crazybob): if we can't proxy this object, can we proxy the
      // other object?
      return constructionContext.createProxy(expectedType);
    }

    // If we're re-entering this factory while injecting fields or methods,
    // return the same instance. This prevents infinite loops.
    T t = constructionContext.getCurrentReference();
    if (t != null) {
      return t;
    }

    try {
      // First time through...
      constructionContext.startConstruction();
      try {
        Object[] parameters =
            ContainerImpl.getParameters(context, parameterInjectors);
        t = newInstance(parameters);
        constructionContext.setProxyDelegates(t);
      } finally {
        constructionContext.finishConstruction();
      }

      // Store reference. If an injector re-enters this factory, they'll
      // get the same reference.
      constructionContext.setCurrentReference(t);

      // Inject fields and methods.
      for (int i = 0; i < injectors.length; i++) {
        injectors[i].inject(context, t);
      }

      return t;
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    } finally {
      constructionContext.removeCurrentReference();
    }
  }

  @SuppressWarnings({"unchecked"})
  private T newInstance(Object[] parameters)
      throws InvocationTargetException {
    return (T) constructionProxy.newInstance(parameters);
  }

  public T get() {
    try {
      return container.callInContext(new ContextualCallable<T>() {
        @SuppressWarnings({"unchecked"})
        public T call(InternalContext context) {
          return (T) construct(context, implementation);
        }
      });
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
