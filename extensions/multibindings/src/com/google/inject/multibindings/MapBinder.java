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

import static com.google.inject.multibindings.Element.Type.MAPBINDER;
import static com.google.inject.multibindings.Multibinder.checkConfiguration;
import static com.google.inject.multibindings.Multibinder.checkNotNull;
import static com.google.inject.multibindings.Multibinder.setOf;
import static com.google.inject.util.Types.newParameterizedType;
import static com.google.inject.util.Types.newParameterizedTypeWithOwner;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Supplier;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.internal.Errors;
import com.google.inject.multibindings.Indexer.IndexedBinding;
import com.google.inject.multibindings.Multibinder.RealMultibinder;
import com.google.inject.spi.BindingTargetVisitor;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.Element;
import com.google.inject.spi.HasDependencies;
import com.google.inject.spi.ProviderInstanceBinding;
import com.google.inject.spi.ProviderLookup;
import com.google.inject.spi.ProviderWithDependencies;
import com.google.inject.spi.ProviderWithExtensionVisitor;
import com.google.inject.spi.Toolable;
import com.google.inject.util.Types;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

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
 * <p>Contributing mapbindings from different modules is supported. For example,
 * it is okay to have both {@code CandyModule} and {@code ChipsModule} both
 * create their own {@code MapBinder<String, Snack>}, and to each contribute
 * bindings to the snacks map. When that map is injected, it will contain
 * entries from both modules.
 *
 * <p>The map's iteration order is consistent with the binding order. This is
 * convenient when multiple elements are contributed by the same module because
 * that module can order its bindings appropriately. Avoid relying on the
 * iteration order of elements contributed by different modules, since there is
 * no equivalent mechanism to order modules.
 *
 * <p>The map is unmodifiable.  Elements can only be added to the map by
 * configuring the MapBinder.  Elements can never be removed from the map.
 *
 * <p>Values are resolved at map injection time. If a value is bound to a
 * provider, that provider's get method will be called each time the map is
 * injected (unless the binding is also scoped, or a map of providers is injected).
 *
 * <p>Annotations are used to create different maps of the same key/value
 * type. Each distinct annotation gets its own independent map.
 *
 * <p><strong>Keys must be distinct.</strong> If the same key is bound more than
 * once, map injection will fail. However, use {@link #permitDuplicates()} in
 * order to allow duplicate keys; extra bindings to {@code Map<K, Set<V>>} and
 * {@code Map<K, Set<Provider<V>>} will be added.
 *
 * <p><strong>Keys must be non-null.</strong> {@code addBinding(null)} will
 * throw an unchecked exception.
 *
 * <p><strong>Values must be non-null to use map injection.</strong> If any
 * value is null, map injection will fail (although injecting a map of providers
 * will not).
 *
 * @author dpb@google.com (David P. Baker)
 */
public abstract class MapBinder<K, V> {
  private MapBinder() {}

  /**
   * Returns a new mapbinder that collects entries of {@code keyType}/{@code valueType} in a
   * {@link Map} that is itself bound with no binding annotation.
   */
  public static <K, V> MapBinder<K, V> newMapBinder(Binder binder,
      TypeLiteral<K> keyType, TypeLiteral<V> valueType) {
    binder = binder.skipSources(MapBinder.class, RealMapBinder.class);
    return newRealMapBinder(binder, keyType, valueType, Key.get(mapOf(keyType, valueType)),
        Multibinder.newSetBinder(binder, entryOfProviderOf(keyType, valueType)));
  }

  /**
   * Returns a new mapbinder that collects entries of {@code keyType}/{@code valueType} in a
   * {@link Map} that is itself bound with no binding annotation.
   */
  public static <K, V> MapBinder<K, V> newMapBinder(Binder binder,
      Class<K> keyType, Class<V> valueType) {
    return newMapBinder(binder, TypeLiteral.get(keyType), TypeLiteral.get(valueType));
  }

  /**
   * Returns a new mapbinder that collects entries of {@code keyType}/{@code valueType} in a
   * {@link Map} that is itself bound with {@code annotation}.
   */
  public static <K, V> MapBinder<K, V> newMapBinder(Binder binder,
      TypeLiteral<K> keyType, TypeLiteral<V> valueType, Annotation annotation) {
    binder = binder.skipSources(MapBinder.class, RealMapBinder.class);
    return newRealMapBinder(binder, keyType, valueType,
        Key.get(mapOf(keyType, valueType), annotation),
        Multibinder.newSetBinder(binder, entryOfProviderOf(keyType, valueType), annotation));
  }

  /**
   * Returns a new mapbinder that collects entries of {@code keyType}/{@code valueType} in a
   * {@link Map} that is itself bound with {@code annotation}.
   */
  public static <K, V> MapBinder<K, V> newMapBinder(Binder binder,
      Class<K> keyType, Class<V> valueType, Annotation annotation) {
    return newMapBinder(binder, TypeLiteral.get(keyType), TypeLiteral.get(valueType), annotation);
  }

  /**
   * Returns a new mapbinder that collects entries of {@code keyType}/{@code valueType} in a
   * {@link Map} that is itself bound with {@code annotationType}.
   */
  public static <K, V> MapBinder<K, V> newMapBinder(Binder binder, TypeLiteral<K> keyType,
      TypeLiteral<V> valueType, Class<? extends Annotation> annotationType) {
    binder = binder.skipSources(MapBinder.class, RealMapBinder.class);
    return newRealMapBinder(binder, keyType, valueType,
        Key.get(mapOf(keyType, valueType), annotationType),
        Multibinder.newSetBinder(binder, entryOfProviderOf(keyType, valueType), annotationType));
  }

  /**
   * Returns a new mapbinder that collects entries of {@code keyType}/{@code valueType} in a
   * {@link Map} that is itself bound with {@code annotationType}.
   */
  public static <K, V> MapBinder<K, V> newMapBinder(Binder binder, Class<K> keyType,
      Class<V> valueType, Class<? extends Annotation> annotationType) {
    return newMapBinder(
        binder, TypeLiteral.get(keyType), TypeLiteral.get(valueType), annotationType);
  }

  @SuppressWarnings("unchecked") // a map of <K, V> is safely a Map<K, V>
  static <K, V> TypeLiteral<Map<K, V>> mapOf(
      TypeLiteral<K> keyType, TypeLiteral<V> valueType) {
    return (TypeLiteral<Map<K, V>>) TypeLiteral.get(
        Types.mapOf(keyType.getType(), valueType.getType()));
  }

  @SuppressWarnings("unchecked") // a provider map <K, V> is safely a Map<K, Provider<V>>
  static <K, V> TypeLiteral<Map<K, Provider<V>>> mapOfProviderOf(
      TypeLiteral<K> keyType, TypeLiteral<V> valueType) {
    return (TypeLiteral<Map<K, Provider<V>>>) TypeLiteral.get(
        Types.mapOf(keyType.getType(), Types.providerOf(valueType.getType())));
  }
  
  // provider map <K, V> is safely a Map<K, javax.inject.Provider<V>>>
  @SuppressWarnings("unchecked")
  static <K, V> TypeLiteral<Map<K, javax.inject.Provider<V>>> mapOfJavaxProviderOf(
      TypeLiteral<K> keyType, TypeLiteral<V> valueType) {
    return (TypeLiteral<Map<K, javax.inject.Provider<V>>>) TypeLiteral.get(
        Types.mapOf(keyType.getType(),
            newParameterizedType(javax.inject.Provider.class, valueType.getType())));
  }

  @SuppressWarnings("unchecked") // a provider map <K, Set<V>> is safely a Map<K, Set<Provider<V>>>
  static <K, V> TypeLiteral<Map<K, Set<Provider<V>>>> mapOfSetOfProviderOf(
      TypeLiteral<K> keyType, TypeLiteral<V> valueType) {
    return (TypeLiteral<Map<K, Set<Provider<V>>>>) TypeLiteral.get(
        Types.mapOf(keyType.getType(), Types.setOf(Types.providerOf(valueType.getType()))));
  }

  @SuppressWarnings("unchecked") // a provider entry <K, V> is safely a Map.Entry<K, Provider<V>>
  static <K, V> TypeLiteral<Map.Entry<K, Provider<V>>> entryOfProviderOf(
      TypeLiteral<K> keyType, TypeLiteral<V> valueType) {
    return (TypeLiteral<Entry<K, Provider<V>>>) TypeLiteral.get(newParameterizedTypeWithOwner(
        Map.class, Entry.class, keyType.getType(), Types.providerOf(valueType.getType())));
  }

  // Note: We use valueTypeAndAnnotation effectively as a Pair<TypeLiteral, Annotation|Class>
  // since it's an easy way to group a type and an optional annotation type or instance.
  static <K, V> RealMapBinder<K, V> newRealMapBinder(Binder binder, TypeLiteral<K> keyType,
      Key<V> valueTypeAndAnnotation) {
    binder = binder.skipSources(MapBinder.class, RealMapBinder.class);
    TypeLiteral<V> valueType = valueTypeAndAnnotation.getTypeLiteral();
    return newRealMapBinder(binder, keyType, valueType,
        valueTypeAndAnnotation.ofType(mapOf(keyType, valueType)),
        Multibinder.newSetBinder(binder,
            valueTypeAndAnnotation.ofType(entryOfProviderOf(keyType, valueType))));
  }

  private static <K, V> RealMapBinder<K, V> newRealMapBinder(Binder binder,
      TypeLiteral<K> keyType, TypeLiteral<V> valueType, Key<Map<K, V>> mapKey,
      Multibinder<Entry<K, Provider<V>>> entrySetBinder) {
    RealMapBinder<K, V> mapBinder =
        new RealMapBinder<K, V>(binder, keyType, valueType, mapKey, entrySetBinder);
    binder.install(mapBinder);
    return mapBinder;
  }

  /**
   * Configures the {@code MapBinder} to handle duplicate entries.
   * <p>When multiple equal keys are bound, the value that gets included in the map is
   * arbitrary.
   * <p>In addition to the {@code Map<K, V>} and {@code Map<K, Provider<V>>}
   * maps that are normally bound, a {@code Map<K, Set<V>>} and
   * {@code Map<K, Set<Provider<V>>>} are <em>also</em> bound, which contain
   * all values bound to each key.
   * <p>
   * When multiple modules contribute elements to the map, this configuration
   * option impacts all of them.
   *
   * @return this map binder
   * @since 3.0
   */
  public abstract MapBinder<K, V> permitDuplicates();

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
  static final class RealMapBinder<K, V> extends MapBinder<K, V> implements Module {
    private final TypeLiteral<K> keyType;
    private final TypeLiteral<V> valueType;
    private final Key<Map<K, V>> mapKey;
    private final Key<Map<K, javax.inject.Provider<V>>> javaxProviderMapKey;
    private final Key<Map<K, Provider<V>>> providerMapKey;
    private final Key<Map<K, Set<V>>> multimapKey;
    private final Key<Map<K, Set<Provider<V>>>> providerMultimapKey;
    private final RealMultibinder<Map.Entry<K, Provider<V>>> entrySetBinder;
    private final Map<K, String> duplicateKeyErrorMessages;

    /* the target injector's binder. non-null until initialization, null afterwards */
    private Binder binder;

    private boolean permitDuplicates;
    private ImmutableList<Map.Entry<K, Binding<V>>> mapBindings;

    private RealMapBinder(Binder binder, TypeLiteral<K> keyType, TypeLiteral<V> valueType,
        Key<Map<K, V>> mapKey, Multibinder<Map.Entry<K, Provider<V>>> entrySetBinder) {
      this.keyType = keyType;
      this.valueType = valueType;
      this.mapKey = mapKey;
      this.providerMapKey = mapKey.ofType(mapOfProviderOf(keyType, valueType));
      this.javaxProviderMapKey = mapKey.ofType(mapOfJavaxProviderOf(keyType, valueType));
      this.multimapKey = mapKey.ofType(mapOf(keyType, setOf(valueType)));
      this.providerMultimapKey = mapKey.ofType(mapOfSetOfProviderOf(keyType, valueType));
      this.entrySetBinder = (RealMultibinder<Entry<K, Provider<V>>>) entrySetBinder;
      this.binder = binder;
      this.duplicateKeyErrorMessages = Maps.newHashMap();
    }
    
    /** Sets the error message to be shown if the key had duplicate non-equal bindings. */
    void updateDuplicateKeyMessage(K k, String errMsg) {
      duplicateKeyErrorMessages.put(k, errMsg);
    }

    @Override
    public MapBinder<K, V> permitDuplicates() {
      entrySetBinder.permitDuplicates();
      binder.install(new MultimapBinder<K, V>(
          multimapKey, providerMultimapKey, entrySetBinder.getSetKey()));
      return this;
    }
    
    Key<V> getKeyForNewValue(K key) {
      checkNotNull(key, "key");
      checkConfiguration(!isInitialized(), "MapBinder was already initialized");

      Key<V> valueKey = Key.get(valueType,
          new RealElement(entrySetBinder.getSetName(), MAPBINDER, keyType.toString()));
      entrySetBinder.addBinding().toProvider(new ProviderMapEntry<K, V>(
          key, binder.getProvider(valueKey), valueKey));
      return valueKey;
    }

    /**
     * This creates two bindings. One for the {@code Map.Entry<K, Provider<V>>}
     * and another for {@code V}.
     */
    @Override public LinkedBindingBuilder<V> addBinding(K key) {
      return binder.bind(getKeyForNewValue(key));
    }

    @Override public void configure(Binder binder) {
      checkConfiguration(!isInitialized(), "MapBinder was already initialized");

      ImmutableSet<Dependency<?>> dependencies
          = ImmutableSet.<Dependency<?>>of(Dependency.get(entrySetBinder.getSetKey()));

      // Binds a Map<K, Provider<V>> from a collection of Set<Entry<K, Provider<V>>.
      Provider<Set<Entry<K, Provider<V>>>> entrySetProvider = binder
          .getProvider(entrySetBinder.getSetKey());

      binder.bind(providerMapKey).toProvider(
          new RealProviderMapProvider(dependencies, entrySetProvider));

      // The map this exposes is internally an ImmutableMap, so it's OK to massage
      // the guice Provider to javax Provider in the value (since Guice provider
      // implements javax Provider).
      @SuppressWarnings("unchecked")
      Key massagedProviderMapKey = (Key)providerMapKey;
      binder.bind(javaxProviderMapKey).to(massagedProviderMapKey);

      Provider<Map<K, Provider<V>>> mapProvider = binder.getProvider(providerMapKey);
      binder.bind(mapKey).toProvider(new RealMapProvider(dependencies, mapProvider));
    }

    boolean containsElement(Element element) {
      if (entrySetBinder.containsElement(element)) {
        return true;
      } else {
        Key<?> key;
        if (element instanceof Binding) {
          key = ((Binding<?>)element).getKey();
        } else if (element instanceof ProviderLookup) {
          key = ((ProviderLookup<?>)element).getKey();
        } else {
          return false; // cannot match;
        }

        return key.equals(mapKey)
            || key.equals(providerMapKey)
            || key.equals(javaxProviderMapKey)
            || key.equals(multimapKey)
            || key.equals(providerMultimapKey)
            || key.equals(entrySetBinder.getSetKey())
            || matchesValueKey(key);
        }
    }

    /** Returns true if the key indicates this is a value in the map. */
    private boolean matchesValueKey(Key<?> key) {
      return key.getAnnotation() instanceof RealElement
          && ((RealElement) key.getAnnotation()).setName().equals(entrySetBinder.getSetName())
          && ((RealElement) key.getAnnotation()).type() == MAPBINDER
          && ((RealElement) key.getAnnotation()).keyType().equals(keyType.toString())
          && key.getTypeLiteral().equals(valueType);
    }

    private boolean isInitialized() {
      return binder == null;
    }

    @Override public boolean equals(Object o) {
      return o instanceof RealMapBinder
          && ((RealMapBinder<?, ?>) o).mapKey.equals(mapKey);
    }

    @Override public int hashCode() {
      return mapKey.hashCode();
    }

    final class RealProviderMapProvider
        extends RealMapBinderProviderWithDependencies<Map<K, Provider<V>>> {
      private final ImmutableSet<Dependency<?>> dependencies;
      private final Provider<Set<Entry<K, Provider<V>>>> entrySetProvider;
      private Map<K, Provider<V>> providerMap;

      private RealProviderMapProvider(
          ImmutableSet<Dependency<?>> dependencies,
          Provider<Set<Entry<K, Provider<V>>>> entrySetProvider) {
        super(mapKey);
        this.dependencies = dependencies;
        this.entrySetProvider = entrySetProvider;
      }

      @Toolable @Inject void initialize(Injector injector) {
        RealMapBinder.this.binder = null;
        permitDuplicates = entrySetBinder.permitsDuplicates(injector);

        Map<K, Provider<V>> providerMapMutable = new LinkedHashMap<K, Provider<V>>();
        List<Map.Entry<K, Binding<V>>> bindingsMutable = Lists.newArrayList();
        Indexer indexer = new Indexer(injector);
        Multimap<K, IndexedBinding> index = HashMultimap.create();
        Set<K> duplicateKeys = null;
        for (Entry<K, Provider<V>> entry : entrySetProvider.get()) {
          ProviderMapEntry<K, V> providerEntry = (ProviderMapEntry<K, V>) entry;
          Key<V> valueKey = providerEntry.getValueKey();
          Binding<V> valueBinding = injector.getBinding(valueKey);
          // If this isn't a dup due to an exact same binding, add it.
          if (index.put(providerEntry.getKey(), valueBinding.acceptTargetVisitor(indexer))) {
            Provider<V> previous = providerMapMutable.put(providerEntry.getKey(),
                new ValueProvider<V>(providerEntry.getValue(), valueBinding));
            if (previous != null && !permitDuplicates) {
              if (duplicateKeys == null) {
                duplicateKeys = Sets.newHashSet();
              }
              duplicateKeys.add(providerEntry.getKey());
            }
            bindingsMutable.add(Maps.immutableEntry(providerEntry.getKey(), valueBinding));
          }
        }
        if (duplicateKeys != null) {
          // Must use a ListMultimap in case more than one binding has the same source
          // and is listed multiple times.
          Multimap<K, String> dups = newLinkedKeyArrayValueMultimap();
          for (Map.Entry<K, Binding<V>> entry : bindingsMutable) {
            if (duplicateKeys.contains(entry.getKey())) {
              dups.put(entry.getKey(), "\t at " + Errors.convert(entry.getValue().getSource()));
            }
          }
          StringBuilder sb = new StringBuilder("Map injection failed due to duplicated key ");
          boolean first = true;
          for (K key : dups.keySet()) {
            if (first) {
              first = false;
              if (duplicateKeyErrorMessages.containsKey(key)) {
                sb.setLength(0);
                sb.append(duplicateKeyErrorMessages.get(key));
              } else {
                sb.append("\"" + key + "\", from bindings:\n");
              }
            } else {
              if (duplicateKeyErrorMessages.containsKey(key)) {
                sb.append("\n and " + duplicateKeyErrorMessages.get(key));
              } else {
                sb.append("\n and key: \"" + key + "\", from bindings:\n");
              }
            }
            Joiner.on('\n').appendTo(sb, dups.get(key)).append("\n");
          }
          checkConfiguration(false, sb.toString());
        }

        providerMap = ImmutableMap.copyOf(providerMapMutable);
        mapBindings = ImmutableList.copyOf(bindingsMutable);
      }

      @Override public Map<K, Provider<V>> get() {
        return providerMap;
      }

      @Override public Set<Dependency<?>> getDependencies() {
        return dependencies;
      }
    }

    final class RealMapProvider extends RealMapWithExtensionProvider<Map<K, V>> {
      private final ImmutableSet<Dependency<?>> dependencies;
      private final Provider<Map<K, Provider<V>>> mapProvider;

      private RealMapProvider(
          ImmutableSet<Dependency<?>> dependencies,
          Provider<Map<K, Provider<V>>> mapProvider) {
        super(mapKey);
        this.dependencies = dependencies;
        this.mapProvider = mapProvider;
      }

      @Override public Map<K, V> get() {
        // We can initialize the internal table efficiently this way and then swap the values
        // one by one.
        Map<K, Object> map = new LinkedHashMap<K, Object>(mapProvider.get());
        for (Entry<K, Object> entry : map.entrySet()) {
          @SuppressWarnings("unchecked")  // we initialized the entries with providers
          ValueProvider<V> provider = (ValueProvider<V>)entry.getValue();
          V value = provider.get();
          checkConfiguration(value != null,
              "Map injection failed due to null value for key \"%s\", bound at: %s",
              entry.getKey(),
              provider.getValueBinding().getSource());
          entry.setValue(value);
        }
        @SuppressWarnings("unchecked")  // if we exited the loop then we replaced all Providers
        Map<K, V> typedMap = (Map<K, V>) map;
        return Collections.unmodifiableMap(typedMap);
      }

      @Override public Set<Dependency<?>> getDependencies() {
        return dependencies;
      }

      @SuppressWarnings("unchecked")
      @Override
      public <B, R> R acceptExtensionVisitor(BindingTargetVisitor<B, R> visitor,
          ProviderInstanceBinding<? extends B> binding) {
        if (visitor instanceof MultibindingsTargetVisitor) {
          return ((MultibindingsTargetVisitor<Map<K, V>, R>)visitor).visit(this);
        } else {
          return visitor.visit(binding);
        }
      }

      @Override public Key<Map<K, V>> getMapKey() {
        return mapKey;
      }

      @Override public TypeLiteral<?> getKeyTypeLiteral() {
        return keyType;
      }

      @Override public TypeLiteral<?> getValueTypeLiteral() {
        return valueType;
      }

      @SuppressWarnings("unchecked")
      @Override
      public List<Entry<?, Binding<?>>> getEntries() {
        if (isInitialized()) {
          return (List)mapBindings; // safe because mapBindings is immutable
        } else {
          throw new UnsupportedOperationException(
              "getElements() not supported for module bindings");
        }
      }

      @Override public boolean permitsDuplicates() {
        if (isInitialized()) {
          return permitDuplicates;
        } else {
          throw new UnsupportedOperationException(
              "permitsDuplicates() not supported for module bindings");
        }
      }

      @Override public boolean containsElement(Element element) {
        return RealMapBinder.this.containsElement(element);
      }
    }

    /**
     * Binds {@code Map<K, Set<V>>} and {{@code Map<K, Set<Provider<V>>>}.
     */
    static final class MultimapBinder<K, V> implements Module {

      private final Key<Map<K, Set<V>>> multimapKey;
      private final Key<Map<K, Set<Provider<V>>>> providerMultimapKey;
      private final Key<Set<Entry<K,Provider<V>>>> entrySetKey;

      public MultimapBinder(
          Key<Map<K, Set<V>>> multimapKey,
          Key<Map<K, Set<Provider<V>>>> providerMultimapKey,
          Key<Set<Entry<K,Provider<V>>>> entrySetKey) {
        this.multimapKey = multimapKey;
        this.providerMultimapKey = providerMultimapKey;
        this.entrySetKey = entrySetKey;
      }

      @Override public void configure(Binder binder) {
        ImmutableSet<Dependency<?>> dependencies
            = ImmutableSet.<Dependency<?>>of(Dependency.get(entrySetKey));

        Provider<Set<Entry<K, Provider<V>>>> entrySetProvider =
            binder.getProvider(entrySetKey);
        // Binds a Map<K, Set<Provider<V>>> from a collection of Map<Entry<K, Provider<V>> if
        // permitDuplicates was called.
        binder.bind(providerMultimapKey).toProvider(
            new RealProviderMultimapProvider(dependencies, entrySetProvider));

        Provider<Map<K, Set<Provider<V>>>> multimapProvider =
            binder.getProvider(providerMultimapKey);
        binder.bind(multimapKey).toProvider(
            new RealMultimapProvider(dependencies, multimapProvider));
      }

      @Override public int hashCode() {
        return multimapKey.hashCode();
      }

      @Override public boolean equals(Object o) {
        return o instanceof MultimapBinder
            && ((MultimapBinder<?, ?>) o).multimapKey.equals(multimapKey);
      }

      final class RealProviderMultimapProvider
          extends RealMapBinderProviderWithDependencies<Map<K, Set<Provider<V>>>> {
        private final ImmutableSet<Dependency<?>> dependencies;
        private final Provider<Set<Entry<K, Provider<V>>>> entrySetProvider;
        private Map<K, Set<Provider<V>>> providerMultimap;

        private RealProviderMultimapProvider(ImmutableSet<Dependency<?>> dependencies,
            Provider<Set<Entry<K, Provider<V>>>> entrySetProvider) {
          super(multimapKey);
          this.dependencies = dependencies;
          this.entrySetProvider = entrySetProvider;
        }

        @SuppressWarnings("unused")
        @Inject void initialize(Injector injector) {
          Map<K, ImmutableSet.Builder<Provider<V>>> providerMultimapMutable =
              new LinkedHashMap<K, ImmutableSet.Builder<Provider<V>>>();
          for (Entry<K, Provider<V>> entry : entrySetProvider.get()) {
            if (!providerMultimapMutable.containsKey(entry.getKey())) {
              providerMultimapMutable.put(
                  entry.getKey(), ImmutableSet.<Provider<V>>builder());
            }
            providerMultimapMutable.get(entry.getKey()).add(entry.getValue());
          }

          ImmutableMap.Builder<K, Set<Provider<V>>> providerMultimapBuilder =
              ImmutableMap.builder();
          for (Entry<K, ImmutableSet.Builder<Provider<V>>> entry
              : providerMultimapMutable.entrySet()) {
            providerMultimapBuilder.put(entry.getKey(), entry.getValue().build());
          }
          providerMultimap = providerMultimapBuilder.build();
        }

        @Override public Map<K, Set<Provider<V>>> get() {
          return providerMultimap;
        }

        @Override public Set<Dependency<?>> getDependencies() {
          return dependencies;
        }
      }

      final class RealMultimapProvider
          extends RealMapBinderProviderWithDependencies<Map<K, Set<V>>> {
        private final ImmutableSet<Dependency<?>> dependencies;
        private final Provider<Map<K, Set<Provider<V>>>> multimapProvider;

        RealMultimapProvider(
            ImmutableSet<Dependency<?>> dependencies,
            Provider<Map<K, Set<Provider<V>>>> multimapProvider) {
          super(multimapKey);
          this.dependencies = dependencies;
          this.multimapProvider = multimapProvider;
        }

        @Override public Map<K, Set<V>> get() {
          ImmutableMap.Builder<K, Set<V>> multimapBuilder = ImmutableMap.builder();
          for (Entry<K, Set<Provider<V>>> entry : multimapProvider.get().entrySet()) {
            K key = entry.getKey();
            ImmutableSet.Builder<V> valuesBuilder = ImmutableSet.builder();
            for (Provider<V> valueProvider : entry.getValue()) {
              V value = valueProvider.get();
              checkConfiguration(value != null,
                  "Multimap injection failed due to null value for key \"%s\"", key);
              valuesBuilder.add(value);
            }
            multimapBuilder.put(key, valuesBuilder.build());
          }
          return multimapBuilder.build();
        }

        @Override public Set<Dependency<?>> getDependencies() {
          return dependencies;
        }
      }
    }

    static final class ValueProvider<V> implements Provider<V> {
      private final Provider<V> delegate;
      private final Binding<V> binding;

      ValueProvider(Provider<V> delegate, Binding<V> binding) {
        this.delegate = delegate;
        this.binding = binding;
      }

      @Override public V get() {
        return delegate.get();
      }

      public Binding<V> getValueBinding() {
        return binding;
      }
    }

    /**
     * A Provider that Map.Entry that is also a Provider.  The key is the entry in the
     * map this corresponds to and the value is the provider of the user's binding.
     * This returns itself as the Provider.get value.
     */
    static final class ProviderMapEntry<K, V> implements
        ProviderWithDependencies<Map.Entry<K, Provider<V>>>, Map.Entry<K, Provider<V>> {
      private final K key;
      private final Provider<V> provider;
      private final Key<V> valueKey;

      private ProviderMapEntry(K key, Provider<V> provider, Key<V> valueKey) {
        this.key = key;
        this.provider = provider;
        this.valueKey = valueKey;
      }

      @Override public Entry<K, Provider<V>> get() {
        return this;
      }

      @Override public Set<Dependency<?>> getDependencies() {
        return ((HasDependencies) provider).getDependencies();
      }

      public Key<V> getValueKey() {
        return valueKey;
      }

      @Override public K getKey() {
        return key;
      }

      @Override public Provider<V> getValue() {
        return provider;
      }

      @Override public Provider<V> setValue(Provider<V> value) {
        throw new UnsupportedOperationException();
      }

       @Override public boolean equals(Object obj) {
         if (obj instanceof Map.Entry) {
           Map.Entry o = (Map.Entry)obj;
           return Objects.equal(key, o.getKey())
               && Objects.equal(provider, o.getValue());
         }
         return false;
      }

      @Override public int hashCode() {
        return key.hashCode() ^ provider.hashCode();
      }

      @Override public String toString() {
        return "ProviderMapEntry(" + key + ", " + provider + ")";
      }
    }

    private static abstract class RealMapWithExtensionProvider<T>
        extends RealMapBinderProviderWithDependencies<T>
        implements ProviderWithExtensionVisitor<T>, MapBinderBinding<T> {
      public RealMapWithExtensionProvider(Object equality) {
        super(equality);
      }
    }

    /**
     * A base class for ProviderWithDependencies that need equality
     * based on a specific object.
     */
    private static abstract class RealMapBinderProviderWithDependencies<T> implements ProviderWithDependencies<T> {
      private final Object equality;

      public RealMapBinderProviderWithDependencies(Object equality) {
        this.equality = equality;
      }

      @Override
      public boolean equals(Object obj) {
        return this.getClass() == obj.getClass() &&
          equality.equals(((RealMapBinderProviderWithDependencies<?>)obj).equality);
      }

      @Override
      public int hashCode() {
        return equality.hashCode();
      }
    }

    private Multimap<K, String> newLinkedKeyArrayValueMultimap() {
      return Multimaps.newListMultimap(
          new LinkedHashMap<K, Collection<String>>(),
          new Supplier<List<String>>() {
            @Override public List<String> get() {
              return Lists.newArrayList();
            }
          });
    }
  }
}
