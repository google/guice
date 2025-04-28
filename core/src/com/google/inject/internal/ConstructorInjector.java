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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableSet;
import com.google.inject.internal.MembersInjectorImpl.MethodHandleMembersInjectorImpl;
import com.google.inject.internal.ProvisionListenerStackCallback.ProvisionCallback;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.InjectionPoint;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Creates instances using an injectable constructor. After construction, all injectable fields and
 * methods are injected.
 *
 * @author crazybob@google.com (Bob Lee)
 */
final class ConstructorInjector<T> implements ProvisionCallback<T> {

  private final ImmutableSet<InjectionPoint> injectableMembers;
  private final SingleParameterInjector<?>[] parameterInjectors;
  private final ConstructionProxy<T> constructionProxy;
  @Nullable private final MembersInjectorImpl<T> membersInjector;
  private final int circularFactoryId;

  ConstructorInjector(
      Set<InjectionPoint> injectableMembers,
      ConstructionProxy<T> constructionProxy,
      SingleParameterInjector<?>[] parameterInjectors,
      @Nullable MembersInjectorImpl<T> membersInjector,
      int circularFactoryId) {
    this.injectableMembers = ImmutableSet.copyOf(injectableMembers);
    this.constructionProxy = constructionProxy;
    this.parameterInjectors = parameterInjectors;
    this.membersInjector =
        membersInjector == null || membersInjector.isEmpty() ? null : membersInjector;
    this.circularFactoryId = circularFactoryId;
  }

  public ImmutableSet<InjectionPoint> getInjectableMembers() {
    return injectableMembers;
  }

  ConstructionProxy<T> getConstructionProxy() {
    return constructionProxy;
  }

  /**
   * Construct an instance. Returns {@code Object} instead of {@code T} because it may return a
   * proxy.
   */
  Object construct(
      final InternalContext context,
      Dependency<?> dependency,
      @Nullable ProvisionListenerStackCallback<T> provisionCallback)
      throws InternalProvisionException {
    @SuppressWarnings("unchecked")
    T result = (T) context.tryStartConstruction(circularFactoryId, dependency);
    if (result != null) {
      // We have a circular reference between bindings. Return a proxy.
      return result;
    }

    // Optimization: Don't go through the callback stack if we have no listeners.
    if (provisionCallback == null) {
      return provision(context);
    } else {
      // NOTE: `provision` always calls the callback, even if provision listeners
      // throw exceptions.
      return provisionCallback.provision(context, dependency, this);
    }
  }

  /**
   * Returns a method handle for constructing the instance with the signature {@code
   * (InternalContext, Dependency<?>) -> T}
   */
  MethodHandle getConstructHandle(
      LinkageContext linkageContext,
      @Nullable ProvisionListenerStackCallback<T> provisionCallback) {

    var handle =
        constructionProxy.getConstructHandle(
            SingleParameterInjector.getAllHandles(linkageContext, parameterInjectors));
    checkState(
        handle.type().equals(InternalMethodHandles.ELEMENT_FACTORY_TYPE),
        "expected %s but got %s",
        InternalMethodHandles.FACTORY_TYPE,
        handle.type());
    // If there are members injectors  we call `finishConstructionAndSetReference` so that
    // cycle detection can find the newly constructed reference.
    if (membersInjector != null) {
      handle = InternalMethodHandles.finishConstructionAndSetReference(handle, circularFactoryId);
      // Members injectors have the signature `(Object, InternalContext)->void`
      var membersHandle =
          ((MethodHandleMembersInjectorImpl<?>) membersInjector)
              .getInjectMembersAndNotifyListenersHandle(linkageContext);
      // Compose it into a handle that just returns the first parameter.
      membersHandle =
          MethodHandles.foldArguments(
              MethodHandles.dropArguments(
                  MethodHandles.identity(Object.class), 1, InternalContext.class),
              membersHandle);
      // Then execute the membersHandle after constructing the object (and calling
      // finishConstructionAndSetReference)
      handle = MethodHandles.foldArguments(membersHandle, handle);
    } else {
      // Otherwise we are done!
      handle = InternalMethodHandles.finishConstruction(handle, circularFactoryId);
    }

    if (membersInjector != null) {
      // If we called finishConstructionAndSetReference, we need to clear the reference here.
      handle = InternalMethodHandles.clearReference(handle, circularFactoryId);
    }

    // Wrap the whole thing in a provision callback if needed.
    handle = MethodHandles.dropArguments(handle, 1, Dependency.class);
    handle = InternalMethodHandles.invokeThroughProvisionCallback(handle, provisionCallback);
    // call tryStartConstruction
    handle = InternalMethodHandles.tryStartConstruction(handle, circularFactoryId);
    // (InternalContext)->T
    return handle;
  }

  // Implements ProvisionCallback<T>
  @Override
  public final T call(InternalContext context, Dependency<?> dependency)
      throws InternalProvisionException {
    return provision(context);
  }

  /** Provisions a new T. */
  private T provision(InternalContext context) throws InternalProvisionException {
    MembersInjectorImpl<T> localMembersInjector = membersInjector;
    try {
      T t = null;
      try {
        Object[] parameters = SingleParameterInjector.getAll(context, parameterInjectors);
        t = constructionProxy.newInstance(parameters);
      } finally {
        if (localMembersInjector == null) {
          context.finishConstruction(circularFactoryId, t);
        } else {
          context.finishConstructionAndSetReference(circularFactoryId, t);
        }
      }

      if (localMembersInjector != null) {
        localMembersInjector.injectMembers(t, context);
      }

      return t;
    } catch (InvocationTargetException userException) {
      Throwable cause = userException.getCause() != null ? userException.getCause() : userException;
      throw InternalProvisionException.errorInjectingConstructor(cause)
          .addSource(constructionProxy.getInjectionPoint());
    } finally {
      if (localMembersInjector != null) {
        context.clearCurrentReference(circularFactoryId);
      }
    }
  }
}
