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
 * A binding from a {@link Key} (type and name) to an implementation.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class Binding<T> {

  final ContainerImpl container;
  final Key<T> key;
  final Object source;
  final InternalFactory<? extends T> internalFactory;

  Binding(ContainerImpl container, Key<T> key,
      Object source, InternalFactory<? extends T> internalFactory) {
    this.container = container;
    this.key = key;
    this.source = source;
    this.internalFactory = internalFactory;
  }

  /**
   * Gets the key for this binding.
   */
  public Key<T> getKey() {
    return key;
  }

  /**
   * Gets the source object, an arbitrary object which points back to the
   * configuration which resulted in this binding.
   */
  public Object getSource() {
    return source;
  }

  volatile Factory<T> factory;

  /**
   * Gets the factory which returns instances of {@code T}.
   */
  public Factory<T> getFactory() {
    if (factory == null) {
      factory = container.getFactory(key);
    }
    return factory;
  }

  InternalFactory<? extends T> getInternalFactory() {
    return internalFactory;
  }

  static <T> Binding<T> newInstance(ContainerImpl container, Key<T> key,
      Object source, InternalFactory<? extends T> internalFactory) {
    return new Binding<T>(container, key, source, internalFactory);
  }

  /**
   * Is this a constant binding?
   */
  public boolean isConstant() {
    return internalFactory instanceof ConstantFactory<?>;
  }

  public String toString() {
    return new ToStringBuilder(Binding.class)
        .add("key", key)
        .add("source", source)
        .add("factory", internalFactory)
        .toString();
  }
}