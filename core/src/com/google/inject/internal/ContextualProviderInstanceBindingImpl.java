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

package com.google.inject.internal;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import com.google.inject.ConfigurationException;
import com.google.inject.ContextualProvider;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.spi.BindingTargetVisitor;
import com.google.inject.spi.ContextualProviderInstanceBinding;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.HasDependencies;
import com.google.inject.spi.InjectionContext;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.ProviderInstanceBinding;
import java.util.Set;

import static com.google.inject.internal.GuiceInternal.GUICE_INTERNAL;
import static com.google.inject.spi.Elements.withTrustedSource;

class ContextualProviderInstanceBindingImpl<T> extends BindingImpl<T> implements ContextualProviderInstanceBinding<T> {

  final ContextualProvider<? extends T> userSupplierProvider;
  final InternalProvider<? extends T> internalProvider;
  final ImmutableSet<InjectionPoint> injectionPoints;

  public static <T> ContextualProviderInstanceBindingImpl<T> create(
      Key<T> key,
      Object source,
      Scoping scoping,
      ContextualProvider<? extends T> userSupplierProvider,
      Set<InjectionPoint> injectionPoints) {
    InternalProvider<? extends T> internalProvider
        = new InternalProvider<T>(key, userSupplierProvider);
    return new ContextualProviderInstanceBindingImpl<>(
        key,
        source,
        scoping,
        internalProvider,
        userSupplierProvider,
        internalProvider,
        injectionPoints
    );
  }

  private ContextualProviderInstanceBindingImpl(
      Key<T> key,
      Object source,
      Scoping scoping,
      ContextualProvider<? extends T> userSupplierProvider,
      InternalProvider<? extends T> internalProvider,
      Set<InjectionPoint> injectionPoints) {
    this(key,
        source,
        scoping,
        new InternalProvider<T>(key, userSupplierProvider),
        userSupplierProvider,
        internalProvider,
        injectionPoints);
  }

  public ContextualProviderInstanceBindingImpl(
      Key<T> key,
      Object source,
      Scoping scoping,
      InternalFactory<? extends T> internalFactory,
      ContextualProvider<? extends T> userSupplierProvider,
      InternalProvider<? extends T> internalProvider,
      Set<InjectionPoint> injectionPoints) {
    super(null, key, source, internalFactory, scoping);
    this.userSupplierProvider = userSupplierProvider;
    this.internalProvider = internalProvider;
    this.injectionPoints = ImmutableSet.copyOf(injectionPoints);
  }

  static class InternalProvider<T> implements InternalFactory<T>, Provider<T> {
    final Key<?> key;
    final ContextualProvider<? extends T> provider;

    InternalProvider(final Key<?> key, final ContextualProvider<? extends T> provider) {
      this.key = key;
      this.provider = provider;
    }

    @Override
    public T get(InternalContext context, Dependency<?> dependency, boolean linked) {
      InjectionContext injectionContext = InjectionContext.fromDependency(dependency);
      return injectionContext == null
          ? get() // without context
          : provider.get(injectionContext);
    }

    @Override
    public T get() {
      if (provider instanceof jakarta.inject.Provider) {
        @SuppressWarnings("unchecked") // safe cast
        jakarta.inject.Provider<T> providerOfT = (jakarta.inject.Provider<T>) provider;
        return providerOfT.get();
      } else if (provider instanceof javax.inject.Provider) {
        @SuppressWarnings("unchecked") // safe cast
        javax.inject.Provider<T> providerOfT = (javax.inject.Provider<T>) provider;
        return providerOfT.get();
      }
      Errors errors = new Errors();
      errors.contextualProvideWithoutContext(key);
      throw new ConfigurationException(errors.getMessages());
    }

    @Override
    public String toString() {
      return "ContextualProviderOf[" + provider + "]";
    }
  }

  @Override
  public <V> V acceptTargetVisitor(BindingTargetVisitor<? super T, V> visitor) {
    return visitor.visit(this);
  }

  @Override
  public ContextualProvider<? extends T> getUserSuppliedProvider() {
    return userSupplierProvider;
  }

  public InternalProvider<? extends T> getInternalProvider() {
    return internalProvider;
  }

  @Override
  public Set<InjectionPoint> getInjectionPoints() {
    return injectionPoints;
  }

  @Override
  public Set<Dependency<?>> getDependencies() {
    return userSupplierProvider instanceof HasDependencies
        ? ImmutableSet.copyOf(((HasDependencies) userSupplierProvider).getDependencies())
        : Dependency.forInjectionPoints(injectionPoints);
  }

  @Override
  public Provider<T> getProvider() {
    @SuppressWarnings("unchecked")
    Provider<T> providerAsT = (Provider<T>) internalProvider;
    return providerAsT;
  }

  @Override
  public BindingImpl<T> withScoping(Scoping scoping) {
    return new ContextualProviderInstanceBindingImpl<T>(
        getKey(), getSource(), scoping, userSupplierProvider, internalProvider, injectionPoints);
  }

  @Override
  public BindingImpl<T> withKey(Key<T> key) {
    return new ContextualProviderInstanceBindingImpl<T>(
        key, getSource(), getScoping(), userSupplierProvider, internalProvider, injectionPoints);
  }

  @Override
  public void applyTo(Binder binder) {
    getScoping()
        .applyTo(
            withTrustedSource(GUICE_INTERNAL, binder, getSource())
                .bind(getKey())
                .toContextualProvider(getUserSuppliedProvider()));
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(ProviderInstanceBinding.class)
        .add("key", getKey())
        .add("source", getSource())
        .add("scope", getScoping())
        .add("provider", userSupplierProvider)
        .toString();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof ContextualProviderInstanceBindingImpl) {
      ContextualProviderInstanceBindingImpl<?> o = (ContextualProviderInstanceBindingImpl<?>) obj;
      return getKey().equals(o.getKey())
          && getScoping().equals(o.getScoping())
          && Objects.equal(userSupplierProvider, o.userSupplierProvider);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getKey(), getScoping());
  }
}
