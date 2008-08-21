/*
 * Copyright (C) 2007 Google Inc.
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

import com.google.inject.InjectorImpl.SingleParameterInjector;
import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;
import com.google.inject.internal.ToStringBuilder;
import com.google.inject.spi.BindingTargetVisitor;
import com.google.inject.spi.InjectionPoint;
import java.util.Collection;

/**
 *
 *
 */
class ClassBindingImpl<T> extends BindingImpl<T> {

  private final InjectorImpl.LateBoundConstructor<T> lateBoundConstructor;

  ClassBindingImpl(InjectorImpl injector, Key<T> key, Object source,
      InternalFactory<? extends T> internalFactory, Scope scope,
      InjectorImpl.LateBoundConstructor<T> lateBoundConstructor,
      LoadStrategy loadStrategy) {
    super(injector, key, source, internalFactory, scope, loadStrategy);
    this.lateBoundConstructor = lateBoundConstructor;
  }

  @Override void initialize(InjectorImpl injector, Errors errors) throws ErrorsException {
    lateBoundConstructor.bind(injector, getBoundClass(), errors);
  }

  public <V> V acceptTargetVisitor(BindingTargetVisitor<? super T, V> visitor) {
    return visitor.visitConstructor(lateBoundConstructor.getConstructor());
  }

  @SuppressWarnings("unchecked")
  public Class<T> getBoundClass() {
    // T should always be the class itself.
    return (Class<T>) key.getRawType();
  }

  public Collection<InjectionPoint<?>> getInjectionPoints() {
    if (lateBoundConstructor == null) {
      throw new AssertionError();
    }

    Class<T> boundClass = getBoundClass();
    Collection<InjectionPoint<?>> injectors;
    try {
      injectors = injector.getModifiableFieldAndMethodInjectionsFor(boundClass);
    } catch (ErrorsException e) {
      throw new AssertionError("This should have failed at CreationTime");
    }

    ConstructorInjector<T> constructor = lateBoundConstructor.constructorInjector;
    if (constructor.parameterInjectors != null) {
      for (SingleParameterInjector<?> parameterInjector
          : constructor.parameterInjectors) {
        injectors.add(parameterInjector.injectionPoint);
      }
    }
    return injectors;
  }

  @Override public String toString() {
    return new ToStringBuilder(Binding.class)
        .add("class", getBoundClass())
        .add("scope", scope)
        .add("source", source)
        .toString();
  }
}
