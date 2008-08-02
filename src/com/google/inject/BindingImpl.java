/**
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

package com.google.inject;

import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;
import com.google.inject.internal.ToStringBuilder;
import com.google.inject.spi.BindScopingVisitor;
import com.google.inject.spi.Element;
import com.google.inject.spi.oldversion.OldVersionBinding;
import com.google.inject.spi.oldversion.ProviderBinding;

/**
 * @author crazybob@google.com (Bob Lee)
 */
abstract class BindingImpl<T> implements OldVersionBinding<T> {

  final InjectorImpl injector;
  final Key<T> key;
  final Object source;
  final InternalFactory<? extends T> internalFactory;
  final Scope scope;
  final LoadStrategy loadStrategy;

  BindingImpl(InjectorImpl injector, Key<T> key, Object source,
      InternalFactory<? extends T> internalFactory, Scope scope, LoadStrategy loadStrategy) {
    this.injector = injector;
    this.key = key;
    this.source = source;
    this.internalFactory = internalFactory;
    this.scope = scope;
    this.loadStrategy = loadStrategy;
  }

  public Key<T> getKey() {
    return key;
  }

  public Object getSource() {
    return source;
  }

  volatile Provider<T> provider;

  public Provider<T> getProvider() {
    if (provider == null) {
      provider = injector.getProvider(key);
    }
    return provider;
  }

  @SuppressWarnings("unchecked")
  public ProviderBinding<T> getProviderBinding() {
    // ProviderBinding is the only type of binding that can be generated for
    // a Provider<T>.
    return (ProviderBinding<T>) injector.getBinding(key.providerKey());
  }

  InternalFactory<? extends T> getInternalFactory() {
    return internalFactory;
  }

  public Scope getScope() {
    return this.scope;
  }

  /**
   * Is this a constant binding? This returns true for constant bindings as
   * well as toInstance() bindings.
   */
  boolean isConstant() {
    return internalFactory instanceof ConstantFactory<?>;
  }

  LoadStrategy getLoadStrategy() {
    return loadStrategy;
  }

  public <V> V acceptVisitor(Element.Visitor<V> visitor) {
    return visitor.visitBinding(this);
  }

  public <V> V acceptScopingVisitor(BindScopingVisitor<V> visitor) {
    if (loadStrategy == LoadStrategy.EAGER) {
      return visitor.visitEagerSingleton();
    } else if (scope != Scopes.NO_SCOPE && scope != null) {
      return visitor.visitScope(scope);
    } else {
      return visitor.visitNoScoping();
    }
  }

  /**
   * Perform any post-creation initialization, that could require construction
   * of other bindings.
   */
  void initialize(InjectorImpl injector, Errors errors) throws ErrorsException {}

  public String toString() {
    return new ToStringBuilder(Binding.class)
        .add("key", key)
        .add("provider", internalFactory)
        .add("scope", scope)
        .add("source", source)
        .toString();
  }
}