/**
 * Copyright (C) 2008 Google Inc.
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

import com.google.inject.internal.BytecodeGen.Visibility;
import com.google.inject.internal.InjectorImpl.MethodInvoker;
import com.google.inject.spi.InjectionPoint;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Invokes an injectable method.
 */
final class SingleMethodInjector implements SingleMemberInjector {
  private final MethodInvoker methodInvoker;
  private final SingleParameterInjector<?>[] parameterInjectors;
  private final InjectionPoint injectionPoint;

  SingleMethodInjector(InjectorImpl injector, InjectionPoint injectionPoint, Errors errors)
      throws ErrorsException {
    this.injectionPoint = injectionPoint;
    final Method method = (Method) injectionPoint.getMember();
    methodInvoker = createMethodInvoker(method);
    parameterInjectors = injector.getParametersInjectors(injectionPoint.getDependencies(), errors);
  }

  private MethodInvoker createMethodInvoker(final Method method) {

    // We can't use FastMethod if the method is private.
    int modifiers = method.getModifiers();
    if (!Modifier.isPrivate(modifiers) && !Modifier.isProtected(modifiers)) {
      /*if[AOP]*/
      try {
      final net.sf.cglib.reflect.FastMethod fastMethod
          = BytecodeGen.newFastClass(method.getDeclaringClass(), Visibility.forMember(method))
              .getMethod(method);

      return new MethodInvoker() {
        public Object invoke(Object target, Object... parameters)
            throws IllegalAccessException, InvocationTargetException {
          return fastMethod.invoke(target, parameters);
        }
      };
      } catch (net.sf.cglib.core.CodeGenerationException e) {/* fall-through */}
      /*end[AOP]*/
    }

    if (!Modifier.isPublic(modifiers) ||
        !Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
      method.setAccessible(true);
    }

    return new MethodInvoker() {
      public Object invoke(Object target, Object... parameters)
          throws IllegalAccessException, InvocationTargetException {
        return method.invoke(target, parameters);
      }
    };
  }

  public InjectionPoint getInjectionPoint() {
    return injectionPoint;
  }

  public void inject(Errors errors, InternalContext context, Object o) {
    Object[] parameters;
    try {
      parameters = SingleParameterInjector.getAll(errors, context, parameterInjectors);
    } catch (ErrorsException e) {
      errors.merge(e.getErrors());
      return;
    }

    try {
      methodInvoker.invoke(o, parameters);
    } catch (IllegalAccessException e) {
      throw new AssertionError(e); // a security manager is blocking us, we're hosed
    } catch (InvocationTargetException userException) {
      Throwable cause = userException.getCause() != null
          ? userException.getCause()
          : userException;
      errors.withSource(injectionPoint).errorInjectingMethod(cause);
    }
  }
}
