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

package com.google.inject.spi;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.internal.Errors;
import com.google.inject.util.Types;
import java.util.Set;

/**
 * A lookup of the provider for a type. Lookups are created explicitly in a module using {@link
 * com.google.inject.Binder#getProvider(Class) getProvider()} statements:
 *
 * <pre>
 *     Provider&lt;PaymentService&gt; paymentServiceProvider
 *         = getProvider(PaymentService.class);</pre>
 *
 * @author jessewilson@google.com (Jesse Wilson)
 * @since 2.0
 */
public final class ProviderLookup<T> implements Element {
  private final Object source;
  private final Dependency<T> dependency;
  private Provider<T> delegate;

  public ProviderLookup(Object source, Key<T> key) {
    this(source, Dependency.get(checkNotNull(key, "key")));
  }

  /** @since 4.0 */
  public ProviderLookup(Object source, Dependency<T> dependency) {
    this.source = checkNotNull(source, "source");
    this.dependency = checkNotNull(dependency, "dependency");
  }

  @Override
  public Object getSource() {
    return source;
  }

  public Key<T> getKey() {
    return dependency.getKey();
  }

  /** @since 4.0 */
  public Dependency<T> getDependency() {
    return dependency;
  }

  @Override
  public <T> T acceptVisitor(ElementVisitor<T> visitor) {
    return visitor.visit(this);
  }

  /**
   * Sets the actual provider.
   *
   * @throws IllegalStateException if the delegate is already set
   */
  public void initializeDelegate(Provider<T> delegate) {
    checkState(this.delegate == null, "delegate already initialized");
    this.delegate = checkNotNull(delegate, "delegate");
  }

  @Override
  public void applyTo(Binder binder) {
    initializeDelegate(binder.withSource(getSource()).getProvider(dependency));
  }

  /**
   * Returns the delegate provider, or {@code null} if it has not yet been initialized. The delegate
   * will be initialized when this element is processed, or otherwise used to create an injector.
   */
  public Provider<T> getDelegate() {
    return delegate;
  }

  /**
   * Returns the looked up provider. The result is not valid until this lookup has been initialized,
   * which usually happens when the injector is created. The provider will throw an {@code
   * IllegalStateException} if you try to use it beforehand.
   */
  public Provider<T> getProvider() {
    return new ProviderWithDependencies<T>() {
      @Override
      public T get() {
        Provider<T> local = delegate;
        if (local == null) {
          throw new IllegalStateException(
              "This Provider cannot be used until the Injector has been created.");
        }
        return local.get();
      }

      @Override
      public Set<Dependency<?>> getDependencies() {
        // We depend on Provider<T>, not T directly.  This is an important distinction
        // for dependency analysis tools that short-circuit on providers.
        Key<?> providerKey = getKey().ofType(Types.providerOf(getKey().getTypeLiteral().getType()));
        return ImmutableSet.<Dependency<?>>of(Dependency.get(providerKey));
      }

      @Override
      public String toString() {
        return "Provider<" + getKey().getTypeLiteral() + ">";
      }
    };
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(ProviderLookup.class)
        .add("dependency", dependency)
        .add("source", Errors.convert(source))
        .toString();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof ProviderLookup
        && ((ProviderLookup<?>) obj).dependency.equals(dependency)
        && ((ProviderLookup<?>) obj).source.equals(source);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(dependency, source);
  }
}
