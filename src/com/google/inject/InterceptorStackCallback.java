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

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.List;
import net.sf.cglib.proxy.MethodProxy;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * Intercepts a method with a stack of interceptors.
 *
 * @author crazybob@google.com (Bob Lee)
 */
class InterceptorStackCallback implements net.sf.cglib.proxy.MethodInterceptor {

  final MethodInterceptor[] interceptors;
  final Method method;

  public InterceptorStackCallback(Method method,
      List<MethodInterceptor> interceptors) {
    this.method = method;
    this.interceptors = interceptors.toArray(new MethodInterceptor[interceptors.size()]);
  }

  public Object intercept(Object proxy, Method method, Object[] arguments,
      MethodProxy methodProxy) throws Throwable {
    return new InterceptedMethodInvocation(proxy, methodProxy, arguments).proceed();
  }

  class InterceptedMethodInvocation implements MethodInvocation {

    final Object proxy;
    final Object[] arguments;
    final MethodProxy methodProxy;
    int index = -1;

    public InterceptedMethodInvocation(Object proxy, MethodProxy methodProxy,
        Object[] arguments) {
      this.proxy = proxy;
      this.methodProxy = methodProxy;
      this.arguments = arguments;
    }

    public Object proceed() throws Throwable {
      try {
        index++;
        return index == interceptors.length
            ? methodProxy.invokeSuper(proxy, arguments)
            : interceptors[index].invoke(this);
      }
      finally {
        index--;
      }
    }

    public Method getMethod() {
      return method;
    }

    public Object[] getArguments() {
      return arguments;
    }

    public Object getThis() {
      return proxy;
    }

    public AccessibleObject getStaticPart() {
      return getMethod();
    }
  }
}
