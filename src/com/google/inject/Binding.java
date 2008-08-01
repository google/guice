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
 * A mapping from a key (type and optional annotation) to the strategy for getting instances of the
 * type. This interface is part of the introspection API and is intended primarily for use by 
 * tools.
 *
 * <p>Bindings are created in several ways:
 * <ul>
 *     <li>Explicitly in a module, via {@code bind()} and {@code bindConstant()}
 *         statements:
 * <pre>
 *     bind(Service.class).annotatedWith(Red.class).to(ServiceImpl.class);
 *     bindConstant().annotatedWith(ServerHost.class).to(args[0]);</pre></li>
 *     <li>Implicitly by the Injector by following a type's {@link ImplementedBy
 *         pointer} {@link ProvidedBy annotations} or by using its {@link Inject annotated} or
 *         default constructor.</li>
 *     <li>By converting a bound instance to a different type.</li>
 *     <li>For {@link Provider providers}, by delegating to the binding for the provided type.</li>
 * </ul>
 *
 *
 * <p>They exist on both modules and on injectors, and their behaviour is different for each:
 * <ul>
 *     <li><strong>Module bindings</strong> are incomplete and cannot be used to provide instances.
 *         This is because the applicable scopes and interceptors may not be known until an injector
 *         is created. From a tool's perspective, module bindings are like the injector's source
 *         code. They can be inspected or rewritten, but this analysis must be done statically.</li>
 *     <li><strong>Injector bindings</strong> are complete and valid and can be used to provide
 *         instances. From a tools' perspective, injector bindings are like reflection for an
 *         injector. They have full runtime information, including the complete graph of injections
 *         necessary to satisfy a binding.</li>
 * </ul>
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

  /**
   * A strategy to find an instance that satisfies an injection.
   */
  interface TargetVisitor<T, V> {

    /**
     * Visit a instance binding. The same instance is returned for every injection. This target is
     * used in both module and injector bindings.
     */
    V visitInstance(T instance);

    V visitProvider(Provider<? extends T> provider);
    V visitProviderKey(Key<? extends Provider<? extends T>> providerKey);
    V visitKey(Key<? extends T> key);

    // module-only bindings
    V visitUntargetted();

    // injector-only bindings
    V visitConstructor(Constructor<? extends T> constructor);
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
