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

import static com.google.common.base.Preconditions.checkState;
import com.google.common.collect.ImmutableSet;
import com.google.inject.InjectorImpl.LateBoundConstructor;
import com.google.inject.internal.BindingImpl;
import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;
import com.google.inject.internal.InternalFactory;
import com.google.inject.internal.Scoping;
import com.google.inject.internal.ToStringBuilder;
import com.google.inject.spi.BindingTargetVisitor;
import com.google.inject.spi.ConstructorBinding;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.InjectionPoint;
import java.lang.reflect.Constructor;
import java.util.Set;

class ClassBindingImpl<T> extends BindingImpl<T> implements ConstructorBinding<T> {

  private final LateBoundConstructor<T> lateBoundConstructor;
  private ImmutableSet<InjectionPoint> injectionPoints;

  ClassBindingImpl(Injector injector, Key<T> key, Object source,
      InternalFactory<? extends T> internalFactory, Scoping scoping,
      LateBoundConstructor<T> lateBoundConstructor) {
    super(injector, key, source, internalFactory, scoping);
    this.lateBoundConstructor = lateBoundConstructor;
  }

  @Override public void initialize(Injector injector, Errors errors) throws ErrorsException {
    lateBoundConstructor.bind(injector, getKey().getTypeLiteral(), errors);
    injectionPoints = lateBoundConstructor.constructorInjector.getInjectionPoints();
  }

  public <V> V acceptTargetVisitor(BindingTargetVisitor<? super T, V> visitor) {
    checkState(injectionPoints != null, "not initialized");
    return visitor.visitConstructor(this);
  }

  public Constructor<? extends T> getConstructor() {
    return lateBoundConstructor.getConstructor();
  }

  public Set<InjectionPoint> getInjectionPoints() {
    return injectionPoints;
  }

  public Set<Dependency<?>> getDependencies() {
    return Dependency.forInjectionPoints(injectionPoints);
  }

  @Override public String toString() {
    return new ToStringBuilder(ConstructorBinding.class)
        .add("key", getKey())
        .add("scope", getScoping())
        .add("source", getSource())
        .toString();
  }
}
