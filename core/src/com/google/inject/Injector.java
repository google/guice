/*
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

import com.google.inject.spi.TypeConverterBinding;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the graphs of objects that make up your application. The injector tracks the dependencies
 * for each type and uses bindings to inject them. This is the core of Guice, although you rarely
 * interact with it directly. This "behind-the-scenes" operation is what distinguishes dependency
 * injection from its cousin, the service locator pattern.
 *
 * <p>Contains several default bindings:
 *
 * <ul>
 * <li>This {@link Injector} instance itself
 * <li>A {@code Provider<T>} for each binding of type {@code T}
 * <li>The {@link java.util.logging.Logger} for the class being injected
 * <li>The {@link Stage} in which the Injector was created
 * </ul>
 *
 * Injectors are created using the facade class {@link Guice}.
 *
 * <p>An injector can also {@link #injectMembers(Object) inject the dependencies} of
 * already-constructed instances. This can be used to interoperate with objects created by other
 * frameworks or services.
 *
 * <p>Injectors can be {@link #createChildInjector(Iterable) hierarchical}. Child injectors inherit
 * the configuration of their parent injectors, but the converse does not hold.
 *
 * <p>The injector's {@link #getBindings() internal bindings} are available for introspection. This
 * enables tools and extensions to operate on an injector reflectively.
 *
 * @author crazybob@google.com (Bob Lee)
 * @author jessewilson@google.com (Jesse Wilson)
 */
public interface Injector {

  /**
   * Injects dependencies into the fields and methods of {@code instance}. Ignores the presence or
   * absence of an injectable constructor.
   *
   * <p>Whenever Guice creates an instance, it performs this injection automatically (after first
   * performing constructor injection), so if you're able to let Guice create all your objects for
   * you, you'll never need to use this method.
   *
   * @param instance to inject members on
   * @see Binder#getMembersInjector(Class) for a preferred alternative that supports checks before
   *     run time
   */
  void injectMembers(Object instance);

  /**
   * Returns the members injector used to inject dependencies into methods and fields on instances
   * of the given type {@code T}.
   *
   * @param typeLiteral type to get members injector for
   * @see Binder#getMembersInjector(TypeLiteral) for an alternative that offers up front error
   *     detection
   * @since 2.0
   */
  <T> MembersInjector<T> getMembersInjector(TypeLiteral<T> typeLiteral);

  /**
   * Returns the members injector used to inject dependencies into methods and fields on instances
   * of the given type {@code T}. When feasible, use {@link Binder#getMembersInjector(TypeLiteral)}
   * instead to get increased up front error detection.
   *
   * @param type type to get members injector for
   * @see Binder#getMembersInjector(Class) for an alternative that offers up front error detection
   * @since 2.0
   */
  <T> MembersInjector<T> getMembersInjector(Class<T> type);

  /**
   * Returns this injector's <strong>explicit</strong> bindings.
   *
   * <p>The returned map does not include bindings inherited from a {@link #getParent() parent
   * injector}, should one exist. The returned map is guaranteed to iterate (for example, with its
   * {@link Map#entrySet()} iterator) in the order of insertion. In other words, the order in which
   * bindings appear in user Modules.
   *
   * <p>This method is part of the Guice SPI and is intended for use by tools and extensions.
   */
  Map<Key<?>, Binding<?>> getBindings();

  /**
   * Returns a snapshot of this injector's bindings, <strong>both explicit and
   * just-in-time</strong>. The returned map is immutable; it contains only the bindings that were
   * present when {@code getAllBindings()} was invoked. Just-in-time bindings are only present if
   * they have been requested at least once. Subsequent calls may return a map with additional
   * just-in-time bindings.
   *
   * <p>The returned map does not include bindings inherited from a {@link #getParent() parent
   * injector}, should one exist.
   *
   * <p>This method is part of the Guice SPI and is intended for use by tools and extensions.
   *
   * @since 3.0
   */
  Map<Key<?>, Binding<?>> getAllBindings();

  /**
   * Returns the binding for the given injection key. This will be an explicit bindings if the key
   * was bound explicitly by a module, or an implicit binding otherwise. The implicit binding will
   * be created if necessary.
   *
   * <p>This method is part of the Guice SPI and is intended for use by tools and extensions.
   *
   * @throws ConfigurationException if this injector cannot find or create the binding.
   */
  <T> Binding<T> getBinding(Key<T> key);

  /**
   * Returns the binding for the given type. This will be an explicit bindings if the injection key
   * was bound explicitly by a module, or an implicit binding otherwise. The implicit binding will
   * be created if necessary.
   *
   * <p>This method is part of the Guice SPI and is intended for use by tools and extensions.
   *
   * @throws ConfigurationException if this injector cannot find or create the binding.
   * @since 2.0
   */
  <T> Binding<T> getBinding(Class<T> type);

