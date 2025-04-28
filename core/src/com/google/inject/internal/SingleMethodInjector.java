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
import static com.google.inject.internal.InternalMethodHandles.castReturnTo;
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
    if (InternalFlags.getUseMethodHandlesOption()) {
      MethodHandle methodHandle = InternalMethodHandles.unreflect(method);
      if (methodHandle != null) {
        methodHandle = InternalMethodHandles.dropReturn(methodHandle);
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
            // Catch and propagate exceptions from the method directly.
            var handle =
                InternalMethodHandles.catchErrorInMethodAndRethrowWithSource(
                    finalMethodHandle, injectionPoint);
            var methodType = handle.type();
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
            handle = MethodHandles.filterArguments(handle, 1, parameterHandles);
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
        if (fastMethod != null) {
          // (Object receiver, Object[]) -> void
          MethodHandle fastMethodHandle;
          if (InternalFlags.getUseMethodHandlesOption()) {
            var handle =
                BIFUNCTION_APPLY_HANDLE
                    .bindTo(fastMethod)
                    // Cast the first parameter to `Object[]`
                    .asType(methodType(Object.class, Object[].class, Object.class));
            handle = InternalMethodHandles.dropReturn(handle);
            // Swap so the receiver is first
            handle =
                MethodHandles.permuteArguments(
                    handle,
                    methodType(void.class, Object.class, InternalContext.class),
                    new int[] {1, 0});
            fastMethodHandle = handle;
          } else {
            fastMethodHandle = null;
          }
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
              // Invoke the handle with the parameters.
              // The signature is now:
              // (InternalContext, Object reciever)-R
              var handle =
                  InternalMethodHandles.catchErrorInMethodAndRethrowWithSource(
                      fastMethodHandle, injectionPoint);
              handle =
                  MethodHandles.filterArguments(
                      handle, 1, InternalMethodHandles.buildObjectArrayFactory(parameterHandles));
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
        // (Object, Object[])->Object
        var handle = InternalMethodHandles.invokeHandle(method);
        handle = InternalMethodHandles.dropReturn(handle); // we never care about return values.
        handle =
            InternalMethodHandles.catchErrorInMethodAndRethrowWithSource(handle, injectionPoint);
        // (Object, InternalContext)->Object
        handle =
            MethodHandles.filterArguments(
                handle, 1, InternalMethodHandles.buildObjectArrayFactory(parameterHandles));
        // (Object, InternalContext)->void
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
    return methodInvoker.getInjectHandle(linkageContext, parameterInjectors);
  }
}
