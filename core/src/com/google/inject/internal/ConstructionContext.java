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

import com.google.inject.internal.InjectorImpl.InjectorOptions;
import java.util.ArrayList;
import java.util.List;

/**
 * Context of a dependency construction. Used to manage circular references.
 *
 * @author crazybob@google.com (Bob Lee)
 */
final class ConstructionContext<T> {

  T currentReference;
  boolean constructing;

  List<DelegatingInvocationHandler<T>> invocationHandlers;

  public T getCurrentReference() {
    return currentReference;
  }

  public void removeCurrentReference() {
    this.currentReference = null;
  }

  public void setCurrentReference(T currentReference) {
    this.currentReference = currentReference;
  }

  public boolean isConstructing() {
    return constructing;
  }

  public void startConstruction() {
    this.constructing = true;
  }

  public void finishConstruction() {
    this.constructing = false;
    invocationHandlers = null;
  }

  public Object createProxy(InjectorOptions injectorOptions, Class<?> expectedType)
      throws InternalProvisionException {
    if (injectorOptions.disableCircularProxies) {
      throw InternalProvisionException.circularDependenciesDisabled(expectedType);
    }
    if (!expectedType.isInterface()) {
      throw InternalProvisionException.cannotProxyClass(expectedType);
    }

    if (invocationHandlers == null) {
      invocationHandlers = new ArrayList<>();
    }

    DelegatingInvocationHandler<T> invocationHandler = new DelegatingInvocationHandler<>();
    invocationHandlers.add(invocationHandler);

    // TODO: if I create a proxy which implements all the interfaces of
    // the implementation type, I'll be able to get away with one proxy
    // instance (as opposed to one per caller).
    return BytecodeGen.newCircularProxy(expectedType, invocationHandler);
  }

  public void setProxyDelegates(T delegate) {
    if (invocationHandlers != null) {
      for (DelegatingInvocationHandler<T> handler : invocationHandlers) {
        handler.setDelegate(delegate);
      }
      // initialization of each handler can happen no more than once
      invocationHandlers = null;
    }
  }
}
