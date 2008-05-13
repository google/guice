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

package com.google.inject.multibindings;

import com.google.inject.*;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.internal.Objects;
import com.google.inject.internal.TypeWithArgument;
import com.google.inject.multibindings.Multibinder.RealMultibinder;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An API to bind multiple map entries separately, only to later inject them as
 * a complete map. MapBinder is intended for use in your application's module:
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
 * <p>With this binding, a {@link Map}{@code <String, Snack>} can now be 
 * injected:
 * <pre><code>
 * class SnackMachine {
 *   {@literal @}Inject
 *   public SnackMachine(Map&lt;String, Snack&gt; snacks) { ... }
 * }</code></pre>
 * 
 * <p>In addition to binding {@code Map<K, V>}, a mapbinder will also bind
 * {@code Map<K, Provider<V>>} for lazy value provision:
 * <pre><code>
 * class SnackMachine {
 *   {@literal @}Inject
 *   public SnackMachine(Map&lt;String, Provider&lt;Snack&gt;&gt; snackProviders) { ... }
 * }</code></pre>
 *
 * <p>Creating mapbindings from different modules is supported. For example, it
 * is okay to have both {@code CandyModule} and {@code ChipsModule} both
 * create their own {@code MapBinder<String, Snack>}, and to each contribute 
 * bindings to the snacks map. When that map is injected, it will contain 
 * entries from both modules.
 *
 * <p>Values are resolved at map injection time. If a value is bound to a
 * provider, that provider's get method will be called each time the map is
 * injected (unless the binding is also scoped).
 *
 * <p>Annotations are be used to create different maps of the same key/value
 * type. Each distinct annotation gets its own independent map.
 *
 * <p><strong>Keys must be distinct.</strong> If the same key is bound more than
 * once, map injection will fail.
 *
 * <p><strong>Keys must be non-null.</strong> {@code addBinding(null)} will 
 * throw an unchecked exception.
 *
 * <p><strong>Values must be non-null to use map injection.</strong> If any
 * value is null, map injection will fail (although injecting a map of providers
 * will not).
 *
 * @author dpb@google.com (David P. Baker)
 * @author jessewilson@google.com (Jesse Wilson)
 */
public abstract class MapBinder<K, V> {
  private MapBinder() {}

  /**
   * Returns a new mapbinder that collects entries of {@code keyType}/{@code 
   * valueType} in a {@link Map} that is itself bound with no binding 
   * annotation.
   */
  public static <K, V> MapBinder<K, V> newMapBinder(Binder binder, 
      Type keyType, Type valueType) {
    return newMapBinder(binder,
        Key.get(MapBinder.<K, V>mapOf(keyType, valueType)),
        Key.get(MapBinder.<K, V>mapOfProviderOf(keyType, valueType)),
        Multibinder.<K>newSetBinder(binder, keyType),
        Multibinder.<V>newSetBinder(binder, valueType));
  }

  /**
   * Returns a new mapbinder that collects entries of {@code keyType}/{@code 
   * valueType} in a {@link Map} that is itself bound with {@code annotation}.
   */
  public static <K, V> MapBinder<K, V> newMapBinder(Binder binder, 
      Type keyType, Type valueType, Annotation annotation) {
    return newMapBinder(binder,
        Key.get(MapBinder.<K, V>mapOf(keyType, valueType), annotation),
        Key.get(MapBinder.<K, V>mapOfProviderOf(keyType, valueType), annotation),
        Multibinder.<K>newSetBinder(binder, keyType, annotation),
        Multibinder.<V>newSetBinder(binder, valueType, annotation));
  }

  /**
   * Returns a new mapbinder that collects entries of {@code keyType}/{@code 
   * valueType} in a {@link Map} that is itself bound with {@code annotationType}.
   */
  public static <K, V> MapBinder<K, V> newMapBinder(Binder binder, 
      Type keyType, Type valueType, Class<? extends Annotation> annotationType) {
    return newMapBinder(binder,
        Key.get(MapBinder.<K, V>mapOf(keyType, valueType), annotationType),
        Key.get(MapBinder.<K, V>mapOfProviderOf(keyType, valueType), annotationType),
        Multibinder.<K>newSetBinder(binder, keyType, annotationType),
        Multibinder.<V>newSetBinder(binder, valueType, annotationType));
  }

  @SuppressWarnings("unchecked")
  private static <K, V> TypeLiteral<Map<K, V>> mapOf(Type keyType, Type valueType) {
    Type type = new TypeWithArgument(Map.class, keyType, valueType);
    return (TypeLiteral<Map<K, V>>) TypeLiteral.get(type);
  }

  private static <K, V> TypeLiteral<Map<K, Provider<V>>> mapOfProviderOf(
      Type keyType, Type valueType) {
    return mapOf(keyType, new TypeWithArgument(Provider.class, valueType));
  }

  private static <K, V> MapBinder<K, V> newMapBinder(Binder binder,
      Key<Map<K, V>> mapKey, Key<Map<K, Provider<V>>> valueKey,
      Multibinder<K> keyMultibinder, Multibinder<V> valueMultibinder) {
    RealMapBinder<K, V> realMapBinder = new RealMapBinder<K, V>(
        mapKey, valueKey, keyMultibinder, valueMultibinder);
    binder.install(realMapBinder);
    return realMapBinder;
  }

