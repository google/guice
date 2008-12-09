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

import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;
import com.google.inject.internal.ToStringBuilder;
import com.google.inject.spi.BindingTargetVisitor;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.PrivateEnvironment;
import java.util.Map;

class ExposedBindingImpl<T> extends BindingImpl<T> {

  private Factory factory;

  public ExposedBindingImpl(InjectorImpl injector, Object source, Factory<T> factory) {
    super(injector, factory.key, source, factory, Scopes.NO_SCOPE, LoadStrategy.LAZY);
    this.factory = factory;
  }

  public <V> V acceptTargetVisitor(BindingTargetVisitor<? super T, V> visitor) {
    return visitor.visitExposed(factory.privateEnvironment);
  }

  public PrivateEnvironment getPrivateEnvironment() {
    return factory.privateEnvironment;
  }

  @Override public String toString() {
    return new ToStringBuilder(Binding.class)
        .add("key", key)
        .add("privateEnvironment", factory.privateEnvironment)
        .add("scope", scope)
        .add("source", source)
        .toString();
  }

  static class Factory<T> implements InternalFactory<T>, BindingProcessor.CreationListener {
    private final Key<T> key;
    private final PrivateEnvironment privateEnvironment;
    private BindingImpl<T> delegate;

    Factory(Key<T> key, PrivateEnvironment privateEnvironment) {
      this.key = key;
      this.privateEnvironment = privateEnvironment;
    }

    public void notify(Map<PrivateEnvironment, InjectorImpl> privateInjectors, Errors errors) {
      InjectorImpl privateInjector = privateInjectors.get(privateEnvironment);
      BindingImpl<T> explicitBinding = privateInjector.state.getExplicitBinding(key);

      if (explicitBinding.getInternalFactory() == this) {
        errors.withSource(explicitBinding.getSource()).exposedButNotBound(key);
        return;
      }

      this.delegate = explicitBinding;
    }

    public T get(Errors errors, InternalContext context, Dependency<?> dependency)
        throws ErrorsException {
      return delegate.getInternalFactory().get(errors, context, dependency);
    }
  }
}
