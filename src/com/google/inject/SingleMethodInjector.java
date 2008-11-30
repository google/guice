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

package com.google.inject;

import com.google.common.collect.ImmutableList;
import com.google.inject.InjectorImpl.MethodInvoker;
import com.google.inject.internal.BytecodeGen.Visibility;
import static com.google.inject.internal.BytecodeGen.newFastClass;
import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;
import com.google.inject.spi.InjectionPoint;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.sf.cglib.reflect.FastClass;
import net.sf.cglib.reflect.FastMethod;

/**
 * Invokes an injectable method.
 */
class SingleMethodInjector implements SingleMemberInjector {
  final MethodInvoker methodInvoker;
  final ImmutableList<SingleParameterInjector<?>> parameterInjectors;
  final InjectionPoint injectionPoint;

  public SingleMethodInjector(InjectorImpl injector, InjectionPoint injectionPoint, Errors errors)
      throws ErrorsException {
    this.injectionPoint = injectionPoint;
    final Method method = (Method) injectionPoint.getMember();

    // We can't use FastMethod if the method is private.
    int modifiers = method.getModifiers();
    if (Modifier.isPrivate(modifiers) || Modifier.isProtected(modifiers)) {
      method.setAccessible(true);
      methodInvoker = new MethodInvoker() {
        public Object invoke(Object target, Object... parameters)
            throws IllegalAccessException, InvocationTargetException {
          return method.invoke(target, parameters);
        }
      };
    } else {
      FastClass fastClass = newFastClass(method.getDeclaringClass(), Visibility.forMember(method));
      final FastMethod fastMethod = fastClass.getMethod(method);

      methodInvoker = new MethodInvoker() {
        public Object invoke(Object target, Object... parameters)
            throws IllegalAccessException, InvocationTargetException {
          return fastMethod.invoke(target, parameters);
        }
      };
    }

    parameterInjectors = injector.getParametersInjectors(injectionPoint.getDependencies(), errors);
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
