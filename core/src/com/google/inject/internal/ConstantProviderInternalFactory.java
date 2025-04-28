/*
 * Copyright (C) 2025 Google Inc.
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.inject.spi.Dependency;
import javax.annotation.Nullable;
import jakarta.inject.Provider;

/** An InternalFactory that delegates to a constant provider. */
final class ConstantProviderInternalFactory<T> extends ProviderInternalFactory<T> {
  private final Provider<T> provider;
  @Nullable private final ProvisionListenerStackCallback<T> provisionCallback;

  ConstantProviderInternalFactory(
      Class<? super T> rawType,
      Provider<T> provider,
      Object source,
      @Nullable ProvisionListenerStackCallback<T> provisionCallback,
      int circularFactoryId) {
    super(rawType, source, circularFactoryId);
    this.provider = checkNotNull(provider);
    this.provisionCallback = provisionCallback;
  }

  @Override
  public T get(InternalContext context, Dependency<?> dependency, boolean linked)
      throws InternalProvisionException {
    return circularGet(provider, context, dependency, provisionCallback);
  }

  @Override
  MethodHandleResult makeHandle(LinkageContext context, boolean linked) {
    return makeCachable(
        circularGetHandleImmediate(
            InternalMethodHandles.constantFactoryGetHandle(provider), provisionCallback));
  }
}