  /**
   * Returns a binding builder used to add a new entry in the map. Each
   * key must be distinct (and non-null). Bound providers will be evaluated each
   * time the map is injected.
   *
   * <p>It is an error to call this method without also calling one of the
   * {@code to} methods on the returned binding builder.
   *
   * <p>Scoping elements independently is supported. Use the {@code in} method
   * to specify a binding scope.
   */
  public abstract LinkedBindingBuilder<V> addBinding(K key);

  /**
   * The actual mapbinder plays several roles:
   *
   * <p>As a MapBinder, it acts as a factory for LinkedBindingBuilders for
   * each of the map's values. It delegates to a {@link Multibinder} of
   * entries (keys to value providers).
   *
   * <p>As a Module, it installs the binding to the map itself, as well as to
   * a corresponding map whose values are providers. It uses the entry set 
   * multibinder to construct the map and the provider map.
   * 
   * <p>As a module, this implements equals() and hashcode() in order to trick 
   * Guice into executing its configure() method only once. That makes it so 
   * that multiple mapbinders can be created for the same target map, but
   * only one is bound. Since the list of bindings is retrieved from the
   * injector itself (and not the mapbinder), each mapbinder has access to
   * all contributions from all equivalent mapbinders.
   *
   * <p>Rather than binding a single Map.Entry&lt;K, V&gt;, the map binder
   * binds keys and values independently. This allows the values to be properly
   * scoped.
   *
   * <p>We use a subclass to hide 'implements Module' from the public API.
   */
  private static final class RealMapBinder<K, V> extends MapBinder<K, V> implements Module {
    private final Key<Map<K, V>> mapKey;
    private final Key<Map<K, Provider<V>>> providerMapKey;
    private final RealMultibinder<K> keyBinder;
    private final RealMultibinder<V> valueBinder;

    private RealMapBinder(Key<Map<K, V>> mapKey, Key<Map<K, Provider<V>>> providerMapKey,
        Multibinder<K> keyBinder, Multibinder<V> valueBinder) {
      this.mapKey = mapKey;
      this.providerMapKey = providerMapKey;
      this.keyBinder = (RealMultibinder<K>) keyBinder;
      this.valueBinder = (RealMultibinder<V>) valueBinder;
    }

    public LinkedBindingBuilder<V> addBinding(K key) {
      // this code is currently quite crufty - we depend on the fact that the
      // key and the value have the same unique ID. A better approach would be
      // to create an element annotation that knows the Map.Entry type
      Objects.nonNull(key, "key");
      int uniqueId = RealMultibinder.nextUniqueId.getAndIncrement();
      keyBinder.addBinding("key", uniqueId).toInstance(key);
      return valueBinder.addBinding("value", uniqueId);
    }

    public void configure(Binder binder) {
      final Provider<Map<K, Provider<V>>> providerMapProvider
          = new Provider<Map<K, Provider<V>>>() {
        private Map<K, Provider<V>> providerMap;

        @Inject void init(Injector injector) {
          Map<Integer, K> keys = new LinkedHashMap<Integer, K>();
          Map<Integer, Provider<V>> valueProviders = new HashMap<Integer, Provider<V>>();

          // find the bindings
          for (Map.Entry<Key<?>, Binding<?>> entry : injector.getBindings().entrySet()) {
            if (keyBinder.keyMatches(entry.getKey(), "key")) {
              Element element = (Element) entry.getKey().getAnnotation();
              @SuppressWarnings("unchecked")
              Binding<K> binding = (Binding<K>) entry.getValue();
              keys.put(element.uniqueId(), binding.getProvider().get());
            } else if (valueBinder.keyMatches(entry.getKey(), "value")) {
              Element element = (Element) entry.getKey().getAnnotation();
              @SuppressWarnings("unchecked")
              Binding<V> binding = (Binding<V>) entry.getValue();
              valueProviders.put(element.uniqueId(), binding.getProvider());
            }
          }

          // build the map
          Map<K, Provider<V>> providerMapMutable = new LinkedHashMap<K, Provider<V>>();
          for (Map.Entry<Integer, K> entry : keys.entrySet()) {
            K key = entry.getValue();
            Provider<V> valueProvider = valueProviders.get(entry.getKey());
            if (valueProvider == null) {
              continue;
            }
            if (providerMapMutable.put(key, valueProvider) != null) {
              throw new IllegalStateException("Map injection failed due to duplicated key \""
                  + key + "\"");
            }
          }

          providerMap = Collections.unmodifiableMap(providerMapMutable);
        }

        public Map<K, Provider<V>> get() {
          return providerMap;
        }
      };

      binder.bind(providerMapKey).toProvider(providerMapProvider);

      binder.bind(mapKey).toProvider(new Provider<Map<K, V>>() {
        public Map<K, V> get() {
          Map<K, V> map = new LinkedHashMap<K, V>();
          for (Map.Entry<K, Provider<V>> entry : providerMapProvider.get().entrySet()) {
            V value = entry.getValue().get();
            K key = entry.getKey();
            if (value == null) {
              throw new IllegalStateException("Map injection failed due to null value for key \"" 
                  + key + "\"");
            }
            map.put(key, value);
          }
          return Collections.unmodifiableMap(map);
        }
      });
    }

    @Override public boolean equals(Object o) {
      return o instanceof RealMapBinder
          && ((RealMapBinder) o).mapKey.equals(mapKey);
    }

    @Override public int hashCode() {
      return mapKey.hashCode();
    }
  }
}
