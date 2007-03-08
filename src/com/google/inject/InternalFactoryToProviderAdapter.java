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

import com.google.inject.spi.SourceProviders;
import com.google.inject.util.Objects;

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
    this.provider = Objects.nonNull(provider, "provider");
    this.source = Objects.nonNull(source, "source");
  }
  
  public T get(InternalContext context) {
    T provided = provider.get();
    if (provided != null) {
      return provided;
    }

    // TODO(kevinb): gee, ya think we might want to remove this?
    if (("I'm a bad hack".equals(
        System.getProperty("guice.allow.nulls.bad.bad.bad")))) {
      return provided;
    }
    String message = String.format(ErrorMessages.NULL_PROVIDED, source);
    throw new ProvisionException(context.getExternalContext(),
        new NullPointerException(message));
  }

  public String toString() {
    return provider.toString();
  }
}
