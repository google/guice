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

import com.google.inject.spi.ClassBinding;
import com.google.inject.spi.BindingVisitor;
import com.google.inject.spi.Dependency;
import com.google.inject.internal.ToStringBuilder;
import com.google.inject.InjectorImpl.SingleParameterInjector;
import java.util.Collection;

/**
 *
 *
 */
class ClassBindingImpl<T> extends BindingImpl<T>
    implements ClassBinding<T> {

  ClassBindingImpl(InjectorImpl injector, Key<T> key, Object source,
      InternalFactory<? extends T> internalFactory, Scope scope) {
    super(injector, key, source, internalFactory, scope);
  }

  public void accept(BindingVisitor<? super T> visitor) {
    visitor.visit(this);
  }

  @SuppressWarnings("unchecked")
  public Class<T> getBoundClass() {
    // T should always be the class itself.
    return (Class<T>) key.getRawType();
  }

  public Collection<Dependency<?>> getDependencies() {
    Class<T> boundClass = getBoundClass();
    Collection<Dependency<?>> injectors
        = injector.getModifiableFieldAndMethodDependenciesFor(boundClass);
    ConstructorInjector<T> constructor = injector.getConstructor(boundClass);
    if (constructor.parameterInjectors != null) {
      for (SingleParameterInjector<?> parameterInjector
          : constructor.parameterInjectors) {
        injectors.add(parameterInjector.externalContext);
      }
    }
    return injectors;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(ClassBinding.class)
        .add("class", getBoundClass())
        .add("scope", scope)
        .add("source", source)
        .toString();
  }
}
