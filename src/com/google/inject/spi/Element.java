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
 * Immutable snapshot of a binding command.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public interface Element {
  Object getSource();
  <T> T acceptVisitor(Visitor<T> visitor);

  /**
   * Visit commands.
   */
  public interface Visitor<V> {
    V visitMessage(Message message);
    V visitBindInterceptor(BindInterceptor bindInterceptor);
    V visitBindScope(BindScope bindScope);
    V visitRequestInjection(RequestInjection requestInjection);
    V visitRequestStaticInjection(RequestStaticInjection requestStaticInjection);
    V visitConvertToTypes(ConvertToTypes convertToTypes);
    <T> V visitBinding(Binding<T> binding);
    <T> V visitGetProvider(GetProvider<T> getProvider);
  }
}
