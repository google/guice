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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import java.lang.reflect.Constructor;
import org.jspecify.annotations.Nullable;

/**
 * See the EDSL examples at {@link com.google.inject.Binder}.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public interface LinkedBindingBuilder<T> extends ScopedBindingBuilder {

  /** See the EDSL examples at {@link com.google.inject.Binder}. */
  @CanIgnoreReturnValue
  ScopedBindingBuilder to(Class<? extends T> implementation);

  /** See the EDSL examples at {@link com.google.inject.Binder}. */
  @CanIgnoreReturnValue
  ScopedBindingBuilder to(TypeLiteral<? extends T> implementation);

  /** See the EDSL examples at {@link com.google.inject.Binder}. */
  @CanIgnoreReturnValue
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
  @CanIgnoreReturnValue
  ScopedBindingBuilder toProvider(Provider<? extends @Nullable T> provider);

  /**
   * See the EDSL examples at {@link com.google.inject.Binder}.
   *
   * @see com.google.inject.Injector#injectMembers
   * @since 4.0
   */
  @CanIgnoreReturnValue
  ScopedBindingBuilder toProvider(jakarta.inject.Provider<? extends @Nullable T> provider);

  /** See the EDSL examples at {@link com.google.inject.Binder}. */
  @CanIgnoreReturnValue
  ScopedBindingBuilder toProvider(
      Class<? extends jakarta.inject.Provider<? extends @Nullable T>> providerType);

  /** See the EDSL examples at {@link com.google.inject.Binder}. */
  @CanIgnoreReturnValue
  ScopedBindingBuilder toProvider(
      TypeLiteral<? extends jakarta.inject.Provider<? extends @Nullable T>> providerType);

  /** See the EDSL examples at {@link com.google.inject.Binder}. */
  @CanIgnoreReturnValue
  ScopedBindingBuilder toProvider(
      Key<? extends jakarta.inject.Provider<? extends @Nullable T>> providerKey);

  /**
   * See the EDSL examples at {@link com.google.inject.Binder}.
   *
   * @since 3.0
   */
  @CanIgnoreReturnValue
  <S extends T> ScopedBindingBuilder toConstructor(Constructor<S> constructor);

  /**
   * See the EDSL examples at {@link com.google.inject.Binder}.
   *
   * @since 3.0
   */
  @CanIgnoreReturnValue
  <S extends T> ScopedBindingBuilder toConstructor(
      Constructor<S> constructor, TypeLiteral<? extends S> type);
}
