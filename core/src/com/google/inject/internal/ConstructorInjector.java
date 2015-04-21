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

import com.google.common.collect.ImmutableSet;
import com.google.inject.internal.ProvisionListenerStackCallback.ProvisionCallback;
import com.google.inject.spi.InjectionPoint;

import java.lang.reflect.InvocationTargetException;
import java.util.Set;

/**
 * Creates instances using an injectable constructor. After construction, all injectable fields and
 * methods are injected.
 *
 * @author crazybob@google.com (Bob Lee)
 */
final class ConstructorInjector<T> {

  private final ImmutableSet<InjectionPoint> injectableMembers;
  private final SingleParameterInjector<?>[] parameterInjectors;
  private final ConstructionProxy<T> constructionProxy;
  private final MembersInjectorImpl<T> membersInjector;

  ConstructorInjector(Set<InjectionPoint> injectableMembers,
      ConstructionProxy<T> constructionProxy,
      SingleParameterInjector<?>[] parameterInjectors,
      MembersInjectorImpl<T> membersInjector) {
    this.injectableMembers = ImmutableSet.copyOf(injectableMembers);
    this.constructionProxy = constructionProxy;
    this.parameterInjectors = parameterInjectors;
    this.membersInjector = membersInjector;
  }

  public ImmutableSet<InjectionPoint> getInjectableMembers() {
    return injectableMembers;
  }

  ConstructionProxy<T> getConstructionProxy() {
    return constructionProxy;
  }

  /**
   * Construct an instance. Returns {@code Object} instead of {@code T} because
   * it may return a proxy.
   */
  Object construct(final Errors errors, final InternalContext context,
      Class<?> expectedType,
      ProvisionListenerStackCallback<T> provisionCallback)
      throws ErrorsException {
    final ConstructionContext<T> constructionContext = context.getConstructionContext(this);

    // We have a circular reference between constructors. Return a proxy.
    if (constructionContext.isConstructing()) {
      // TODO (crazybob): if we can't proxy this object, can we proxy the other object?
      return constructionContext.createProxy(
          errors, context.getInjectorOptions(), expectedType);
    }

    // If we're re-entering this factory while injecting fields or methods,
    // return the same instance. This prevents infinite loops.
    T t = constructionContext.getCurrentReference();
    if (t != null) {
      return t;
    }

    constructionContext.startConstruction();
    try {
      // Optimization: Don't go through the callback stack if we have no listeners.
      if (!provisionCallback.hasListeners()) {
        return provision(errors, context, constructionContext);
      } else {
        return provisionCallback.provision(errors, context, new ProvisionCallback<T>() {
          public T call() throws ErrorsException {
            return provision(errors, context, constructionContext);
          }
        });
      }
    } finally {
      constructionContext.finishConstruction();
    }
  }

  /** Provisions a new T. */
  private T provision(Errors errors, InternalContext context,
      ConstructionContext<T> constructionContext) throws ErrorsException {
    try {
      T t;
      try {
        Object[] parameters = SingleParameterInjector.getAll(errors, context, parameterInjectors);
        t = constructionProxy.newInstance(parameters);
        constructionContext.setProxyDelegates(t);
      } finally {
        constructionContext.finishConstruction();
      }

      // Store reference. If an injector re-enters this factory, they'll get the same reference.
      constructionContext.setCurrentReference(t);

      membersInjector.injectMembers(t, errors, context, false);
      membersInjector.notifyListeners(t, errors);

      return t;
    } catch (InvocationTargetException userException) {
      Throwable cause = userException.getCause() != null
          ? userException.getCause()
          : userException;
      throw errors.withSource(constructionProxy.getInjectionPoint())
          .errorInjectingConstructor(cause).toException();
    } finally {
      constructionContext.removeCurrentReference();
    }
  }
}
