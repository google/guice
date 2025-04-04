/*
 * Copyright (C) 2009 Google Inc.
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

import com.google.common.base.Preconditions;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class DelegatingInvocationHandler implements InvocationHandler {
  private static final Object UNINITIALIZED_PROXY = new Object();
  private volatile Object delegate = UNINITIALIZED_PROXY;

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    // checking volatile field for synchronization
    final Object delegate = this.delegate;
    Preconditions.checkState(
        delegate != UNINITIALIZED_PROXY,
        "This is a proxy used to support"
            + " circular references. The object we're"
            + " proxying is not constructed yet. Please wait until after"
            + " injection has completed to use this object.");
      Preconditions.checkNotNull(
          delegate,
          "This is a proxy used to support"
              + " circular references. The object we're "
              + " proxying is initialized to null."
              + " No methods can be called.");

    try {
      // TODO: method.setAccessible(true); ?
      // this would fix visibility errors when we proxy a
      // non-public interface.
      return method.invoke(delegate, args);
    } catch (IllegalAccessException | IllegalArgumentException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      throw e.getTargetException();
    }
  }

  void setDelegate(Object delegate) {
    this.delegate = delegate;
  }
}
