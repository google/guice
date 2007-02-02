// Copyright 2006 Google Inc. All Rights Reserved.

package com.google.inject.intercept;

import net.sf.cglib.proxy.MethodProxy;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import java.lang.reflect.Method;
import java.lang.reflect.AccessibleObject;
import java.util.List;

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
    this.interceptors = interceptors.toArray(
        new MethodInterceptor[interceptors.size()]);
  }

  public Object intercept(Object proxy, Method method, Object[] arguments,
      MethodProxy methodProxy) throws Throwable {
    return new InterceptedMethodInvocation(
        proxy, methodProxy, arguments).proceed();
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
      } finally {
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
