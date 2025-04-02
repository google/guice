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

import static com.google.inject.internal.InternalMethodHandles.castReturnTo;
import static java.lang.invoke.MethodType.methodType;

import com.google.inject.internal.InjectorImpl.JitLimitation;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.InjectionPoint;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** Sets an injectable field. */
final class SingleFieldInjector implements SingleMemberInjector {
  final Field field;
  final InjectionPoint injectionPoint;
  final Dependency<?> dependency;
  final InternalFactory<?> factory;

  public SingleFieldInjector(InjectorImpl injector, InjectionPoint injectionPoint, Errors errors)
      throws ErrorsException {
    this.injectionPoint = injectionPoint;
    this.field = (Field) injectionPoint.getMember();
    this.dependency = injectionPoint.getDependencies().get(0);

    // Ewwwww...
    field.setAccessible(true);
    factory =
        injector
            .getBindingOrThrow(dependency.getKey(), errors, JitLimitation.NO_JIT)
            .getInternalFactory();
  }

  @Override
  public InjectionPoint getInjectionPoint() {
    return injectionPoint;
  }

  @Override
  public void inject(InternalContext context, Object o) throws InternalProvisionException {
    try {
      Object value = factory.get(context, dependency, /* linked= */ false);
      field.set(o, value);
    } catch (InternalProvisionException e) {
      throw e.addSource(dependency);
    } catch (IllegalAccessException e) {
      throw new AssertionError(e); // a security manager is blocking us, we're hosed
    }
  }

  @Override
  public MethodHandle getInjectHandle(LinkageContext linkageContext) {
    // unreflect should always succeed due to the setAccessible call in the constructor.
    // (T, V)->void
    var handle = InternalMethodHandles.unreflectSetter(field);
    // Add an ignored receiver if there is no reciever parameter (aka it is a static field).
    if (Modifier.isStatic(field.getModifiers())) {
      handle = MethodHandles.dropArguments(handle, 0, Object.class);
    }
    // Catch and rethrow exceptions from our dependency factory.
    var injectHandle =
        InternalMethodHandles.catchInternalProvisionExceptionAndRethrowWithSource(
            MethodHandles.insertArguments(
                factory.getHandle(linkageContext, /* linked= */ false), 1, dependency),
            dependency);
    // We might need a boxing conversion or some other type conversion here to satisfy a generic.
    injectHandle = castReturnTo(injectHandle, handle.type().parameterType(1));
    // Call the injectHandle and pass it to the field handle.
    // (T, InternalContext)->void
    handle = MethodHandles.filterArguments(handle, 1, injectHandle);
    // (Object, InternalContext)->void
    handle = handle.asType(methodType(void.class, Object.class, InternalContext.class));
    return handle;
  }
}