  /**
   * Returns the binding if it already exists, or null if does not exist. Unlike {@link
   * #getBinding(Key)}, this does not attempt to create just-in-time bindings for keys that aren't
   * bound.
   *
   * <p>This method is part of the Guice SPI and is intended for use by tools and extensions.
   *
   * @since 3.0
   */
  <T> Binding<T> getExistingBinding(Key<T> key);

  /**
   * Returns all explicit bindings for {@code type}.
   *
   * <p>This method is part of the Guice SPI and is intended for use by tools and extensions.
   */
  <T> List<Binding<T>> findBindingsByType(TypeLiteral<T> type);

  /**
   * Returns the provider used to obtain instances for the given injection key. When feasible, avoid
   * using this method, in favor of having Guice inject your dependencies ahead of time.
   *
   * @throws ConfigurationException if this injector cannot find or create the provider.
   * @see Binder#getProvider(Key) for an alternative that offers up front error detection
   */
  <T> Provider<T> getProvider(Key<T> key);

  /**
   * Returns the provider used to obtain instances for the given type. When feasible, avoid using
   * this method, in favor of having Guice inject your dependencies ahead of time.
   *
   * @throws ConfigurationException if this injector cannot find or create the provider.
   * @see Binder#getProvider(Class) for an alternative that offers up front error detection
   */
  <T> Provider<T> getProvider(Class<T> type);

  /**
   * Returns the appropriate instance for the given injection key; equivalent to {@code
   * getProvider(key).get()}. When feasible, avoid using this method, in favor of having Guice
   * inject your dependencies ahead of time.
   *
   * @throws ConfigurationException if this injector cannot find or create the provider.
   * @throws ProvisionException if there was a runtime failure while providing an instance.
   */
  <T> T getInstance(Key<T> key);

  /**
   * Returns the appropriate instance for the given injection type; equivalent to {@code
   * getProvider(type).get()}. When feasible, avoid using this method, in favor of having Guice
   * inject your dependencies ahead of time.
   *
   * @throws ConfigurationException if this injector cannot find or create the provider.
   * @throws ProvisionException if there was a runtime failure while providing an instance.
   */
  <T> T getInstance(Class<T> type);

  /**
   * Returns this injector's parent, or {@code null} if this is a top-level injector.
   *
   * @since 2.0
   */
  Injector getParent();

  /**
   * Returns a new injector that inherits all state from this injector. All bindings, scopes,
   * interceptors and type converters are inherited -- they are visible to the child injector.
   * Elements of the child injector are not visible to its parent.
   *
   * <p>Just-in-time bindings created for child injectors will be created in an ancestor injector
   * whenever possible. This allows for scoped instances to be shared between injectors. Use
   * explicit bindings to prevent bindings from being shared with the parent injector. Optional
   * injections in just-in-time bindings (created in the parent injector) may be silently ignored if
   * the optional dependencies are from the child injector.
   *
   * <p>No key may be bound by both an injector and one of its ancestors. This includes just-in-time
   * bindings. The lone exception is the key for {@code Injector.class}, which is bound by each
   * injector to itself.
   *
   * @since 2.0
   */
  Injector createChildInjector(Iterable<? extends Module> modules);

  /**
   * Returns a new injector that inherits all state from this injector. All bindings, scopes,
   * interceptors and type converters are inherited -- they are visible to the child injector.
   * Elements of the child injector are not visible to its parent.
   *
   * <p>Just-in-time bindings created for child injectors will be created in an ancestor injector
   * whenever possible. This allows for scoped instances to be shared between injectors. Use
   * explicit bindings to prevent bindings from being shared with the parent injector.
   *
   * <p>No key may be bound by both an injector and one of its ancestors. This includes just-in-time
   * bindings. The lone exception is the key for {@code Injector.class}, which is bound by each
   * injector to itself.
   *
   * @since 2.0
   */
  Injector createChildInjector(Module... modules);

  /**
   * Returns a map containing all scopes in the injector. The maps keys are scoping annotations like
   * {@code Singleton.class}, and the values are scope instances, such as {@code Scopes.SINGLETON}.
   * The returned map is immutable.
   *
   * <p>This method is part of the Guice SPI and is intended for use by tools and extensions.
   *
   * @since 3.0
   */
  Map<Class<? extends Annotation>, Scope> getScopeBindings();

  /**
   * Returns a set containing all type converter bindings in the injector. The returned set is
   * immutable.
   *
   * <p>This method is part of the Guice SPI and is intended for use by tools and extensions.
   *
   * @since 3.0
   */
  Set<TypeConverterBinding> getTypeConverterBindings();
}
