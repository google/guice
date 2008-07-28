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
 * @author sberlin@gmail.com (Sam Berlin)
 */
public class DefaultElementVisitor<V> implements Element.Visitor<V> {

  protected DefaultElementVisitor() {}

  /**
   * Visit {@code element} and return a result.
   */
  protected V visitElement(Element element) {
    return null;
  }

  public V visitMessage(Message message) {
    return visitElement(message);
  }

  public <T> V visitBinding(Binding<T> command) {
    return visitElement(command);
  }

  public V visitBindConstant(BindConstant command) {
    return visitElement(command);
  }

  public V visitBindInterceptor(BindInterceptor command) {
    return visitElement(command);
  }

  public V visitBindScope(BindScope command) {
    return visitElement(command);
  }

  public V visitConvertToTypes(ConvertToTypes command) {
    return visitElement(command);
  }

  public <T> V visitGetProvider(GetProvider<T> command) {
    return visitElement(command);
  }

  public V visitRequestInjection(RequestInjection command) {
    return visitElement(command);
  }

  public V visitRequestStaticInjection(RequestStaticInjection command) {
    return visitElement(command);
  }
}
