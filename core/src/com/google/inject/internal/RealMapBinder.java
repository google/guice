package com.google.inject.internal;

import static com.google.inject.internal.Element.Type.MAPBINDER;
import static com.google.inject.internal.Errors.checkConfiguration;
import static com.google.inject.internal.Errors.checkNotNull;
import static com.google.inject.internal.RealMultibinder.setOf;
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
import com.google.inject.internal.Indexer.IndexedBinding;
import com.google.inject.internal.InternalProviderInstanceBindingImpl.InitializationTiming;
import com.google.inject.multibindings.MapBinderBinding;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.MultibindingsTargetVisitor;
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
import java.util.Set;
import java.util.Map.Entry;

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
 */
public final class RealMapBinder<K, V> implements Module {

  /**
   * Returns a new mapbinder that collects entries of {@code keyType}/{@code valueType} in a
   * {@link Map} that is itself bound with no binding annotation.
   */
  public static <K, V> RealMapBinder<K, V> newMapRealBinder(Binder binder,
      TypeLiteral<K> keyType, TypeLiteral<V> valueType) {
    binder = binder.skipSources(RealMapBinder.class);
    return 
        newRealMapBinder(binder, keyType, valueType, Key.get(mapOf(keyType, valueType)),
        RealMultibinder.newRealSetBinder(binder, Key.get(entryOfProviderOf(keyType, valueType))));
  }

  /**
   * Returns a new mapbinder that collects entries of {@code keyType}/{@code valueType} in a
   * {@link Map} that is itself bound with {@code annotation}.
   */
  public static <K, V> RealMapBinder<K, V> newRealMapBinder(Binder binder,
      TypeLiteral<K> keyType, TypeLiteral<V> valueType, Annotation annotation) {
    binder = binder.skipSources(RealMapBinder.class);
    return newRealMapBinder(binder, keyType, valueType,
        Key.get(mapOf(keyType, valueType), annotation),
        RealMultibinder.newRealSetBinder(binder, Key.get(entryOfProviderOf(keyType, valueType), annotation)));
  }

