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
 * {@link #visitOther(Element)}, returning its result.
 *
 * @param <V> any type to be returned by the visit method. Use {@link Void} with
 *     {@code return null} if no return type is needed.
 *
 * @author sberlin@gmail.com (Sam Berlin)
 * @since 2.0
 */
public abstract class DefaultElementVisitor<V> implements ElementVisitor<V> {

  /**
   * Default visit implementation. Returns {@code null}.
   */
  protected V visitOther(Element element) {
    return null;
  }

  public V visit(Message message) {
    return visitMessage(message);
  }

  public <T> V visit(Binding<T> binding) {
    return visitBinding(binding);
  }

  /*if[AOP]*/
  public V visit(InterceptorBinding interceptorBinding) {
    return visitInterceptorBinding(interceptorBinding);
  }
  /*end[AOP]*/

  public V visit(ScopeBinding scopeBinding) {
    return visitScopeBinding(scopeBinding);
  }

  public V visit(TypeConverterBinding typeConverterBinding) {
    return visitTypeConverterBinding(typeConverterBinding);
  }

  public <T> V visit(ProviderLookup<T> providerLookup) {
    return visitProviderLookup(providerLookup);
  }

  public V visit(InjectionRequest injectionRequest) {
    return visitInjectionRequest(injectionRequest);
  }

  public V visit(StaticInjectionRequest staticInjectionRequest) {
    return visitStaticInjectionRequest(staticInjectionRequest);
  }

  public V visit(PrivateElements privateElements) {
    return visitPrivateElements(privateElements);
  }

  public <T> V visit(MembersInjectorLookup<T> lookup) {
    return visitOther(lookup);
  }

  public V visit(TypeListenerBinding binding) {
    return visitOther(binding);
  }

  /** @deprecated override {@code visit} instead. */
  protected V visitElement(Element element) {
    return visitOther(element);
  }

  /** @deprecated override {@code visit} instead. */
  public V visitMessage(Message message) {
    return visitElement(message);
  }

  /** @deprecated override {@code visit} instead. */
  public <T> V visitBinding(Binding<T> binding) {
    return visitElement(binding);
  }

  /*if[AOP]*/
  /** @deprecated override {@code visit} instead. */
  public V visitInterceptorBinding(InterceptorBinding interceptorBinding) {
    return visitElement(interceptorBinding);
  }
  /*end[AOP]*/

  /** @deprecated override {@code visit} instead. */
  public V visitScopeBinding(ScopeBinding scopeBinding) {
    return visitElement(scopeBinding);
  }

  /** @deprecated override {@code visit} instead. */
  public V visitTypeConverterBinding(TypeConverterBinding typeConverterBinding) {
    return visitElement(typeConverterBinding);
  }

  /** @deprecated override {@code visit} instead. */
  public <T> V visitProviderLookup(ProviderLookup<T> providerLookup) {
    return visitElement(providerLookup);
  }

  /** @deprecated override {@code visit} instead. */
  public V visitInjectionRequest(InjectionRequest injectionRequest) {
    return visitElement(injectionRequest);
  }

  /** @deprecated override {@code visit} instead. */
  public V visitStaticInjectionRequest(StaticInjectionRequest staticInjectionRequest) {
    return visitElement(staticInjectionRequest);
  }

  /** @deprecated override {@code visit} instead. */
  public V visitPrivateElements(PrivateElements privateElements) {
    return visitElement(privateElements);
  }
}
