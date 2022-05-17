/*
 * Copyright (C) 2022 Google Inc.
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

import static com.google.inject.internal.GuiceInternal.GUICE_INTERNAL;
import static com.google.inject.spi.Elements.withTrustedSource;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.spi.BindingTargetVisitor;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.HasDependencies;
import com.google.inject.spi.JeeProviderKeyBinding;
import com.google.inject.spi.ProviderKeyBinding;
import java.util.Set;

final class LinkedJeeProviderBindingImpl<T> extends BindingImpl<T>
    implements JeeProviderKeyBinding<T>, HasDependencies, DelayedInitialize {

  final Key<? extends jakarta.inject.Provider<? extends T>> providerKey;
  final DelayedInitialize delayedInitializer;

  private LinkedJeeProviderBindingImpl(
      InjectorImpl injector,
      Key<T> key,
      Object source,
      InternalFactory<? extends T> internalFactory,
      Scoping scoping,
      Key<? extends jakarta.inject.Provider<? extends T>> providerKey,
      DelayedInitialize delayedInitializer) {
    super(injector, key, source, internalFactory, scoping);
    this.providerKey = providerKey;
    this.delayedInitializer = delayedInitializer;
  }

  public LinkedJeeProviderBindingImpl(
      InjectorImpl injector,
      Key<T> key,
      Object source,
      InternalFactory<? extends T> internalFactory,
      Scoping scoping,
      Key<? extends jakarta.inject.Provider<? extends T>> providerKey) {
    this(injector, key, source, internalFactory, scoping, providerKey, null);
  }

  LinkedJeeProviderBindingImpl(
      Object source,
      Key<T> key,
      Scoping scoping,
      Key<? extends jakarta.inject.Provider<? extends T>> providerKey) {
    super(source, key, scoping);
    this.providerKey = providerKey;
    this.delayedInitializer = null;
  }

  static <T> LinkedJeeProviderBindingImpl<T> createWithInitializer(
      InjectorImpl injector,
      Key<T> key,
      Object source,
      InternalFactory<? extends T> internalFactory,
      Scoping scoping,
      Key<? extends jakarta.inject.Provider<? extends T>> providerKey,
      DelayedInitialize delayedInitializer) {
    return new LinkedJeeProviderBindingImpl<T>(
        injector, key, source, internalFactory, scoping, providerKey, delayedInitializer);
  }

  @Override
  public <V> V acceptTargetVisitor(BindingTargetVisitor<? super T, V> visitor) {
    return visitor.visit(this);
  }

  @Override
  public Key<? extends jakarta.inject.Provider<? extends T>> getProviderKey() {
    return providerKey;
  }

  @Override
  public void initialize(InjectorImpl injector, Errors errors) throws ErrorsException {
    if (delayedInitializer != null) {
      delayedInitializer.initialize(injector, errors);
    }
  }

  @Override
  public Set<Dependency<?>> getDependencies() {
    return ImmutableSet.<Dependency<?>>of(Dependency.get(providerKey));
  }

  @Override
  public BindingImpl<T> withScoping(Scoping scoping) {
    return new LinkedJeeProviderBindingImpl<T>(getSource(), getKey(), scoping, providerKey);
  }

  @Override
  public BindingImpl<T> withKey(Key<T> key) {
    return new LinkedJeeProviderBindingImpl<T>(getSource(), key, getScoping(), providerKey);
  }

  @Override
  public void applyTo(Binder binder) {
    getScoping()
        .applyTo(
            withTrustedSource(GUICE_INTERNAL, binder, getSource())
                .bind(getKey())
                .toJeeProvider(getProviderKey()));
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(JeeProviderKeyBinding.class)
        .add("key", getKey())
        .add("source", getSource())
        .add("scope", getScoping())
        .add("provider", providerKey)
        .toString();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof LinkedJeeProviderBindingImpl) {
      LinkedJeeProviderBindingImpl<?> o = (LinkedJeeProviderBindingImpl<?>) obj;
      return getKey().equals(o.getKey())
          && getScoping().equals(o.getScoping())
          && Objects.equal(providerKey, o.providerKey);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getKey(), getScoping(), providerKey);
  }
}
