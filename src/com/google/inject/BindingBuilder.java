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

import static com.google.inject.util.Objects.nonNull;
import com.google.inject.util.Objects;

import java.util.*;

/**
 * Builds a binding.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class BindingBuilder<T> {

  final SourceProvider sourceProvider;
  Key<T> key;
  Object source;
  InternalFactory<? extends T> factory;
  boolean eagerlyLoad = false;

  /**
   * Keeps error messages in order and prevents duplicates.
   */
  Set<ErrorMessage> errorMessages = new LinkedHashSet<ErrorMessage>();

  /**
   * Creates a new binding for the given key.
   */
  BindingBuilder(Key<T> key, SourceProvider sourceProvider) {
    this.key = nonNull(key, "key");
    this.sourceProvider = nonNull(sourceProvider, "sourceProvider");
  }

  /**
   * Specifies that this binding should load as soon as its scope loads. For
   * example, a singleton will load at startup.
   */
  public BindingBuilder<T> eagerlyLoad() {
    this.eagerlyLoad = true;
    return this;
  }

  /**
   * Sets the name of this dependency.
   */
  public BindingBuilder<T> named(String name) {
    if (!this.key.hasDefaultName()) {
      errorMessages.add(
          new ErrorMessage(source, "Name set more than once."));
    }

    this.key = this.key.rename(name);
    return this;
  }

  /**
   * Sets the implementation to the given class.
   */
  public <I extends T> BindingBuilder<T> to(Class<I> implementation) {
    ensureImplementationIsNotSet();
    this.factory = new DefaultFactory<I>(implementation);
    return this;
  }

  /**
   * Uses the given factory to create instances of the implementation.
   */
  public BindingBuilder<T> to(final Factory<? extends T> factory) {
    ensureImplementationIsNotSet();

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

  /**
   * Sets the implementation to the given class.
   */
  public BindingBuilder<T> to(Key<? extends T> implementation) {
    ensureImplementationIsNotSet();
//    this.factory = new DefaultFactory<I>(implementation);
    return this;
  }

  private void ensureImplementationIsNotSet() {
    if (factory != null) {
      errorMessages.add(
          new ErrorMessage(source, "Implementation set more than once."));
    }
  }

  /**
   * Specifies the scope.
   */
  public BindingBuilder<T> in(Scope scope) {
    if (scope != null) {
      errorMessages.add(
          new ErrorMessage(source, "Scope set more than once."));
    }

    return this;
  }

  /**
   * Sets the source object. Useful for debugging. Contents may include the
   * name of the file and the line number this binding came from, a code
   * snippet, etc.
   */
  public BindingBuilder<T> from(Object source) {
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
