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

package com.google.inject.spi;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.inject.internal.RehashableKeys.Keys.needsRehashing;
import static com.google.inject.internal.RehashableKeys.Keys.rehash;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.internal.RehashableKeys;
import com.google.inject.util.Types;

import java.util.Set;

/**
 * A lookup of the provider for a type. Lookups are created explicitly in a module using
 * {@link com.google.inject.Binder#getProvider(Class) getProvider()} statements:
 * <pre>
 *     Provider&lt;PaymentService&gt; paymentServiceProvider
 *         = getProvider(PaymentService.class);</pre>
 *
 * @author jessewilson@google.com (Jesse Wilson)
 * @since 2.0
 */
public final class ProviderLookup<T> implements Element {
  private final Object source;
  private Key<T> key;  // effectively final, as it will not change once it escapes into user code
  private Provider<T> delegate;
  private boolean rehashed = false;

  public ProviderLookup(Object source, Key<T> key) {
    this.source = checkNotNull(source, "source");
    this.key = checkNotNull(key, "key");
  }

  public Object getSource() {
    return source;
  }

  public Key<T> getKey() {
    return key;
  }

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

  public void applyTo(Binder binder) {
    initializeDelegate(binder.withSource(getSource()).getProvider(key));
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
      public T get() {
        checkState(delegate != null,
            "This Provider cannot be used until the Injector has been created.");
        return delegate.get();
      }
      
      public Set<Dependency<?>> getDependencies() {
        // If someone inside a Module is casting the Provider to HasDependencies
        // in order to find its dependencies, we give them nothing (because we can't
        // guarantee that the key is finalized yet).  However, if someone acts on
        // a ProviderLookup or ProviderInstanceBinding, it will properly find dependencies.
        checkState(rehashed, "Dependencies can not be retrieved until the Injector has been "
            + "created (or Elements.getElements finishes)");
        // We depend on Provider<T>, not T directly.  This is an important distinction
        // for dependency analysis tools that short-circuit on providers.
        Key<?> providerKey = key.ofType(Types.providerOf(key.getTypeLiteral().getType()));
        return ImmutableSet.<Dependency<?>>of(Dependency.get(providerKey));
      }

      @Override public String toString() {
        return "Provider<" + key.getTypeLiteral() + ">";
      }
    };
  }

  RehashableKeys getKeyRehasher() {
    return new RehashableKeys() {
      @Override public void rehashKeys() {
        rehashed = true;
        if (needsRehashing(key)) {
          key = rehash(key);
        }
      }
    };
  }
}
