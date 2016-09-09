/*
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

package com.google.inject.multibindings;

import static com.google.inject.internal.RealMapBinder.newMapRealBinder;
import static com.google.inject.internal.RealMapBinder.newRealMapBinder;

import com.google.inject.Binder;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.internal.RealMapBinder;
import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * An API to bind multiple map entries separately, only to later inject them as a complete map.
 * MapBinder is intended for use in your application's module:
 *
 * <pre><code>
 * public class SnacksModule extends AbstractModule {
 *   protected void configure() {
 *     MapBinder&lt;String, Snack&gt; mapbinder
 *         = MapBinder.newMapBinder(binder(), String.class, Snack.class);
 *     mapbinder.addBinding("twix").toInstance(new Twix());
 *     mapbinder.addBinding("snickers").toProvider(SnickersProvider.class);
 *     mapbinder.addBinding("skittles").to(Skittles.class);
 *   }
 * }</code></pre>
 *
 * <p>With this binding, a {@link Map}{@code <String, Snack>} can now be injected:
 *
 * <pre><code>
 * class SnackMachine {
 *   {@literal @}Inject
 *   public SnackMachine(Map&lt;String, Snack&gt; snacks) { ... }
 * }</code></pre>
 *
 * <p>In addition to binding {@code Map<K, V>}, a mapbinder will also bind {@code Map<K,
 * Provider<V>>} for lazy value provision:
 *
 * <pre><code>
 * class SnackMachine {
 *   {@literal @}Inject
 *   public SnackMachine(Map&lt;String, Provider&lt;Snack&gt;&gt; snackProviders) { ... }
 * }</code></pre>
 *
 * <p>Contributing mapbindings from different modules is supported. For example, it is okay to have
 * both {@code CandyModule} and {@code ChipsModule} both create their own {@code MapBinder<String,
 * Snack>}, and to each contribute bindings to the snacks map. When that map is injected, it will
 * contain entries from both modules.
 *
 * <p>The map's iteration order is consistent with the binding order. This is convenient when
 * multiple elements are contributed by the same module because that module can order its bindings
 * appropriately. Avoid relying on the iteration order of elements contributed by different modules,
 * since there is no equivalent mechanism to order modules.
 *
 * <p>The map is unmodifiable. Elements can only be added to the map by configuring the MapBinder.
 * Elements can never be removed from the map.
 *
 * <p>Values are resolved at map injection time. If a value is bound to a provider, that provider's
 * get method will be called each time the map is injected (unless the binding is also scoped, or a
 * map of providers is injected).
 *
 * <p>Annotations are used to create different maps of the same key/value type. Each distinct
 * annotation gets its own independent map.
 *
 * <p><strong>Keys must be distinct.</strong> If the same key is bound more than once, map injection
 * will fail. However, use {@link #permitDuplicates()} in order to allow duplicate keys; extra
 * bindings to {@code Map<K, Set<V>>} and {@code Map<K, Set<Provider<V>>} will be added.
 *
 * <p><strong>Keys must be non-null.</strong> {@code addBinding(null)} will throw an unchecked
 * exception.
 *
 * <p><strong>Values must be non-null to use map injection.</strong> If any value is null, map
 * injection will fail (although injecting a map of providers will not).
 *
 * @author dpb@google.com (David P. Baker)
 */
public class MapBinder<K, V> {
  // This class is non-final due to users mocking this in tests :(

  /**
   * Returns a new mapbinder that collects entries of {@code keyType}/{@code valueType} in a {@link
   * Map} that is itself bound with no binding annotation.
   */
  public static <K, V> MapBinder<K, V> newMapBinder(
      Binder binder, TypeLiteral<K> keyType, TypeLiteral<V> valueType) {
    return new MapBinder<K, V>(
        newMapRealBinder(binder.skipSources(MapBinder.class), keyType, valueType));
  }

