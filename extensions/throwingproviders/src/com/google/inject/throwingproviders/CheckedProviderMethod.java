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

package com.google.inject.throwingproviders;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import com.google.inject.Exposed;
import com.google.inject.Key;
import com.google.inject.PrivateBinder;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.ScopedBindingBuilder;
import com.google.inject.internal.util.StackTraceElements;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.HasDependencies;
import com.google.inject.throwingproviders.ThrowingProviderBinder.SecondaryBinder;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * A provider that invokes a method and returns its result.
 *
 * @author sameb@google.com (Sam Berlin)
 */
class CheckedProviderMethod<T> implements CheckedProvider<T>, HasDependencies {
  private final Key<T> key;
  private final Class<? extends Annotation> scopeAnnotation;
  private final Object instance;
  private final Method method;
  private final ImmutableSet<Dependency<?>> dependencies;
  private final List<Provider<?>> parameterProviders;
  private final boolean exposed;

  @SuppressWarnings("rawtypes") // Class literal uses rawtypes.
  private final Class<? extends CheckedProvider> checkedProvider;

  private final List<TypeLiteral<?>> exceptionTypes;
  private final boolean scopeExceptions;

  CheckedProviderMethod(
      Key<T> key,
      Method method,
      Object instance,
      ImmutableSet<Dependency<?>> dependencies,
      List<Provider<?>> parameterProviders,
      Class<? extends Annotation> scopeAnnotation,
      Class<? extends CheckedProvider> checkedProvider,
      List<TypeLiteral<?>> exceptionTypes,
      boolean scopeExceptions) {
    this.key = key;
    this.scopeAnnotation = scopeAnnotation;
    this.instance = instance;
    this.dependencies = dependencies;
    this.method = method;
    this.parameterProviders = parameterProviders;
    this.exposed = method.isAnnotationPresent(Exposed.class);
    this.checkedProvider = checkedProvider;
    this.exceptionTypes = exceptionTypes;
    this.scopeExceptions = scopeExceptions;

    method.setAccessible(true);
  }

  void configure(Binder binder) {
    binder = binder.withSource(method);

    SecondaryBinder<?, ?> sbinder =
        ThrowingProviderBinder.create(binder).bind(checkedProvider, key.getTypeLiteral());
    if (key.getAnnotation() != null) {
      sbinder = sbinder.annotatedWith(key.getAnnotation());
    } else if (key.getAnnotationType() != null) {
      sbinder = sbinder.annotatedWith(key.getAnnotationType());
    }
    sbinder.scopeExceptions(scopeExceptions);
    ScopedBindingBuilder sbbuilder = sbinder.toProviderMethod(this);
    if (scopeAnnotation != null) {
      sbbuilder.in(scopeAnnotation);
    }

    if (exposed) {
      // the cast is safe 'cause the only binder we have implements PrivateBinder. If there's a
      // misplaced @Exposed, calling this will add an error to the binder's error queue
      ((PrivateBinder) binder).expose(sbinder.getKey());
    }

    CheckedProvideUtils.validateExceptions(
        binder, exceptionTypes, sbinder.getExceptionTypes(), checkedProvider);
  }

  @Override
  public T get() throws Exception {
    Object[] parameters = new Object[parameterProviders.size()];
    Arrays.setAll(parameters, i -> parameterProviders.get(i).get());

    try {
      // We know this cast is safe becase T is the method's return type.
      @SuppressWarnings({"unchecked", "UnnecessaryLocalVariable"})
      T result = (T) method.invoke(instance, parameters);
      return result;
    } catch (IllegalAccessException e) {
      throw new AssertionError(e);
    } catch (InvocationTargetException e) {
      Throwable t = e.getCause();
      if (t instanceof Exception) {
        throw (Exception) t;
      } else if (t instanceof Error) {
        throw (Error) t;
      } else {
        throw new IllegalStateException(t);
      }
    }
  }

  @Override
  public Set<Dependency<?>> getDependencies() {
    return dependencies;
  }

  @Override
  public String toString() {
    return "@CheckedProvides " + StackTraceElements.forMember(method);
  }
}
