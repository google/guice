/**
 * Copyright (C) 2011 Google Inc.
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

import static com.google.inject.internal.util.Preconditions.checkNotNull;

import javax.inject.Provider;

import com.google.inject.Key;
import com.google.inject.spi.Dependency;

/**
 * Base class for InternalFactories that are used by Providers, to handle
 * circular dependencies.
 *
 * @author sameb@google.com (Sam Berlin)
 */
abstract class ProviderInternalFactory<T> implements InternalFactory<T> {
  
  private final Key<T> key;
  protected final Object source;
  private final boolean allowProxy;
  
  ProviderInternalFactory(Key<T> key, Object source, boolean allowProxy) {
    this.key = key;
    this.source = checkNotNull(source, "source");
    this.allowProxy = allowProxy;
  }
  
  protected T circularGet(Provider<? extends T> provider, Errors errors,
      InternalContext context, Dependency<?> dependency, boolean linked)
      throws ErrorsException {    
    Class<?> expectedType = dependency.getKey().getTypeLiteral().getRawType();
    
    // Use the Key we are providing for as a unique key to locate the context.
    // We cannot use dependency.getKey() because that is the Key of the type
    // we are trying to fulfill (which may be different among different
    // calls to us).  We also cannot use 'this', because the factory can
    // be recreated different times during @ProvidedBy creations.
    ConstructionContext<T> constructionContext = context.getConstructionContext(key);
    
    // We have a circular reference between constructors. Return a proxy.
    if (constructionContext.isConstructing()) {
      if (!allowProxy) {
        throw errors.circularProxiesDisabled(expectedType).toException();
      } else {
        // TODO: if we can't proxy this object, can we proxy the other object?
        @SuppressWarnings("unchecked")
        T proxyType = (T) constructionContext.createProxy(errors, expectedType);
        return proxyType;
      }
    }
    // First time through...
    constructionContext.startConstruction();
    try {
      T t = errors.checkForNull(provider.get(), source, dependency);
      constructionContext.setProxyDelegates(t);
      return t;
    } finally {
      constructionContext.finishConstruction();
    }
  }

}