  /**
   * Returns a new mapbinder that collects entries of {@code keyType}/{@code valueType} in a {@link
   * Map} that is itself bound with no binding annotation.
   */
  public static <K, V> MapBinder<K, V> newMapBinder(
      Binder binder, Class<K> keyType, Class<V> valueType) {
    return newMapBinder(binder, TypeLiteral.get(keyType), TypeLiteral.get(valueType));
  }

  /**
   * Returns a new mapbinder that collects entries of {@code keyType}/{@code valueType} in a {@link
   * Map} that is itself bound with {@code annotation}.
   */
  public static <K, V> MapBinder<K, V> newMapBinder(
      Binder binder, TypeLiteral<K> keyType, TypeLiteral<V> valueType, Annotation annotation) {
    return new MapBinder<K, V>(
        newRealMapBinder(binder.skipSources(MapBinder.class), keyType, valueType, annotation));
  }

  /**
   * Returns a new mapbinder that collects entries of {@code keyType}/{@code valueType} in a {@link
   * Map} that is itself bound with {@code annotation}.
   */
  public static <K, V> MapBinder<K, V> newMapBinder(
      Binder binder, Class<K> keyType, Class<V> valueType, Annotation annotation) {
    return newMapBinder(binder, TypeLiteral.get(keyType), TypeLiteral.get(valueType), annotation);
  }

  /**
   * Returns a new mapbinder that collects entries of {@code keyType}/{@code valueType} in a {@link
   * Map} that is itself bound with {@code annotationType}.
   */
  public static <K, V> MapBinder<K, V> newMapBinder(
      Binder binder,
      TypeLiteral<K> keyType,
      TypeLiteral<V> valueType,
      Class<? extends Annotation> annotationType) {
    return new MapBinder<K, V>(
        newRealMapBinder(binder.skipSources(MapBinder.class), keyType, valueType, annotationType));
  }

  /**
   * Returns a new mapbinder that collects entries of {@code keyType}/{@code valueType} in a {@link
   * Map} that is itself bound with {@code annotationType}.
   */
  public static <K, V> MapBinder<K, V> newMapBinder(
      Binder binder,
      Class<K> keyType,
      Class<V> valueType,
      Class<? extends Annotation> annotationType) {
    return newMapBinder(
        binder, TypeLiteral.get(keyType), TypeLiteral.get(valueType), annotationType);
  }

  private final RealMapBinder<K, V> delegate;

  private MapBinder(RealMapBinder<K, V> delegate) {
    this.delegate = delegate;
  }

  /**
   * Configures the {@code MapBinder} to handle duplicate entries.
   *
   * <p>When multiple equal keys are bound, the value that gets included in the map is arbitrary.
   *
   * <p>In addition to the {@code Map<K, V>} and {@code Map<K, Provider<V>>} maps that are normally
   * bound, a {@code Map<K, Set<V>>} and {@code Map<K, Set<Provider<V>>>} are <em>also</em> bound,
   * which contain all values bound to each key.
   *
   * <p>When multiple modules contribute elements to the map, this configuration option impacts all
   * of them.
   *
   * @return this map binder
   * @since 3.0
   */
  public MapBinder<K, V> permitDuplicates() {
    delegate.permitDuplicates();
    return this;
  }

  /**
   * Returns a binding builder used to add a new entry in the map. Each key must be distinct (and
   * non-null). Bound providers will be evaluated each time the map is injected.
   *
   * <p>It is an error to call this method without also calling one of the {@code to} methods on the
   * returned binding builder.
   *
   * <p>Scoping elements independently is supported. Use the {@code in} method to specify a binding
   * scope.
   */
  public LinkedBindingBuilder<V> addBinding(K key) {
    return delegate.addBinding(key);
  }

  // Some tests rely on MapBinder implementing equals/hashCode

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof MapBinder) {
      return delegate.equals(((MapBinder<?, ?>) obj).delegate);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return delegate.hashCode();
  }
}
