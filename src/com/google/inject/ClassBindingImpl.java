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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;
import com.google.inject.internal.ToStringBuilder;
import com.google.inject.spi.BindingTargetVisitor;
import com.google.inject.spi.HasInjections;
import com.google.inject.spi.InjectionPoint;
import java.util.Set;

/**
 *
 *
 */
class ClassBindingImpl<T> extends BindingImpl<T> implements HasInjections {

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

  public Set<InjectionPoint> getInjectionPoints() {
    if (lateBoundConstructor == null) {
      throw new AssertionError();
    }

    Set<InjectionPoint> injectionPoints = Sets.newLinkedHashSet();

    Class<T> boundClass = getBoundClass();
    ConstructorInjector<T> constructor = lateBoundConstructor.constructorInjector;
    injectionPoints.add(constructor.getInjectionPoint());

    try {
      injectionPoints.addAll(injector.getFieldAndMethodInjectionsFor(boundClass));
    } catch (ErrorsException e) {
      throw new AssertionError("This should have failed at CreationTime");
    }

    return ImmutableSet.copyOf(injectionPoints);
  }

  @Override public String toString() {
    return new ToStringBuilder(Binding.class)
        .add("class", getBoundClass())
        .add("scope", scope)
        .add("source", source)
        .toString();
  }
}
