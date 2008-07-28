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

package com.google.inject;

import com.google.inject.spi.Element;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;

/**
 * A mapping from a key (type and optional annotation) to a provider of
 * instances of that type.  This interface is part of the {@link Injector}
 * introspection API and is intended primary for use by tools.
 *
 * @author crazybob@google.com (Bob Lee)
 * @author jessewilson@google.com (Jesse Wilson)
 */
public interface Binding<T> extends Element {

  /**
   * Returns the key for this binding.
   */
  Key<T> getKey();

  /**
   * Returns an arbitrary object containing information about the "place"
   * where this binding was configured. Used by Guice in the production of
   * descriptive error messages.
   *
   * <p>Tools might specially handle types they know about;
   * {@code StackTraceElement} is a good example. Tools should simply call
   * {@code toString()} on the source object if the type is unfamiliar.
   */
  Object getSource();

  /**
   * Returns the scoped provider guice uses to fulfill requests for this
   * binding.
   *
   * @throws UnsupportedOperationException when invoked on a {@link Binding}
   *      created via {@link com.google.inject.spi.Elements#getElements}. This
   *      method is only supported on {@link Binding}s returned from an injector.
   */
  Provider<T> getProvider();

  <V> V acceptVisitor(Visitor<V> visitor);

  <V> V acceptTargetVisitor(TargetVisitor<? super T, V> visitor);

  <V> V acceptScopingVisitor(ScopingVisitor<V> visitor);

  interface TargetVisitor<T, V> {
    V visitToInstance(T instance);
    V visitToProvider(Provider<? extends T> provider);
    V visitToProviderKey(Key<? extends Provider<? extends T>> providerKey);
    V visitToKey(Key<? extends T> key);

    // module-only bindings
    V visitUntargetted();

    // injector-only bindings
    V visitConstructor(Constructor<? extends T> constructor);
    V visitConstant(T value);
    V visitConvertedConstant(T value);
    V visitProviderBinding(Key<?> provided);
  }

  interface ScopingVisitor<V> {
    V visitEagerSingleton();
    V visitScope(Scope scope);
    V visitScopeAnnotation(Class<? extends Annotation> scopeAnnotation);
    V visitNoScoping();
  }
}
