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

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a binding from a type and optional name to a given implementation
 * in a given scope. Uses the given type as the implementation by default.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class BindingBuilder<T> {

  final Key<T> key;
  String source;
  InternalFactory<? extends T> factory;
  Factory<? extends T> externalFactory;
  final List<Key<? super T>> exportKeys = new ArrayList<Key<? super T>>();

  /**
   * Creates a new binding for the given key.
   */
  public BindingBuilder(Key<T> key) {
    this.key = key;
  }

  /**
   * Exports this binding.
   */
  public BindingBuilder exportBinding() {
    return exportBinding(this.key);
  }

  /**
   * Exports this binding with the given key.
   */
  public BindingBuilder exportBinding(Key<? super T> exportKey) {
    this.exportKeys.add(exportKey);
    return this;
  }

  /**
   * Sets the implementation to the given class.
   */
  public <I extends T> BindingBuilder implementation(
      final Class<I> implementation) {
    ensureImplementationIsNotSet();
    this.factory = new DefaultFactory<I>(implementation);
    return this;
  }

  /**
   * Uses the given factory to create instances of the implementation.
   */
  public BindingBuilder factory(final Factory<? extends T> factory) {
    ensureImplementationIsNotSet();

    this.externalFactory = factory;

    this.factory = new InternalFactory<T>() {
      public T create(InternalContext context) {
        try {
          Context externalContext = context.getExternalContext();
          return factory.create(externalContext);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }

      public String toString() {
        return factory.toString();
      }
    };

    return this;
  }

  private <I extends T> void ensureImplementationIsNotSet() {
    if (factory != null) {
      throw new IllegalStateException("An implementation is already set.");
    }
  }

  /**
   * Specifies the scope.
   */
  public BindingBuilder scope(Scope scope) {
    if (scope != null) {
      throw new IllegalStateException("Scope is already set.");
    }

    return this;
  }

  /**
   * Sets the source string. Useful for debugging. Contents may include the
   * name of the file and the line number this binding came from, a code
   * snippet, etc.
   */
  public BindingBuilder source(String source) {
    if (source != null) {
      throw new IllegalStateException("Source is already set.");
    }

    this.source = source;
    return this;
  }

  /**
   * Injects new instances of the specified implementation class.
   */
  private class DefaultFactory<I extends T> implements InternalFactory<I> {

    volatile ContainerImpl.ConstructorInjector<I> constructor;

    private final Class<I> implementation;

    public DefaultFactory(Class<I> implementation) {
      this.implementation = implementation;
    }

    @SuppressWarnings("unchecked")
      public I create(InternalContext context) {
      if (constructor == null) {
        this.constructor =
            context.getContainerImpl().getConstructor(implementation);
      }
      return (I) constructor.construct(context, key.getRawType());
    }

    public String toString() {
      return implementation.toString();
    }
  }
}
