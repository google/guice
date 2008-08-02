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
 * A core component of a module or injector.
 *
 * <p>The elements of a module can be inspected, validated and rewritten. Use {@link
 * Elements#getElements(com.google.inject.Module[])} to read the elements from a module, and
 * {@link com.google.inject.spi.ModuleWriter} to rewrite them. This can be used for static analysis
 * and generation of Guice modules.
 *
 * <p>The elements of an injector can be inspected and exercised. Use {@link
 * com.google.inject.Injector#getBindings} to reflect on Guice injectors.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public interface Element {

  /**
   * Returns an arbitrary object containing information about the "place" where this element was
   * configured. Used by Guice in the production of descriptive error messages.
   *
   * <p>Tools might specially handle types they know about; {@code StackTraceElement} is a good
   * example. Tools should simply call {@code toString()} on the source object if the type is
   * unfamiliar.
   */
  Object getSource();

  /**
   * Accepts an element visitor. Invokes the visitor method specific to this element's type.
   *
   * @param visitor to call back on
   */
  <T> T acceptVisitor(Visitor<T> visitor);

  /**
   * Visit elements.
   *
   * @param <V> any type to be returned by the visit method. Use {@link Void} with
   *     {@code return null} if no return type is needed.
   */
  public interface Visitor<V> {

    /**
     * Visit a mapping from a key (type and optional annotation) to the strategy for getting
     * instances of the type.
     */
    <T> V visitBinding(Binding<T> binding);

    /**
     * Visit a registration of interceptors for matching methods of matching classes.
     */
    V visitInterceptorBinding(InterceptorBinding interceptorBinding);

    /**
     * Visit a registration of a scope annotation with the scope that implements it.
     */
    V visitScopeBinding(ScopeBinding scopeBinding);

    /**
     * Visit a registration of type converters for matching target types.
     */
    V visitTypeConverterBinding(TypeConverterBinding typeConverterBinding);

    /**
     * Visit a request to inject the instance fields and methods of an instance.
     */
    V visitInjectionRequest(InjectionRequest injectionRequest);

    /**
     * Visit a request to inject the static fields and methods of type.
     */
    V visitStaticInjectionRequest(StaticInjectionRequest staticInjectionRequest);

    /**
     * Visit a lookup of the provider for a type.
     */
    <T> V visitProviderLookup(ProviderLookup<T> providerLookup);

    /**
     * Visit an error message and the context in which it occured.
     */
    V visitMessage(Message message);
  }
}
