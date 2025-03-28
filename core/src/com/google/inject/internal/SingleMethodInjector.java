/*
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

import static com.google.inject.internal.InternalMethodHandles.BIFUNCTION_APPLY_HANDLE;
import static com.google.inject.internal.InternalMethodHandles.METHOD_INVOKE_HANDLE;
import static com.google.inject.internal.InternalMethodHandles.castReturnTo;
import static com.google.inject.internal.InternalMethodHandles.castReturnToObject;
import static java.lang.invoke.MethodType.methodType;

import com.google.inject.spi.InjectionPoint;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.function.BiFunction;

/** Invokes an injectable method. */
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

  /** Invokes a method. */
  private interface MethodInvoker {
    Object invoke(Object target, Object... parameters)
        throws IllegalAccessException, InvocationTargetException;

    /**
     * Returns a method handle for the injection with the signature (Object, InternalContext)->void.
     */
    MethodHandle getInjectHandle(LinkageContext linkageContext, MethodHandle[] parameterHandles);
  }

  private MethodInvoker createMethodInvoker(final Method method) {
    if (InternalFlags.getUseExperimentalMethodHandlesOption()) {
      MethodHandle methodHandle = InternalMethodHandles.unreflect(method);
      if (methodHandle != null) {
        if ((method.getModifiers() & Modifier.STATIC) != 0) {
          // insert a fake ignored receiver
          methodHandle = MethodHandles.dropArguments(methodHandle, 0, Object.class);
        }
        var finalMethodHandle = methodHandle;
        return new MethodInvoker() {
          @Override
          public Object invoke(Object target, Object... parameters)
              throws InvocationTargetException {
            // This path can happen if the top level caller calls InternalFactory.get()
            // Should rarely happen.
            try {
              return finalMethodHandle.bindTo(target).invokeWithArguments(parameters);
            } catch (Throwable e) {
              throw new InvocationTargetException(e); // match JDK reflection behaviour
            }
          }

          @Override
          public MethodHandle getInjectHandle(
              LinkageContext linkageContext, MethodHandle[] parameterHandles) {
            var methodType = finalMethodHandle.type();
            // Cast each parameterHandle to the type of the parameter it is bound to.
            // This addresses generics, unboxing, and other tiny type differences that Java usually
            // handles for us.
            for (int i = 0; i < parameterHandles.length; i++) {
              parameterHandles[i] =
                  castReturnTo(parameterHandles[i], methodType.parameterType(i + 1));
            }
            // Invoke the handle with the parameters.
            // The signature is now:
            // (Object, InternalContext,InternalContext,InternalContext,InternalContext)-R
            // With one internalContext per parameter.
            var handle = MethodHandles.filterArguments(finalMethodHandle, 1, parameterHandles);
            // We never care about return values from injected methods.
            handle = InternalMethodHandles.dropReturn(handle);
            // Cast the receiver to Object.
            handle = handle.asType(handle.type().changeParameterType(0, Object.class));
            // Merge all the internalcontext parameters, since all the parameters should share it.
            int[] permutations = new int[parameterHandles.length + 1];
            Arrays.fill(permutations, 1);
            permutations[0] = 0;
            handle =
                MethodHandles.permuteArguments(
                    handle,
                    methodType(void.class, Object.class, InternalContext.class),
                    permutations);
            return handle;
          }
        };
      }
    }
    if (InternalFlags.isBytecodeGenEnabled()) {
      try {
        BiFunction<Object, Object[], Object> fastMethod = BytecodeGen.fastMethod(method);
        // (Object o1, Object o2, ...Object oN, Object receiver) -> void
        if (fastMethod != null) {
          MethodHandle fastMethodHandle =
              InternalFlags.getUseExperimentalMethodHandlesOption()
                  ? BIFUNCTION_APPLY_HANDLE
                      .bindTo(fastMethod)
                      .asType(methodType(Object.class, Object[].class, Object.class))
                      // Have it collect N arguments into an array of type Object[]
                      // This is safe because the number of parameters is the same as the number of
                      // parameters to the method which should never exceed the maximum number of
                      // method parameters.
                      .asCollector(Object[].class, method.getParameterCount())
                  : null;
          return new MethodInvoker() {
            @Override
            public Object invoke(Object target, Object... parameters)
                throws InvocationTargetException {
              try {
                return fastMethod.apply(target, parameters);
              } catch (Throwable e) {
                throw new InvocationTargetException(e); // match JDK reflection behaviour
              }
            }

            @Override
            public MethodHandle getInjectHandle(
                LinkageContext linkageContext, MethodHandle[] parameterHandles) {
              // We are calling the fast class method which we have use `asCollector` adapter to
              // turn into N object parameters, cast each parameterInjector to Object to account.
              for (int i = 0; i < parameterHandles.length; i++) {
                parameterHandles[i] = castReturnToObject(parameterHandles[i]);
              }
              // Invoke the handle with the parameters.
              // The signature is now:
              // (nternalContext,InternalContext,InternalContext...,Object receiver)-R
              // With one internalContext per parameter.
              var handle = MethodHandles.filterArguments(fastMethodHandle, 0, parameterHandles);
              // Now permute to 1. move the receiver to the beginning and 2. merge all the internal
              // context parameters
              int[] permutations = new int[parameterHandles.length + 1];
              permutations[parameterHandles.length] = 1;
              handle =
                  MethodHandles.permuteArguments(
                      handle,
                      methodType(void.class, InternalContext.class, Object.class),
                      permutations);
              return handle;
            }
          };
        }
      } catch (Exception | LinkageError e) {
        /* fall-through */
      }
    }

    int modifiers = method.getModifiers();
    if (!Modifier.isPublic(modifiers)
        || !Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
      method.setAccessible(true);
    }
    return new MethodInvoker() {
      @Override
      public Object invoke(Object target, Object... parameters)
          throws IllegalAccessException, InvocationTargetException {
        return method.invoke(target, parameters);
      }

      @Override
      public MethodHandle getInjectHandle(
          LinkageContext linkageContext, MethodHandle[] parameterHandles) {
        // See comments in ProviderMethod on why we use reflection here and how rarely this happens.
        // bind to the `Method` object
        var handle = METHOD_INVOKE_HANDLE.bindTo(method);
        // collect the parameters into an array of type Object[]
        var arrayHandle =
            MethodHandles.identity(Object[].class)
                .asCollector(Object[].class, parameterHandles.length);
        // supply all parameters
        arrayHandle = MethodHandles.filterArguments(arrayHandle, 0, parameterHandles);
        arrayHandle =
            MethodHandles.permuteArguments(
                arrayHandle,
                methodType(Object[].class, InternalContext.class),
                new int[parameterHandles.length]);
        handle = InternalMethodHandles.dropReturn(handle); // we never care about return values.
        handle = MethodHandles.filterArguments(handle, 1, arrayHandle);
        return handle;
      }
    };
  }

  @Override
  public InjectionPoint getInjectionPoint() {
    return injectionPoint;
  }

  @Override
  public void inject(InternalContext context, Object o) throws InternalProvisionException {
    Object[] parameters = SingleParameterInjector.getAll(context, parameterInjectors);

    try {
      var unused = methodInvoker.invoke(o, parameters);
    } catch (IllegalAccessException e) {
      throw new AssertionError(e); // a security manager is blocking us, we're hosed
    } catch (InvocationTargetException userException) {
      Throwable cause = userException.getCause() != null ? userException.getCause() : userException;
      throw InternalProvisionException.errorInjectingMethod(cause).addSource(injectionPoint);
    }
  }

  @Override
  public MethodHandle getInjectHandle(LinkageContext linkageContext) {
    MethodHandle[] parameterInjectors =
        SingleParameterInjector.getAllHandles(linkageContext, this.parameterInjectors);
    var methodHandle =
        methodInvoker
            .getInjectHandle(linkageContext, parameterInjectors)
            .asType(methodType(void.class, Object.class, InternalContext.class));
    methodHandle =
        InternalMethodHandles.catchErrorInMethodAndRethrowWithSource(methodHandle, injectionPoint);
    return methodHandle;
  }
}
