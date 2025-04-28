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

import static com.google.inject.internal.InternalMethodHandles.castReturnTo;
import static com.google.inject.internal.InternalMethodHandles.castReturnToObject;
import static java.lang.invoke.MethodType.methodType;

import com.google.common.collect.ImmutableMap;
import com.google.inject.spi.InjectionPoint;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.IntStream;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * Produces construction proxies that invoke the class constructor.
 *
 * @author crazybob@google.com (Bob Lee)
 */
final class DefaultConstructionProxyFactory<T> implements ConstructionProxyFactory<T> {

  private final InjectionPoint injectionPoint;

  /**
   * @param injectionPoint an injection point whose member is a constructor of {@code T}.
   */
  DefaultConstructionProxyFactory(InjectionPoint injectionPoint) {
    this.injectionPoint = injectionPoint;
  }

  @Override
  public ConstructionProxy<T> create() {
    @SuppressWarnings("unchecked") // the injection point is for a constructor of T
    final Constructor<T> constructor = (Constructor<T>) injectionPoint.getMember();

    if (InternalFlags.getUseMethodHandlesOption()) {
      MethodHandle target = InternalMethodHandles.unreflectConstructor(constructor);
      // If construction fails fall through to the fastclass approach which can
      // access more constructors.  See comments in ProviderMethod on how to change
      // Guice APIs to better support this.
      if (target != null) {
        return new MethodHandleProxy<T>(injectionPoint, constructor, target);
      }
    }

    if (InternalFlags.isBytecodeGenEnabled()) {
      try {
        BiFunction<Object, Object[], Object> fastConstructor =
            BytecodeGen.fastConstructor(constructor);
        if (fastConstructor != null) {
          return new FastClassProxy<T>(injectionPoint, constructor, fastConstructor);
        }
      } catch (Exception | LinkageError e) {
        /* fall-through */
      }
    }
    if (!Modifier.isPublic(constructor.getDeclaringClass().getModifiers())
        || !Modifier.isPublic(constructor.getModifiers())) {
      constructor.setAccessible(true);
    }
    return new ReflectiveProxy<T>(injectionPoint, constructor);
  }

  private abstract static class DefaultConstructorProxy<T> implements ConstructionProxy<T> {
    final InjectionPoint injectionPoint;
    final Constructor<T> constructor;

    DefaultConstructorProxy(InjectionPoint injectionPoint, Constructor<T> constructor) {
      this.injectionPoint = injectionPoint;
      this.constructor = constructor;
    }

    @Override
    public InjectionPoint getInjectionPoint() {
      return injectionPoint;
    }

    @Override
    public Constructor<T> getConstructor() {
      return constructor;
    }

    @Override
    public ImmutableMap<Method, List<MethodInterceptor>> getMethodInterceptors() {
      return ImmutableMap.of();
    }
  }

  /** A {@link ConstructionProxy} that uses bytecode generation to invoke the constructor. */
  private static final class FastClassProxy<T> extends DefaultConstructorProxy<T> {
    final BiFunction<Object, Object[], Object> fastConstructor;

    FastClassProxy(
        InjectionPoint injectionPoint,
        Constructor<T> constructor,
        BiFunction<Object, Object[], Object> fastConstructor) {
      super(injectionPoint, constructor);
      this.fastConstructor = fastConstructor;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T newInstance(Object... arguments) throws InvocationTargetException {
      try {
        return (T) fastConstructor.apply(null, arguments);
      } catch (Throwable e) {
        throw new InvocationTargetException(e); // match JDK reflection behaviour
      }
    }

    @Override
    public MethodHandle getConstructHandle(MethodHandle[] parameterHandles) {
      var handle =
          InternalMethodHandles.BIFUNCTION_APPLY_HANDLE
              .bindTo(fastConstructor)
              .asType(methodType(Object.class, Object.class, Object[].class));
      // (Object[])->Object
      handle = MethodHandles.insertArguments(handle, 0, (Object) null); // no receiver type.
      // catch here so we don't catch errors from our parameters
      handle =
          InternalMethodHandles.catchErrorInConstructorAndRethrowWithSource(handle, injectionPoint);
      // (InternalContext)->Object
      handle =
          MethodHandles.filterArguments(
              handle, 0, InternalMethodHandles.buildObjectArrayFactory(parameterHandles));
      return handle;
    }
  }

  private static final class ReflectiveProxy<T> extends DefaultConstructorProxy<T> {
    ReflectiveProxy(InjectionPoint injectionPoint, Constructor<T> constructor) {
      super(injectionPoint, constructor);
    }

    @Override
    public T newInstance(Object... arguments) throws InvocationTargetException {
      try {
        return constructor.newInstance(arguments);
      } catch (InstantiationException e) {
        throw new AssertionError(e); // shouldn't happen, we know this is a concrete type
      } catch (IllegalAccessException e) {
        throw new AssertionError(e); // a security manager is blocking us, we're hosed
      }
    }

    @Override
    public MethodHandle getConstructHandle(MethodHandle[] parameterHandles) {
      // See comments in ProviderMethod on how this rarely happens and why it happens
      // (Object[])->Object
      var handle = InternalMethodHandles.newInstanceHandle(constructor);
      // Catch here so we don't catch errors from our parameters
      handle =
          InternalMethodHandles.catchErrorInConstructorAndRethrowWithSource(handle, injectionPoint);

      // (InternalContext)->Object
      handle =
          MethodHandles.filterArguments(
              handle, 0, InternalMethodHandles.buildObjectArrayFactory(parameterHandles));
      return handle;
    }
  }

  private static final class MethodHandleProxy<T> extends DefaultConstructorProxy<T> {
    final MethodHandle target;

    MethodHandleProxy(
        InjectionPoint injectionPoint, Constructor<T> constructor, MethodHandle target) {
      super(injectionPoint, constructor);
      this.target = target;
    }

    @Override
    public T newInstance(Object... arguments) throws InvocationTargetException {
      try {
        @SuppressWarnings("unchecked")
        T t = (T) target.invokeWithArguments(arguments);
        return t;
      } catch (Throwable e) {
        throw new InvocationTargetException(e); // match JDK reflection behaviour
      }
    }

    @Override
    public MethodHandle getConstructHandle(MethodHandle[] parameterHandles) {
      var type = target.type();
      // Adapt the parameter handles to the constructor signature.
      var typedHandles =
          IntStream.range(0, parameterHandles.length)
              .mapToObj(i -> castReturnTo(parameterHandles[i], type.parameterType(i)))
              .toArray(MethodHandle[]::new);
      // catch errors from the constructor
      var handle =
          InternalMethodHandles.catchErrorInConstructorAndRethrowWithSource(target, injectionPoint);
      handle = MethodHandles.filterArguments(handle, 0, typedHandles);
      handle = castReturnToObject(handle); // satisfy the signature of the factory type.
      return MethodHandles.permuteArguments(
          handle, InternalMethodHandles.ELEMENT_FACTORY_TYPE, new int[typedHandles.length]);
    }
  }
}
