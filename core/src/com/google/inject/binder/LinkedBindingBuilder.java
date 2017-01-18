/*
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

package com.google.inject.binder;

import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import java.lang.reflect.Constructor;

/**
 * See the EDSL examples at {@link com.google.inject.Binder}.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public interface LinkedBindingBuilder<T> extends ScopedBindingBuilder {

  /** See the EDSL examples at {@link com.google.inject.Binder}. */
  ScopedBindingBuilder to(Class<? extends T> implementation);

  /** See the EDSL examples at {@link com.google.inject.Binder}. */
  ScopedBindingBuilder to(TypeLiteral<? extends T> implementation);

  /** See the EDSL examples at {@link com.google.inject.Binder}. */
  ScopedBindingBuilder to(Key<? extends T> targetKey);

  /**
   * See the EDSL examples at {@link com.google.inject.Binder}.
   *
   * @see com.google.inject.Injector#injectMembers
   */
  void toInstance(T instance);

  /**
   * See the EDSL examples at {@link com.google.inject.Binder}.
   *
   * @see com.google.inject.Injector#injectMembers
   */
  ScopedBindingBuilder toProvider(Provider<? extends T> provider);

  /**
   * See the EDSL examples at {@link com.google.inject.Binder}.
   *
   * @see com.google.inject.Injector#injectMembers
   * @since 4.0
   */
  ScopedBindingBuilder toProvider(javax.inject.Provider<? extends T> provider);

  /** See the EDSL examples at {@link com.google.inject.Binder}. */
  ScopedBindingBuilder toProvider(Class<? extends javax.inject.Provider<? extends T>> providerType);

  /** See the EDSL examples at {@link com.google.inject.Binder}. */
  ScopedBindingBuilder toProvider(
      TypeLiteral<? extends javax.inject.Provider<? extends T>> providerType);

  /** See the EDSL examples at {@link com.google.inject.Binder}. */
  ScopedBindingBuilder toProvider(Key<? extends javax.inject.Provider<? extends T>> providerKey);

  /**
   * See the EDSL examples at {@link com.google.inject.Binder}.
   *
   * @since 3.0
   */
  <S extends T> ScopedBindingBuilder toConstructor(Constructor<S> constructor);

  /**
   * See the EDSL examples at {@link com.google.inject.Binder}.
   *
   * @since 3.0
   */
  <S extends T> ScopedBindingBuilder toConstructor(
      Constructor<S> constructor, TypeLiteral<? extends S> type);
}
