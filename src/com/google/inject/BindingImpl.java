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

import com.google.inject.util.ToStringBuilder;

/**
 * @author crazybob@google.com (Bob Lee)
 */
class BindingImpl<T> implements Binding<T> {

  final ContainerImpl container;
  final Key<T> key;
  final Object source;
  final InternalFactory<? extends T> internalFactory;

  BindingImpl(ContainerImpl container, Key<T> key, Object source,
      InternalFactory<? extends T> internalFactory) {
    this.container = container;
    this.key = key;
    this.source = source;
    this.internalFactory = internalFactory;
  }

  public Key<T> getKey() {
    return key;
  }

  public Object getSource() {
    return source;
  }

  volatile Provider<T> provider;

  public Provider<T> getProvider() {
    if (provider == null) {
      provider = container.getProvider(key);
    }
    return provider;
  }

  InternalFactory<? extends T> getInternalFactory() {
    return internalFactory;
  }

  static <T> BindingImpl<T> newInstance(ContainerImpl container, Key<T> key,
      Object source, InternalFactory<? extends T> internalFactory) {
    return new BindingImpl<T>(container, key, source, internalFactory);
  }

  /**
   * Is this a constant binding?
   */
  boolean isConstant() {
    return internalFactory instanceof ConstantFactory<?>;
  }

  public String toString() {
    return new ToStringBuilder(BindingImpl.class)
        .add("key", key)
        .add("source", source)
        .add("provider", internalFactory)
        .toString();
  }
}