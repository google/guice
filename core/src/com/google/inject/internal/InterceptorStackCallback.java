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

import static com.google.inject.internal.BytecodeGen.ENHANCER_BY_GUICE_MARKER;

import com.google.common.collect.Lists;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BiFunction;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * Intercepts a method with a stack of interceptors.
 *
 * <p>Acts as a JDK {@link InvocationHandler} so the proxy using it can avoid Guice-specific types.
 *
 * @author crazybob@google.com (Bob Lee)
 */
final class InterceptorStackCallback implements InvocationHandler {
  private static final String GUICE_INTERNAL_AOP_PACKAGE = "com.google.inject.internal.aop";

  final Method method;
  final MethodInterceptor[] interceptors;
  final BiFunction<Object, Object[], Object> superInvoker;

  public InterceptorStackCallback(
      Method method,
      List<MethodInterceptor> interceptors,
      BiFunction<Object, Object[], Object> superInvoker) {
    this.method = method;
    this.interceptors = interceptors.toArray(new MethodInterceptor[interceptors.size()]);
    this.superInvoker = superInvoker;
  }

  @Override
  public Object invoke(Object proxy, Method unused, Object[] arguments) throws Throwable {
    return new InterceptedMethodInvocation(proxy, arguments, 0).proceed();
  }

  private class InterceptedMethodInvocation implements MethodInvocation {

    final Object proxy;
    final Object[] arguments;
    final int interceptorIndex;

    public InterceptedMethodInvocation(Object proxy, Object[] arguments, int interceptorIndex) {
      this.proxy = proxy;
      this.arguments = arguments;
      this.interceptorIndex = interceptorIndex;
    }

    @Override
    public Object proceed() throws Throwable {
      try {
        return interceptorIndex == interceptors.length
            ? superInvoker.apply(proxy, arguments)
            : interceptors[interceptorIndex].invoke(
                new InterceptedMethodInvocation(proxy, arguments, interceptorIndex + 1));
      } catch (Throwable t) {
        pruneStacktrace(t);
        throw t;
      }
    }

    @Override
    public Method getMethod() {
      return method;
    }

    @Override
    public Object[] getArguments() {
      return arguments;
    }

    @Override
    public Object getThis() {
      return proxy;
    }

    @Override
    public AccessibleObject getStaticPart() {
      return getMethod();
    }
  }

  /**
   * Removes stacktrace elements related to AOP internal mechanics from the throwable's stack trace
   * and any causes it may have.
   */
  private void pruneStacktrace(Throwable throwable) {
    for (Throwable t = throwable; t != null; t = t.getCause()) {
      StackTraceElement[] stackTrace = t.getStackTrace();
      List<StackTraceElement> pruned = Lists.newArrayList();
      for (StackTraceElement element : stackTrace) {
        String className = element.getClassName();
        if (!className.startsWith(InterceptorStackCallback.class.getName())
            && !className.startsWith(GUICE_INTERNAL_AOP_PACKAGE)
            && !className.contains(ENHANCER_BY_GUICE_MARKER)) {
          pruned.add(element);
        }
      }
      t.setStackTrace(pruned.toArray(new StackTraceElement[pruned.size()]));
    }
  }
}
