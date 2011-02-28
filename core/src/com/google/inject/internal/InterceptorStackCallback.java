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

import com.google.inject.internal.util.Lists;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.sf.cglib.proxy.MethodProxy;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * Intercepts a method with a stack of interceptors.
 *
 * @author crazybob@google.com (Bob Lee)
 */
final class InterceptorStackCallback implements net.sf.cglib.proxy.MethodInterceptor {
  private static final Set<String> AOP_INTERNAL_CLASSES = new HashSet<String>(Arrays.asList(
      InterceptorStackCallback.class.getName(),
      InterceptedMethodInvocation.class.getName(),
      MethodProxy.class.getName()));

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

  private class InterceptedMethodInvocation implements MethodInvocation {

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
      } catch (Throwable t) {
        pruneStacktrace(t);
        throw t;
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

  /**
   * Removes stacktrace elements related to AOP internal mechanics from the
   * throwable's stack trace and any causes it may have.
   */
  private void pruneStacktrace(Throwable throwable) {
    for(Throwable t = throwable; t != null; t = t.getCause()) {
      StackTraceElement[] stackTrace = t.getStackTrace();
      List<StackTraceElement> pruned = Lists.newArrayList();
      for (StackTraceElement element : stackTrace) {
        String className = element.getClassName();
        if (!AOP_INTERNAL_CLASSES.contains(className) && !className.contains("$EnhancerByGuice$")) {
          pruned.add(element);
        }
      }
      t.setStackTrace(pruned.toArray(new StackTraceElement[pruned.size()]));
    }
  }
}
