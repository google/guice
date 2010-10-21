/**
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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

class DelegatingInvocationHandler<T> implements InvocationHandler {

  private T delegate;

  public Object invoke(Object proxy, Method method, Object[] args)
      throws Throwable {
    if (delegate == null) {
      throw new IllegalStateException("This is a proxy used to support"
          + " circular references involving constructors. The object we're"
          + " proxying is not constructed yet. Please wait until after"
          + " injection has completed to use this object.");
    }

    try {
      return method.invoke(delegate, args);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } catch (IllegalArgumentException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      throw e.getTargetException();
    }
  }

  public T getDelegate() {
    return delegate;
  }

  void setDelegate(T delegate) {
    this.delegate = delegate;
  }
}
