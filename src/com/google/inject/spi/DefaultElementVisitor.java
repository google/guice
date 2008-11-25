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

import com.google.inject.Binding;

/**
 * No-op visitor for subclassing. All interface methods simply delegate to
 * {@link #visitElement(Element)}, returning its result.
 *
 * @param <V> any type to be returned by the visit method. Use {@link Void} with
 *     {@code return null} if no return type is needed.
 *
 * @author sberlin@gmail.com (Sam Berlin)
 * @since 2.0
 */
public abstract class DefaultElementVisitor<V> implements ElementVisitor<V> {

  /**
   * Visit {@code element} and return a result.
   */
  protected V visitElement(Element element) {
    return null;
  }

  public V visitMessage(Message message) {
    return visitElement(message);
  }

  public <T> V visitBinding(Binding<T> binding) {
    return visitElement(binding);
  }

  public V visitInterceptorBinding(InterceptorBinding interceptorBinding) {
    return visitElement(interceptorBinding);
  }

  public V visitScopeBinding(ScopeBinding scopeBinding) {
    return visitElement(scopeBinding);
  }

  public V visitTypeConverterBinding(TypeConverterBinding typeConverterBinding) {
    return visitElement(typeConverterBinding);
  }

  public <T> V visitProviderLookup(ProviderLookup<T> providerLookup) {
    return visitElement(providerLookup);
  }

  public V visitInjectionRequest(InjectionRequest injectionRequest) {
    return visitElement(injectionRequest);
  }

  public V visitStaticInjectionRequest(StaticInjectionRequest staticInjectionRequest) {
    return visitElement(staticInjectionRequest);
  }

  public V visitPrivateElements(PrivateEnvironment privateEnvironment) {
    return visitElement(privateEnvironment);
  }

  public V visitExposure(Exposure exposure) {
    return visitElement(exposure);
  }
}
