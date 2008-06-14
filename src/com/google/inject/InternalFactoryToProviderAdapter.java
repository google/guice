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

import static com.google.common.base.Preconditions.checkNotNull;
import com.google.inject.internal.ErrorMessage;
import com.google.inject.spi.SourceProviders;

/**
 * @author crazybob@google.com (Bob Lee)
*/
class InternalFactoryToProviderAdapter<T> implements InternalFactory<T> {

  private final Provider<? extends T> provider;
  private final Object source;

  public InternalFactoryToProviderAdapter(Provider<? extends T> provider) {
    this(provider, SourceProviders.UNKNOWN_SOURCE);
  }

  public InternalFactoryToProviderAdapter(
      Provider<? extends T> provider, Object source) {
    this.provider = checkNotNull(provider, "provider");
    this.source = checkNotNull(source, "source");
  }

  public T get(InternalContext context, InjectionPoint<?> injectionPoint) {
    context.ensureMemberInjected(provider);
    try {
      return injectionPoint.checkForNull(provider.get(), source);
    } catch(ProvisionException e) {
      throw e;
    } catch(RuntimeException e) {
      throw new ProvisionException(ErrorMessage.errorInProvider().toString(), e);
    }
  }

  public String toString() {
    return provider.toString();
  }
}