  /**
   * Returns a new mapbinder that collects entries of {@code keyType}/{@code valueType} in a
   * {@link Map} that is itself bound with {@code annotationType}.
   */
  public static <K, V> RealMapBinder<K, V> newRealMapBinder(Binder binder, TypeLiteral<K> keyType,
      TypeLiteral<V> valueType, Class<? extends Annotation> annotationType) {
    binder = binder.skipSources(RealMapBinder.class);
    return newRealMapBinder(binder, keyType, valueType,
        Key.get(mapOf(keyType, valueType), annotationType),
        RealMultibinder.newRealSetBinder(binder, Key.get(entryOfProviderOf(keyType, valueType), annotationType)));
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

  @SuppressWarnings("unchecked") // a provider map <K, Set<V>> is safely a Map<K, Set<Provider<V>>>
  static <K, V> TypeLiteral<Map<K, Set<javax.inject.Provider<V>>>> mapOfSetOfJavaxProviderOf(
      TypeLiteral<K> keyType, TypeLiteral<V> valueType) {
    return (TypeLiteral<Map<K, Set<javax.inject.Provider<V>>>>) TypeLiteral.get(
        Types.mapOf(keyType.getType(), Types.setOf(Types.javaxProviderOf(valueType.getType()))));
  }

  @SuppressWarnings("unchecked") // a provider map <K, Set<V>> is safely a Map<K, Set<Provider<V>>>
  static <K, V> TypeLiteral<Map<K, Collection<Provider<V>>>> mapOfCollectionOfProviderOf(
      TypeLiteral<K> keyType, TypeLiteral<V> valueType) {
    return (TypeLiteral<Map<K, Collection<Provider<V>>>>) TypeLiteral.get(
        Types.mapOf(keyType.getType(), Types.collectionOf(Types.providerOf(valueType.getType()))));
  }

  @SuppressWarnings("unchecked") // a provider map <K, Set<V>> is safely a Map<K, Set<Provider<V>>>
  static <K, V> TypeLiteral<Map<K, Collection<javax.inject.Provider<V>>>> mapOfCollectionOfJavaxProviderOf(
      TypeLiteral<K> keyType, TypeLiteral<V> valueType) {
    return (TypeLiteral<Map<K, Collection<javax.inject.Provider<V>>>>) TypeLiteral.get(
        Types.mapOf(keyType.getType(), 
            Types.collectionOf(Types.javaxProviderOf(valueType.getType()))));
  }

  @SuppressWarnings("unchecked") // a provider entry <K, V> is safely a Map.Entry<K, Provider<V>>
  static <K, V> TypeLiteral<Map.Entry<K, Provider<V>>> entryOfProviderOf(
      TypeLiteral<K> keyType, TypeLiteral<V> valueType) {
    return (TypeLiteral<Entry<K, Provider<V>>>) TypeLiteral.get(newParameterizedTypeWithOwner(
        Map.class, Entry.class, keyType.getType(), Types.providerOf(valueType.getType())));
  }
  
  @SuppressWarnings("unchecked") // a provider entry <K, V> is safely a Map.Entry<K, Provider<V>>
  static <K, V> TypeLiteral<Map.Entry<K, Provider<V>>> entryOfJavaxProviderOf(
      TypeLiteral<K> keyType, TypeLiteral<V> valueType) {
    return (TypeLiteral<Entry<K, Provider<V>>>) TypeLiteral.get(newParameterizedTypeWithOwner(
        Map.class, Entry.class, keyType.getType(), Types.javaxProviderOf(valueType.getType())));
  }

  @SuppressWarnings("unchecked") // a provider entry <K, V> is safely a Map.Entry<K, Provider<V>>
  static <K, V> TypeLiteral<Set<Map.Entry<K, javax.inject.Provider<V>>>> setOfEntryOfJavaxProviderOf(
      TypeLiteral<K> keyType, TypeLiteral<V> valueType) {
    return (TypeLiteral<Set<Entry<K, javax.inject.Provider<V>>>>)
        TypeLiteral.get(Types.setOf(entryOfJavaxProviderOf(keyType, valueType).getType()));
  }

  // Note: We use valueTypeAndAnnotation effectively as a Pair<TypeLiteral, Annotation|Class>
  // since it's an easy way to group a type and an optional annotation type or instance.
  static <K, V> RealMapBinder<K, V> newRealMapBinder(Binder binder, TypeLiteral<K> keyType,
      Key<V> valueTypeAndAnnotation) {
    binder = binder.skipSources(RealMapBinder.class);
    TypeLiteral<V> valueType = valueTypeAndAnnotation.getTypeLiteral();
    return newRealMapBinder(binder, keyType, valueType,
        valueTypeAndAnnotation.ofType(mapOf(keyType, valueType)),
        RealMultibinder.newRealSetBinder(binder,
            valueTypeAndAnnotation.ofType(entryOfProviderOf(keyType, valueType))));
  }

  private static <K, V> RealMapBinder<K, V> newRealMapBinder(Binder binder,
      TypeLiteral<K> keyType, TypeLiteral<V> valueType, Key<Map<K, V>> mapKey,
      RealMultibinder<Entry<K, Provider<V>>> entrySetBinder) {
    RealMapBinder<K, V> mapBinder =
        new RealMapBinder<K, V>(binder, keyType, valueType, mapKey, entrySetBinder);
    binder.install(mapBinder);
    return mapBinder;
  }
  
  private final TypeLiteral<K> keyType;
  private final TypeLiteral<V> valueType;
  private final Key<Map<K, V>> mapKey;
  private final Key<Map<K, javax.inject.Provider<V>>> javaxProviderMapKey;
  private final Key<Map<K, Provider<V>>> providerMapKey;
  private final Key<Map<K, Set<V>>> multimapKey;
  private final Key<Map<K, Set<Provider<V>>>> providerSetMultimapKey;
  private final Key<Map<K, Set<javax.inject.Provider<V>>>> javaxProviderSetMultimapKey;
  private final Key<Map<K, Collection<Provider<V>>>> providerCollectionMultimapKey;
  private final Key<Map<K, Collection<javax.inject.Provider<V>>>> javaxProviderCollectionMultimapKey;
  private final Key<Set<Map.Entry<K, javax.inject.Provider<V>>>> entrySetJavaxProviderKey;
  private final RealMultibinder<Map.Entry<K, Provider<V>>> entrySetBinder;

  /* the target injector's binder. non-null until initialization, null afterwards */
  private Binder binder;

  private boolean permitDuplicates;
  private ImmutableList<Map.Entry<K, Binding<V>>> mapBindings;

  RealMapBinder(Binder binder, TypeLiteral<K> keyType, TypeLiteral<V> valueType,
      Key<Map<K, V>> mapKey, RealMultibinder<Map.Entry<K, Provider<V>>> entrySetBinder) {
    this.keyType = keyType;
    this.valueType = valueType;
    this.mapKey = mapKey;
    this.providerMapKey = mapKey.ofType(mapOfProviderOf(keyType, valueType));
    this.javaxProviderMapKey = mapKey.ofType(mapOfJavaxProviderOf(keyType, valueType));
    this.multimapKey = mapKey.ofType(mapOf(keyType, setOf(valueType)));
    this.providerSetMultimapKey = mapKey.ofType(mapOfSetOfProviderOf(keyType, valueType));
    this.javaxProviderSetMultimapKey = mapKey.ofType(mapOfSetOfJavaxProviderOf(keyType, valueType));
    this.providerCollectionMultimapKey = mapKey.ofType(mapOfCollectionOfProviderOf(keyType, valueType));
    this.javaxProviderCollectionMultimapKey = mapKey.ofType(mapOfCollectionOfJavaxProviderOf(keyType, valueType));
    this.entrySetJavaxProviderKey = mapKey.ofType(setOfEntryOfJavaxProviderOf(keyType, valueType));
    this.entrySetBinder = (RealMultibinder<Entry<K, Provider<V>>>) entrySetBinder;
    this.binder = binder;
  }

  public void permitDuplicates() {
    entrySetBinder.permitDuplicates();
    binder.install(new MultimapBinder<K, V>(
        multimapKey, providerSetMultimapKey, javaxProviderSetMultimapKey,
        providerCollectionMultimapKey, javaxProviderCollectionMultimapKey, entrySetBinder));
  }
  
  /** 
   * Adds a binding to the map for the given key.
   */
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
  public LinkedBindingBuilder<V> addBinding(K key) {
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

    // The Map.Entries are all ProviderMapEntry instances which do not allow setValue, so it is
    // safe to massage the return type like this
    @SuppressWarnings("unchecked")
    Key<Set<Entry<K,javax.inject.Provider<V>>>> massagedEntrySetProviderKey =
        (Key) entrySetBinder.getSetKey();
    binder.bind(entrySetJavaxProviderKey).to(massagedEntrySetProviderKey);
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
          || key.equals(providerSetMultimapKey)
          || key.equals(javaxProviderSetMultimapKey)
          || key.equals(providerCollectionMultimapKey)
          || key.equals(javaxProviderCollectionMultimapKey)
          || key.equals(entrySetBinder.getSetKey())
          || key.equals(entrySetJavaxProviderKey)
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
            sb.append("\"" + key + "\", from bindings:\n");
          } else {
            sb.append("\n and key: \"" + key + "\", from bindings:\n");
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
    private final Key<Map<K, Set<javax.inject.Provider<V>>>> javaxProviderMultimapKey;
    private final Key<Map<K, Collection<javax.inject.Provider<V>>>> javaxProviderCollectionMultimapKey;
    private final Key<Map<K, Collection<Provider<V>>>> providerCollectionMultimapKey;
    private final RealMultibinder<Map.Entry<K, Provider<V>>> entrySetBinder;

    public MultimapBinder(
        Key<Map<K, Set<V>>> multimapKey,
        Key<Map<K, Set<Provider<V>>>> providerSetMultimapKey,
        Key<Map<K, Set<javax.inject.Provider<V>>>> javaxProviderSetMultimapKey,
        Key<Map<K, Collection<Provider<V>>>> providerCollectionMultimapKey,
        Key<Map<K, Collection<javax.inject.Provider<V>>>> javaxProviderCollectionMultimapKey,
        RealMultibinder<Map.Entry<K, Provider<V>>> entrySetBinder) {
      this.multimapKey = multimapKey;
      this.providerMultimapKey = providerSetMultimapKey;
      this.javaxProviderMultimapKey = javaxProviderSetMultimapKey;
      this.providerCollectionMultimapKey = providerCollectionMultimapKey;
      this.javaxProviderCollectionMultimapKey = javaxProviderCollectionMultimapKey;
      this.entrySetBinder = entrySetBinder;
    }

    @Override public void configure(Binder binder) {
      ImmutableSet<Dependency<?>> dependencies
          = ImmutableSet.<Dependency<?>>of(Dependency.get(entrySetBinder.getSetKey()));

      // Binds a Map<K, Set<Provider<V>>>
      Provider<Map<K, Set<Provider<V>>>> multimapProvider =
          new RealProviderMultimapProvider<K, V>(entrySetBinder, multimapKey);
      binder.bind(providerMultimapKey).toProvider(multimapProvider);

      // Provide links from a few different public keys to the providerMultimapKey.
      // The collection this exposes is internally an ImmutableMap, so it's OK to massage
      // the guice Provider to javax Provider in the value (since the guice Provider implements
      // javax Provider).
      @SuppressWarnings("unchecked")
      Provider<Map<K, Set<javax.inject.Provider<V>>>> javaxProvider = (Provider) multimapProvider;
      binder.bind(javaxProviderMultimapKey).toProvider(javaxProvider);

      @SuppressWarnings("unchecked")
      Provider<Map<K, Collection<Provider<V>>>> collectionProvider = (Provider) multimapProvider;
      binder.bind(providerCollectionMultimapKey).toProvider(collectionProvider);

      @SuppressWarnings("unchecked")
      Provider<Map<K, Collection<javax.inject.Provider<V>>>> collectionJavaxProvider =
          (Provider) multimapProvider;
      binder.bind(javaxProviderCollectionMultimapKey).toProvider(collectionJavaxProvider);

      // Binds a Map<K, Set<V>>
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

    private static final class RealProviderMultimapProvider<K, V>
        extends InternalProviderInstanceBindingImpl.Factory<Map<K, Set<Provider<V>>>> {
      // prior to initialization we declare just a dependency on the injector, but as soon as we are
      // initialized we swap to dependencies on the elements.
      private static final ImmutableSet<Dependency<?>> MODULE_DEPENDENCIES =
          ImmutableSet.<Dependency<?>>of(Dependency.get(Key.get(Injector.class)));

      // Starts out as Injector and gets set up properly after initialization
      private ImmutableSet<Dependency<?>> dependencies = MODULE_DEPENDENCIES;
      private Map<K, Set<Provider<V>>> providerMultimap;
      
      private final RealMultibinder<Map.Entry<K, Provider<V>>> entrySetBinder;
      private final Key<Map<K, Set<V>>> multimapKey;

      RealProviderMultimapProvider(
          RealMultibinder<Map.Entry<K, Provider<V>>> entrySetBinder,
          Key<Map<K, Set<V>>> multimapKey) {
        // While MapBinders only depend on bindings created in modules so we could theoretically
        // initialize eagerly, they also depend on
        // 1. findBindingsByType returning results
        // 2. being able to call BindingImpl.acceptTargetVisitor
        // neither of those is available during eager initialization, so we use DELAYED
        super(InitializationTiming.DELAYED);

        this.entrySetBinder = entrySetBinder;
        this.multimapKey = multimapKey;
      }

      @Override void initialize(InjectorImpl injector, Errors errors) {
        // We now build the Map<K, Set<Provider<V>>> from the entrySetBinder.
        // The entrySetBinder contains all of the ProviderMapEntrys, and once
        // we have those, it's easy to iterate through them to organize them by K.
        Map<K, ImmutableSet.Builder<Provider<V>>> providerMultimapMutable =
            new LinkedHashMap<K, ImmutableSet.Builder<Provider<V>>>();
        List<Dependency<?>> dependencies = Lists.newArrayList();
        Set<Indexer.IndexedBinding> index = Sets.newHashSet();
        Indexer indexer = new Indexer(injector);
        TypeLiteral<?> elementType = entrySetBinder.getElementTypeLiteral();
        // We get all of the Bindings that were put into the entrySetBinder
        for (Binding<?> binding : injector.findBindingsByType(elementType)) {
          if (entrySetBinder.containsElement(binding)) {
            @SuppressWarnings("unchecked") // protected by findBindingsByType()
            ProviderInstanceBinding<ProviderMapEntry<K, V>> entryBinding =
                (ProviderInstanceBinding<ProviderMapEntry<K, V>>) binding;
            // Use the indexer to de-dupe user bindings. This is needed because of the
            // uniqueId in RealElement. The uniqueId intentionally circumvents the regular
            // Guice deduplication, so we need to re-implement our own here, ignoring
            // uniqueId.
            if (index.add(entryBinding.acceptTargetVisitor(indexer))) {
              @SuppressWarnings("unchecked")
              // We added all these bindings initially, so we know they are ProviderMapEntrys
              ProviderMapEntry<K, V> entry =
                  (ProviderMapEntry) entryBinding.getUserSuppliedProvider();

              // Create a set builder for this key if it's the first time we've seen it
              if (!providerMultimapMutable.containsKey(entry.getKey())) {
                providerMultimapMutable.put(
                    entry.getKey(), ImmutableSet.<Provider<V>>builder());
              }
              // Add a dependency for this element
              dependencies.add(Dependency.get(entryBinding.getKey()));

              // Add the Provider<V>
              providerMultimapMutable.get(entry.getKey()).add(entry.getValue());
            }
          }
        }

        // Build all of the ImmutableSet.Builders,
        // transforming from Map<K, ImmutableSet.Builder<Provider<V>>> to
        // ImmutableMap<K, Set<Provider<V>>>
        ImmutableMap.Builder<K, Set<Provider<V>>> providerMultimapBuilder =
            ImmutableMap.builder();
        for (Entry<K, ImmutableSet.Builder<Provider<V>>> entry
            : providerMultimapMutable.entrySet()) {
          providerMultimapBuilder.put(entry.getKey(), entry.getValue().build());
        }
        providerMultimap = providerMultimapBuilder.build();

        this.dependencies = ImmutableSet.copyOf(dependencies);
      }

      @Override protected Map<K, Set<Provider<V>>> doProvision(
          Errors errors, InternalContext context, Dependency<?> dependency) {
        return providerMultimap;
      }

      @Override public Set<Dependency<?>> getDependencies() {
        return dependencies;
      }

      @Override public boolean equals(Object obj) {
        return obj != null && this.getClass() == obj.getClass()
          && multimapKey.equals(((RealProviderMultimapProvider<?, ?>) obj).multimapKey);
      }

      @Override public int hashCode() {
        return multimapKey.hashCode();
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
      return obj != null && this.getClass() == obj.getClass() &&
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
