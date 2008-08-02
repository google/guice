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
 * @param <T> the bound type. The injected is always assignable to this type.
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
   * Returns the scoped provider guice uses to fulfill requests for this
   * binding.
   *
   * @throws UnsupportedOperationException when invoked on a {@link Binding}
   *      created via {@link com.google.inject.spi.Elements#getElements}. This
   *      method is only supported on {@link Binding}s returned from an injector.
   */
  Provider<T> getProvider();

  /**
   * Accepts a target visitor. Invokes the visitor method specific to this binding's target.
   *
   * @param visitor to call back on
   */
  <V> V acceptTargetVisitor(TargetVisitor<? super T, V> visitor);

  /**
   * Accepts a scoping visitor. Invokes the visitor method specific to this binding's scoping.
   *
   * @param visitor to call back on
   */
  <V> V acceptScopingVisitor(ScopingVisitor<V> visitor);

  /**
   * Visits each of the strategies used to find an instance to satisfy an injection.
   *
   * @param <V> any type to be returned by the visit method. Use {@link Void} with
   *     {@code return null} if no return type is needed.
   */
  interface TargetVisitor<T, V> {

    /**
     * Visit a instance binding. The same instance is returned for every injection. This target is
     * found in both module and injector bindings.
     *
     * @param instance the user-supplied value
     */
    V visitInstance(T instance);

    /**
     * Visit a provider instance binding. The provider's {@code get} method is invoked to resolve
     * injections. This target is found in both module and injector bindings.
     *
     * @param provider the user-supplied, unscoped provider
     */
    V visitProvider(Provider<? extends T> provider);

    /**
     * Visit a provider key binding. To resolve injections, the provider injection is first
     * resolved, then that provider's {@code get} method is invoked. This target is found in both
     * module and injector bindings.
     *
     * @param providerKey the key used to resolve the provider's binding. That binding can be 
     *      retrieved from an injector using {@link Injector#getBinding(Key)
     *      Injector.getBinding(providerKey)}
     */
    V visitProviderKey(Key<? extends Provider<? extends T>> providerKey);

    /**
     * Visit a linked key binding. The other key's binding is used to resolve injections. This
     * target is found in both module and injector bindings.
     *
     * @param key the linked key used to resolve injections. That binding can be retrieved from an
     *      injector using {@link Injector#getBinding(Key) Injector.getBinding(key)}
     */
    V visitKey(Key<? extends T> key);

    /**
     * Visit an untargetted binding. This target is found only on module bindings. It indicates
     * that the injector should use its implicit binding strategies to resolve injections.
     */
    V visitUntargetted();

    /**
     * Visit a constructor binding. To resolve injections, an instance is instantiated by invoking
     * {@code constructor}. This target is found only on injector bindings.
     *
     * @param constructor the {@link Inject annotated} or default constructor that is invoked for
     *      creating values
     */
    V visitConstructor(Constructor<? extends T> constructor);

    /**
     * Visit a binding created from converting a bound instance to a new type. The source binding
     * has the same binding annotation but a different type. This target is found only on injector
     * bindings.
     *
     * @param value the converted value
     */
    V visitConvertedConstant(T value);

    /**
     * Visit a binding to a {@link Provider} that delegates to the binding for the provided type.
     * This target is found only on injector bindings.
     *
     * @param provided the key whose binding is used to {@link Provider#get provide instances}. That
     *      binding can be retrieved from an injector using {@link Injector#getBinding(Key)
     *      Injector.getBinding(provided)}
     */
    V visitProviderBinding(Key<?> provided);
  }

  /**
   * Visits each of the strategies used to scope an injection.
   *
   * @param <V> any type to be returned by the visit method. Use {@link Void} with
   *     {@code return null} if no return type is needed.
   */
  interface ScopingVisitor<V> {

    /**
     * Visit an eager singleton or single instance. This scope strategy is found on both module and
     * injector bindings.
     */
    V visitEagerSingleton();

    /**
     * Visit a scope instance. This scope strategy is found on both module and injector bindings.
     */
    V visitScope(Scope scope);

    /**
     * Visit a scope annotation. This scope strategy is found only on module bindings. The instance
     * that implements this scope is registered by {@link Binder#bindScope(Class, Scope)}.
     */
    V visitScopeAnnotation(Class<? extends Annotation> scopeAnnotation);

    /**
     * Visit an unspecified or unscoped strategy. On a module, this strategy indicates that the
     * injector should use scoping annotations to find a scope. On an injector, it indicates that
     * no scope is applied to the binding. An unscoped binding will behave like a scoped one when it
     * is linked to a scoped binding.
     */
    V visitNoScoping();
  }
}
