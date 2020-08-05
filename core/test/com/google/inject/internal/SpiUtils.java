/*
 * Copyright (C) 2010 Google Inc.
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

package com.google.inject.internal;

import static com.google.common.truth.Truth.assertThat;
import static com.google.inject.internal.RealMapBinder.entryOfJavaxProviderOf;
import static com.google.inject.internal.RealMapBinder.entryOfProviderOf;
import static com.google.inject.internal.RealMapBinder.mapOf;
import static com.google.inject.internal.RealMapBinder.mapOfCollectionOfJavaxProviderOf;
import static com.google.inject.internal.RealMapBinder.mapOfCollectionOfProviderOf;
import static com.google.inject.internal.RealMapBinder.mapOfJavaxProviderOf;
import static com.google.inject.internal.RealMapBinder.mapOfProviderOf;
import static com.google.inject.internal.RealMapBinder.mapOfSetOfJavaxProviderOf;
import static com.google.inject.internal.RealMapBinder.mapOfSetOfProviderOf;
import static com.google.inject.internal.RealMultibinder.collectionOfJavaxProvidersOf;
import static com.google.inject.internal.RealMultibinder.collectionOfProvidersOf;
import static com.google.inject.internal.RealMultibinder.setOf;
import static com.google.inject.internal.RealMultibinder.setOfExtendsOf;
import static com.google.inject.internal.SpiUtils.BindType.INSTANCE;
import static com.google.inject.internal.SpiUtils.BindType.LINKED;
import static com.google.inject.internal.SpiUtils.BindType.PROVIDER_INSTANCE;
import static com.google.inject.internal.SpiUtils.BindType.PROVIDER_KEY;
import static com.google.inject.internal.SpiUtils.VisitType.BOTH;
import static com.google.inject.internal.SpiUtils.VisitType.INJECTOR;
import static com.google.inject.internal.SpiUtils.VisitType.MODULE;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Sets;
import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.Indexer.IndexedBinding;
import com.google.inject.internal.RealMapBinder.ProviderMapEntry;
import com.google.inject.multibindings.MapBinderBinding;
import com.google.inject.multibindings.MultibinderBinding;
import com.google.inject.multibindings.MultibindingsTargetVisitor;
import com.google.inject.multibindings.OptionalBinderBinding;
import com.google.inject.spi.DefaultBindingTargetVisitor;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.google.inject.spi.InstanceBinding;
import com.google.inject.spi.LinkedKeyBinding;
import com.google.inject.spi.ProviderInstanceBinding;
import com.google.inject.spi.ProviderKeyBinding;
import com.google.inject.spi.ProviderLookup;
import com.google.inject.util.Types;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utilities for testing the Multibinder & MapBinder extension SPI.
 *
 * @author sameb@google.com (Sam Berlin)
 */
public class SpiUtils {

  /** The kind of test we should perform. A live Injector, a raw Elements (Module) test, or both. */
  enum VisitType {
    INJECTOR,
    MODULE,
    BOTH
  }

  /**
   * Asserts that MapBinderBinding visitors for work correctly.
   *
   * @param <T> The type of the binding
   * @param mapKey The key the map belongs to.
   * @param keyType the TypeLiteral of the key of the map
   * @param valueType the TypeLiteral of the value of the map
   * @param modules The modules that define the mapbindings
   * @param visitType The kind of test we should perform. A live Injector, a raw Elements (Module)
   *     test, or both.
   * @param allowDuplicates If duplicates are allowed.
   * @param expectedMapBindings The number of other mapbinders we expect to see.
   * @param results The kind of bindings contained in the mapbinder.
   */
  static <T> void assertMapVisitor(
      Key<T> mapKey,
      TypeLiteral<?> keyType,
      TypeLiteral<?> valueType,
      Iterable<? extends Module> modules,
      VisitType visitType,
      boolean allowDuplicates,
      int expectedMapBindings,
      MapResult<?, ?>... results) {
    if (visitType == null) {
      fail("must test something");
    }

    if (visitType == BOTH || visitType == INJECTOR) {
      mapInjectorTest(
          mapKey, keyType, valueType, modules, allowDuplicates, expectedMapBindings, results);
    }

    if (visitType == BOTH || visitType == MODULE) {
      mapModuleTest(
          mapKey, keyType, valueType, modules, allowDuplicates, expectedMapBindings, results);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> void mapInjectorTest(
      Key<T> mapKey,
      TypeLiteral<?> keyType,
      TypeLiteral<?> valueType,
      Iterable<? extends Module> modules,
      boolean allowDuplicates,
      int expectedMapBindings,
      MapResult<?, ?>... results) {
    Injector injector = Guice.createInjector(modules);
    Visitor<T> visitor = new Visitor<>();
    Binding<T> mapBinding = injector.getBinding(mapKey);
    MapBinderBinding<T> mapbinder = (MapBinderBinding<T>) mapBinding.acceptTargetVisitor(visitor);
    assertNotNull(mapbinder);
    assertEquals(mapKey, mapbinder.getMapKey());
    assertEquals(keyType, mapbinder.getKeyTypeLiteral());
    assertEquals(valueType, mapbinder.getValueTypeLiteral());
    assertEquals(allowDuplicates, mapbinder.permitsDuplicates());
    List<Map.Entry<?, Binding<?>>> entries = Lists.newArrayList(mapbinder.getEntries());
    List<MapResult<?, ?>> mapResults = Lists.newArrayList(results);
    assertEquals(
        "wrong entries, expected: " + mapResults + ", but was: " + entries,
        mapResults.size(),
        entries.size());

    for (MapResult<?, ?> result : mapResults) {
      Map.Entry<?, Binding<?>> found = null;
      for (Map.Entry<?, Binding<?>> entry : entries) {
        Object key = entry.getKey();
        Binding<?> value = entry.getValue();
        if (key.equals(result.k) && matches(value, result.v)) {
          found = entry;
          break;
        }
      }
      if (found == null) {
        fail("Could not find entry: " + result + " in remaining entries: " + entries);
      } else {
        assertTrue(
            "mapBinder doesn't contain: " + found.getValue(),
            mapbinder.containsElement(found.getValue()));
        entries.remove(found);
      }
    }

    if (!entries.isEmpty()) {
      fail("Found all entries of: " + mapResults + ", but more were left over: " + entries);
    }

    Key<?> mapOfJavaxProvider = mapKey.ofType(mapOfJavaxProviderOf(keyType, valueType));
    Key<?> mapOfProvider = mapKey.ofType(mapOfProviderOf(keyType, valueType));
    Key<?> mapOfSetOfProvider = mapKey.ofType(mapOfSetOfProviderOf(keyType, valueType));
    Key<?> mapOfSetOfJavaxProvider = mapKey.ofType(mapOfSetOfJavaxProviderOf(keyType, valueType));
    Key<?> mapOfCollectionOfProvider =
        mapKey.ofType(mapOfCollectionOfProviderOf(keyType, valueType));
    Key<?> mapOfCollectionOfJavaxProvider =
        mapKey.ofType(mapOfCollectionOfJavaxProviderOf(keyType, valueType));
    Key<?> mapOfSet = mapKey.ofType(mapOf(keyType, setOf(valueType)));
    Key<?> setOfEntry = mapKey.ofType(setOf(entryOfProviderOf(keyType, valueType)));
    Key<?> setOfJavaxEntry = mapKey.ofType(setOf(entryOfJavaxProviderOf(keyType, valueType)));
    Key<?> collectionOfProvidersOfEntryOfProvider =
        mapKey.ofType(collectionOfProvidersOf(entryOfProviderOf(keyType, valueType)));
    Key<?> collectionOfJavaxProvidersOfEntryOfProvider =
        mapKey.ofType(collectionOfJavaxProvidersOf(entryOfProviderOf(keyType, valueType)));
    Key<?> setOfExtendsOfEntryOfProvider =
        mapKey.ofType(setOfExtendsOf(entryOfProviderOf(keyType, valueType)));
    Key<?> mapOfKeyExtendsValueKey =
        mapKey.ofType(mapOf(keyType, TypeLiteral.get(Types.subtypeOf(valueType.getType()))));

    assertEquals(
        ImmutableSet.of(
            mapOfJavaxProvider,
            mapOfProvider,
            mapOfSetOfProvider,
            mapOfSetOfJavaxProvider,
            mapOfCollectionOfProvider,
            mapOfCollectionOfJavaxProvider,
            mapOfSet,
            mapOfKeyExtendsValueKey),
        mapbinder.getAlternateMapKeys());

    boolean entrySetMatch = false;
    boolean javaxEntrySetMatch = false;
    boolean mapJavaxProviderMatch = false;
    boolean mapProviderMatch = false;
    boolean mapSetMatch = false;
    boolean mapSetProviderMatch = false;
    boolean mapSetJavaxProviderMatch = false;
    boolean mapCollectionProviderMatch = false;
    boolean mapCollectionJavaxProviderMatch = false;
    boolean collectionOfProvidersOfEntryOfProviderMatch = false;
    boolean collectionOfJavaxProvidersOfEntryOfProviderMatch = false;
    boolean setOfExtendsOfEntryOfProviderMatch = false;
    boolean mapOfKeyExtendsValueKeyMatch = false;
    List<Object> otherMapBindings = Lists.newArrayList();
    List<Binding<?>> otherMatches = Lists.newArrayList();
    Multimap<Object, IndexedBinding> indexedEntries =
        MultimapBuilder.hashKeys().hashSetValues().build();
    Indexer indexer = new Indexer(injector);
    int duplicates = 0;
    for (Binding<?> b : injector.getAllBindings().values()) {
      boolean contains = mapbinder.containsElement(b);
      Object visited = ((Binding<T>) b).acceptTargetVisitor(visitor);
      if (visited instanceof MapBinderBinding) {
        if (visited.equals(mapbinder)) {
          assertTrue(contains);
        } else {
          otherMapBindings.add(visited);
        }
      } else if (b.getKey().equals(mapOfProvider)) {
        assertTrue(contains);
        mapProviderMatch = true;
      } else if (b.getKey().equals(mapOfJavaxProvider)) {
        assertTrue(contains);
        mapJavaxProviderMatch = true;
      } else if (b.getKey().equals(mapOfSet)) {
        assertTrue(contains);
        mapSetMatch = true;
      } else if (b.getKey().equals(mapOfSetOfProvider)) {
        assertTrue(contains);
        mapSetProviderMatch = true;
      } else if (b.getKey().equals(mapOfSetOfJavaxProvider)) {
        assertTrue(contains);
        mapSetJavaxProviderMatch = true;
      } else if (b.getKey().equals(mapOfCollectionOfProvider)) {
        assertTrue(contains);
        mapCollectionProviderMatch = true;
      } else if (b.getKey().equals(mapOfCollectionOfJavaxProvider)) {
        assertTrue(contains);
        mapCollectionJavaxProviderMatch = true;
      } else if (b.getKey().equals(setOfEntry)) {
        assertTrue(contains);
        entrySetMatch = true;
        // Validate that this binding is also a MultibinderBinding.
        assertThat(((Binding<T>) b).acceptTargetVisitor(visitor))
            .isInstanceOf(MultibinderBinding.class);
      } else if (b.getKey().equals(setOfJavaxEntry)) {
        assertTrue(contains);
        javaxEntrySetMatch = true;
      } else if (b.getKey().equals(collectionOfProvidersOfEntryOfProvider)) {
        assertTrue(contains);
        collectionOfProvidersOfEntryOfProviderMatch = true;
      } else if (b.getKey().equals(collectionOfJavaxProvidersOfEntryOfProvider)) {
        assertTrue(contains);
        collectionOfJavaxProvidersOfEntryOfProviderMatch = true;
      } else if (b.getKey().equals(setOfExtendsOfEntryOfProvider)) {
        assertTrue(contains);
        setOfExtendsOfEntryOfProviderMatch = true;
      } else if (b.getKey().equals(mapOfKeyExtendsValueKey)) {
        assertTrue(contains);
        mapOfKeyExtendsValueKeyMatch = true;
      } else if (contains) {
        if (b instanceof ProviderInstanceBinding) {
          ProviderInstanceBinding<?> pib = (ProviderInstanceBinding<?>) b;
          if (pib.getUserSuppliedProvider() instanceof ProviderMapEntry) {
            // weird casting required to workaround compilation issues with jdk6
            ProviderMapEntry<?, ?> pme =
                (ProviderMapEntry<?, ?>) (Provider) pib.getUserSuppliedProvider();
            Binding<?> valueBinding = injector.getBinding(pme.getValueKey());
            if (indexer.isIndexable(valueBinding)
                && !indexedEntries.put(pme.getKey(), valueBinding.acceptTargetVisitor(indexer))) {
              duplicates++;
            }
          }
        }
        otherMatches.add(b);
      }
    }

    int sizeOfOther = otherMatches.size();
    if (allowDuplicates) {
      sizeOfOther--; // account for 1 duplicate binding
    }
    // Multiply by two because each has a value and Map.Entry.
    int expectedSize = 2 * (mapResults.size() + duplicates);
    assertEquals(
        "Incorrect other matches:\n\t" + Joiner.on("\n\t").join(otherMatches),
        expectedSize,
        sizeOfOther);
    assertTrue(entrySetMatch);
    assertTrue(javaxEntrySetMatch);
    assertTrue(mapProviderMatch);
    assertTrue(mapJavaxProviderMatch);
    assertTrue(collectionOfProvidersOfEntryOfProviderMatch);
    assertTrue(collectionOfJavaxProvidersOfEntryOfProviderMatch);
    assertTrue(setOfExtendsOfEntryOfProviderMatch);
    assertTrue(mapOfKeyExtendsValueKeyMatch);
    assertEquals(allowDuplicates, mapSetMatch);
    assertEquals(allowDuplicates, mapSetProviderMatch);
    assertEquals(allowDuplicates, mapSetJavaxProviderMatch);
    assertEquals(allowDuplicates, mapCollectionJavaxProviderMatch);
    assertEquals(allowDuplicates, mapCollectionProviderMatch);
    assertEquals(
        "other MapBindings found: " + otherMapBindings,
        expectedMapBindings,
        otherMapBindings.size());
  }

  @SuppressWarnings("unchecked")
  private static <T> void mapModuleTest(
      Key<T> mapKey,
      TypeLiteral<?> keyType,
      TypeLiteral<?> valueType,
      Iterable<? extends Module> modules,
      boolean allowDuplicates,
      int expectedMapBindings,
      MapResult<?, ?>... results) {
    Set<Element> elements = ImmutableSet.copyOf(Elements.getElements(modules));
    Visitor<T> visitor = new Visitor<>();
    MapBinderBinding<T> mapbinder = null;
    Map<Key<?>, Binding<?>> keyMap = Maps.newHashMap();
    for (Element element : elements) {
      if (element instanceof Binding) {
        Binding<?> binding = (Binding<?>) element;
        keyMap.put(binding.getKey(), binding);
        if (binding.getKey().equals(mapKey)) {
          mapbinder = (MapBinderBinding<T>) ((Binding<T>) binding).acceptTargetVisitor(visitor);
        }
      }
    }
    assertNotNull(mapbinder);

    List<MapResult<?, ?>> mapResults = Lists.newArrayList(results);

    // Make sure the entries returned from getEntries(elements) are correct.
    // Because getEntries() can return duplicates, make sure to continue searching, even
    // after we find one match.
    List<Map.Entry<?, Binding<?>>> entries = Lists.newArrayList(mapbinder.getEntries(elements));
    for (MapResult<?, ?> result : mapResults) {
      List<Map.Entry<?, Binding<?>>> foundEntries = Lists.newArrayList();
      for (Map.Entry<?, Binding<?>> entry : entries) {
        Object key = entry.getKey();
        Binding<?> value = entry.getValue();
        if (key.equals(result.k) && matches(value, result.v)) {
          assertTrue(
              "mapBinder doesn't contain: " + entry.getValue(),
              mapbinder.containsElement(entry.getValue()));
          foundEntries.add(entry);
        }
      }
      assertTrue(
          "Could not find entry: " + result + " in remaining entries: " + entries,
          !foundEntries.isEmpty());

      entries.removeAll(foundEntries);
    }

    assertTrue(
        "Found all entries of: " + mapResults + ", but more were left over: " + entries,
        entries.isEmpty());

    assertEquals(mapKey, mapbinder.getMapKey());
    assertEquals(keyType, mapbinder.getKeyTypeLiteral());
    assertEquals(valueType, mapbinder.getValueTypeLiteral());

    Key<?> mapOfProvider = mapKey.ofType(mapOfProviderOf(keyType, valueType));
    Key<?> mapOfJavaxProvider = mapKey.ofType(mapOfJavaxProviderOf(keyType, valueType));
    Key<?> mapOfSetOfProvider = mapKey.ofType(mapOfSetOfProviderOf(keyType, valueType));
    Key<?> mapOfSetOfJavaxProvider = mapKey.ofType(mapOfSetOfJavaxProviderOf(keyType, valueType));
    Key<?> mapOfCollectionOfProvider =
        mapKey.ofType(mapOfCollectionOfProviderOf(keyType, valueType));
    Key<?> mapOfCollectionOfJavaxProvider =
        mapKey.ofType(mapOfCollectionOfJavaxProviderOf(keyType, valueType));
    Key<?> mapOfSet = mapKey.ofType(mapOf(keyType, setOf(valueType)));
    Key<?> setOfEntry = mapKey.ofType(setOf(entryOfProviderOf(keyType, valueType)));
    Key<?> setOfJavaxEntry = mapKey.ofType(setOf(entryOfJavaxProviderOf(keyType, valueType)));
    Key<?> collectionOfProvidersOfEntryOfProvider =
        mapKey.ofType(collectionOfProvidersOf(entryOfProviderOf(keyType, valueType)));
    Key<?> collectionOfJavaxProvidersOfEntryOfProvider =
        mapKey.ofType(collectionOfJavaxProvidersOf(entryOfProviderOf(keyType, valueType)));
    Key<?> setOfExtendsOfEntryOfProvider =
        mapKey.ofType(setOfExtendsOf(entryOfProviderOf(keyType, valueType)));
    Key<?> mapOfKeyExtendsValueKey =
        mapKey.ofType(mapOf(keyType, TypeLiteral.get(Types.subtypeOf(valueType.getType()))));

    assertEquals(
        ImmutableSet.of(
            mapOfProvider,
            mapOfJavaxProvider,
            mapOfSetOfProvider,
            mapOfSetOfJavaxProvider,
            mapOfCollectionOfProvider,
            mapOfCollectionOfJavaxProvider,
            mapOfSet,
            mapOfKeyExtendsValueKey),
        mapbinder.getAlternateMapKeys());

    boolean entrySetMatch = false;
    boolean entrySetJavaxMatch = false;
    boolean mapProviderMatch = false;
    boolean mapJavaxProviderMatch = false;
    boolean mapSetMatch = false;
    boolean mapSetProviderMatch = false;
    boolean mapSetJavaxProviderMatch = false;
    boolean mapCollectionProviderMatch = false;
    boolean mapCollectionJavaxProviderMatch = false;
    boolean collectionOfProvidersOfEntryOfProviderMatch = false;
    boolean collectionOfJavaxProvidersOfEntryOfProviderMatch = false;
    boolean setOfExtendsOfEntryOfProviderMatch = false;
    boolean mapOfKeyExtendsValueKeyMatch = false;
    List<Object> otherMapBindings = Lists.newArrayList();
    List<Element> otherMatches = Lists.newArrayList();
    List<Element> otherElements = Lists.newArrayList();
    Indexer indexer = new Indexer(null);
    Multimap<Object, IndexedBinding> indexedEntries =
        MultimapBuilder.hashKeys().hashSetValues().build();
    int duplicates = 0;
    for (Element element : elements) {
      boolean contains = mapbinder.containsElement(element);
      if (!contains) {
        otherElements.add(element);
      }
      boolean matched = false;
      Key<T> key = null;
      Binding<T> b = null;
      if (element instanceof Binding) {
        b = (Binding) element;
        if (b instanceof ProviderInstanceBinding) {
          ProviderInstanceBinding<?> pb = (ProviderInstanceBinding<?>) b;
          if (pb.getUserSuppliedProvider() instanceof ProviderMapEntry) {
            // weird casting required to workaround jdk6 compilation problems
            ProviderMapEntry<?, ?> pme =
                (ProviderMapEntry<?, ?>) (Provider) pb.getUserSuppliedProvider();
            Binding<?> valueBinding = keyMap.get(pme.getValueKey());
            if (indexer.isIndexable(valueBinding)
                && !indexedEntries.put(pme.getKey(), valueBinding.acceptTargetVisitor(indexer))) {
              duplicates++;
            }
          }
        }

        key = b.getKey();
        Object visited = b.acceptTargetVisitor(visitor);
        if (visited instanceof MapBinderBinding) {
          matched = true;
          if (visited.equals(mapbinder)) {
            assertTrue(contains);
          } else {
            otherMapBindings.add(visited);
          }
        }
      } else if (element instanceof ProviderLookup) {
        key = ((ProviderLookup) element).getKey();
      }

      if (!matched && key != null) {
        if (key.equals(mapOfProvider)) {
          matched = true;
          assertTrue(contains);
          mapProviderMatch = true;
        } else if (key.equals(mapOfJavaxProvider)) {
          matched = true;
          assertTrue(contains);
          mapJavaxProviderMatch = true;
        } else if (key.equals(mapOfSet)) {
          matched = true;
          assertTrue(contains);
          mapSetMatch = true;
        } else if (key.equals(mapOfSetOfProvider)) {
          matched = true;
          assertTrue(contains);
          mapSetProviderMatch = true;
        } else if (key.equals(mapOfSetOfJavaxProvider)) {
          matched = true;
          assertTrue(contains);
          mapSetJavaxProviderMatch = true;
        } else if (key.equals(mapOfCollectionOfProvider)) {
          matched = true;
          assertTrue(contains);
          mapCollectionProviderMatch = true;
        } else if (key.equals(mapOfCollectionOfJavaxProvider)) {
          matched = true;
          assertTrue(contains);
          mapCollectionJavaxProviderMatch = true;
        } else if (key.equals(setOfEntry)) {
          matched = true;
          assertTrue(contains);
          entrySetMatch = true;
          // Validate that this binding is also a MultibinderBinding.
          if (b != null) {
            assertTrue(b.acceptTargetVisitor(visitor) instanceof MultibinderBinding);
          }
        } else if (key.equals(setOfJavaxEntry)) {
          matched = true;
          assertTrue(contains);
          entrySetJavaxMatch = true;
        } else if (key.equals(collectionOfProvidersOfEntryOfProvider)) {
          matched = true;
          assertTrue(contains);
          collectionOfProvidersOfEntryOfProviderMatch = true;
        } else if (key.equals(collectionOfJavaxProvidersOfEntryOfProvider)) {
          matched = true;
          assertTrue(contains);
          collectionOfJavaxProvidersOfEntryOfProviderMatch = true;
        } else if (key.equals(setOfExtendsOfEntryOfProvider)) {
          matched = true;
          assertTrue(contains);
          setOfExtendsOfEntryOfProviderMatch = true;
        } else if (key.equals(mapOfKeyExtendsValueKey)) {
          matched = true;
          assertTrue(contains);
          mapOfKeyExtendsValueKeyMatch = true;
        }
      }

      if (!matched && contains) {
        otherMatches.add(element);
      }
    }

    int otherMatchesSize = otherMatches.size();
    if (allowDuplicates) {
      otherMatchesSize--; // allow for 1 duplicate binding
    }
    // Multiply by 2 because each has a value, and Map.Entry
    int expectedSize = (mapResults.size() + duplicates) * 2;
    assertEquals(
        "incorrect number of contains, leftover matches:\n" + Joiner.on("\n\t").join(otherMatches),
        expectedSize,
        otherMatchesSize);

    assertTrue(entrySetMatch);
    assertTrue(entrySetJavaxMatch);
    assertTrue(mapProviderMatch);
    assertTrue(mapJavaxProviderMatch);
    assertTrue(collectionOfProvidersOfEntryOfProviderMatch);
    assertTrue(collectionOfJavaxProvidersOfEntryOfProviderMatch);
    assertTrue(setOfExtendsOfEntryOfProviderMatch);
    assertTrue(mapOfKeyExtendsValueKeyMatch);
    assertEquals(allowDuplicates, mapSetMatch);
    assertEquals(allowDuplicates, mapSetProviderMatch);
    assertEquals(allowDuplicates, mapSetJavaxProviderMatch);
    assertEquals(allowDuplicates, mapCollectionProviderMatch);
    assertEquals(allowDuplicates, mapCollectionJavaxProviderMatch);
    assertEquals(
        "other MapBindings found: " + otherMapBindings,
        expectedMapBindings,
        otherMapBindings.size());

    // Validate that we can construct an injector out of the remaining bindings.
    Guice.createInjector(Elements.getModule(otherElements));
  }

  /**
   * Asserts that MultibinderBinding visitors work correctly.
   *
   * @param <T> The type of the binding
   * @param setKey The key the set belongs to.
   * @param elementType the TypeLiteral of the element
   * @param modules The modules that define the multibindings
   * @param visitType The kind of test we should perform. A live Injector, a raw Elements (Module)
   *     test, or both.
   * @param allowDuplicates If duplicates are allowed.
   * @param expectedMultibindings The number of other multibinders we expect to see.
   * @param results The kind of bindings contained in the multibinder.
   */
  static <T> void assertSetVisitor(
      Key<Set<T>> setKey,
      TypeLiteral<?> elementType,
      Iterable<? extends Module> modules,
      VisitType visitType,
      boolean allowDuplicates,
      int expectedMultibindings,
      BindResult<T>... results) {
    if (visitType == null) {
      fail("must test something");
    }

    if (visitType == BOTH || visitType == INJECTOR) {
      setInjectorTest(
          setKey, elementType, modules, allowDuplicates, expectedMultibindings, results);
    }

    if (visitType == BOTH || visitType == MODULE) {
      setModuleTest(setKey, elementType, modules, allowDuplicates, expectedMultibindings, results);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> void setInjectorTest(
      Key<Set<T>> setKey,
      TypeLiteral<?> elementType,
      Iterable<? extends Module> modules,
      boolean allowDuplicates,
      int otherMultibindings,
      BindResult<T>... results) {
    Key<?> collectionOfProvidersKey = setKey.ofType(collectionOfProvidersOf(elementType));
    Key<?> collectionOfJavaxProvidersKey = setKey.ofType(collectionOfJavaxProvidersOf(elementType));
    Key<?> setOfExtendsKey = setKey.ofType(setOfExtendsOf(elementType));
    Injector injector = Guice.createInjector(modules);
    Visitor<Set<T>> visitor = new Visitor<>();
    Binding<Set<T>> binding = injector.getBinding(setKey);
    MultibinderBinding<Set<T>> multibinder =
        (MultibinderBinding<Set<T>>) binding.acceptTargetVisitor(visitor);
    assertNotNull(multibinder);
    assertEquals(setKey, multibinder.getSetKey());
    assertEquals(elementType, multibinder.getElementTypeLiteral());
    assertEquals(allowDuplicates, multibinder.permitsDuplicates());
    assertEquals(
        ImmutableSet.of(collectionOfProvidersKey, collectionOfJavaxProvidersKey, setOfExtendsKey),
        multibinder.getAlternateSetKeys());
    List<Binding<?>> elements = Lists.newArrayList(multibinder.getElements());
    List<BindResult<?>> bindResults = Lists.newArrayList(results);
    assertEquals(
        "wrong bind elements, expected: " + bindResults + ", but was: " + multibinder.getElements(),
        bindResults.size(),
        elements.size());

    for (BindResult<?> result : bindResults) {
      Binding<?> found = null;
      for (Binding<?> item : elements) {
        if (matches(item, result)) {
          found = item;
          break;
        }
      }
      if (found == null) {
        fail("Could not find element: " + result + " in remaining elements: " + elements);
      } else {
        elements.remove(found);
      }
    }

    if (!elements.isEmpty()) {
      fail("Found all elements of: " + bindResults + ", but more were left over: " + elements);
    }

    Set<Binding<?>> setOfElements = new HashSet<>(multibinder.getElements());
    Set<IndexedBinding> setOfIndexed = Sets.newHashSet();
    Indexer indexer = new Indexer(injector);
    for (Binding<?> oneBinding : setOfElements) {
      setOfIndexed.add(oneBinding.acceptTargetVisitor(indexer));
    }

    List<Object> otherMultibinders = Lists.newArrayList();
    List<Binding<?>> otherContains = Lists.newArrayList();
    boolean collectionOfProvidersMatch = false;
    boolean collectionOfJavaxProvidersMatch = false;
    boolean setOfExtendsKeyMatch = false;
    for (Binding<?> b : injector.getAllBindings().values()) {
      boolean contains = multibinder.containsElement(b);
      Key<?> key = b.getKey();
      Object visited = ((Binding<Set<T>>) b).acceptTargetVisitor(visitor);
      if (visited != null) {
        if (visited.equals(multibinder)) {
          assertTrue(contains);
        } else {
          otherMultibinders.add(visited);
        }
      } else if (setOfElements.contains(b)) {
        assertTrue(contains);
      } else if (key.equals(collectionOfProvidersKey)) {
        assertTrue(contains);
        collectionOfProvidersMatch = true;
      } else if (key.equals(collectionOfJavaxProvidersKey)) {
        assertTrue(contains);
        collectionOfJavaxProvidersMatch = true;
      } else if (key.equals(setOfExtendsKey)) {
        assertTrue(contains);
        setOfExtendsKeyMatch = true;
      } else if (contains) {
        if (!indexer.isIndexable(b) || !setOfIndexed.contains(b.acceptTargetVisitor(indexer))) {
          otherContains.add(b);
        }
      }
    }

    assertTrue(collectionOfProvidersMatch);
    assertTrue(collectionOfJavaxProvidersMatch);
    assertTrue(setOfExtendsKeyMatch);

    if (allowDuplicates) {
      assertEquals("contained more than it should: " + otherContains, 1, otherContains.size());
    } else {
      assertTrue("contained more than it should: " + otherContains, otherContains.isEmpty());
    }
    assertEquals(
        "other multibindings found: " + otherMultibinders,
        otherMultibindings,
        otherMultibinders.size());
  }

  @SuppressWarnings("unchecked")
  private static <T> void setModuleTest(
      Key<Set<T>> setKey,
      TypeLiteral<?> elementType,
      Iterable<? extends Module> modules,
      boolean allowDuplicates,
      int otherMultibindings,
      BindResult<?>... results) {
    Key<?> collectionOfProvidersKey = setKey.ofType(collectionOfProvidersOf(elementType));
    Key<?> collectionOfJavaxProvidersKey = setKey.ofType(collectionOfJavaxProvidersOf(elementType));
    Key<?> setOfExtendsKey = setKey.ofType(setOfExtendsOf(elementType));
    List<BindResult<?>> bindResults = Lists.newArrayList(results);
    List<Element> elements = Elements.getElements(modules);
    Visitor<T> visitor = new Visitor<>();
    MultibinderBinding<Set<T>> multibinder = null;
    for (Element element : elements) {
      if (element instanceof Binding && ((Binding) element).getKey().equals(setKey)) {
        multibinder = (MultibinderBinding<Set<T>>) ((Binding) element).acceptTargetVisitor(visitor);
        break;
      }
    }
    assertNotNull(multibinder);

    assertEquals(setKey, multibinder.getSetKey());
    assertEquals(elementType, multibinder.getElementTypeLiteral());
    assertEquals(
        ImmutableSet.of(collectionOfProvidersKey, collectionOfJavaxProvidersKey, setOfExtendsKey),
        multibinder.getAlternateSetKeys());
    List<Object> otherMultibinders = Lists.newArrayList();
    Set<Element> otherContains = new HashSet<>();
    List<Element> otherElements = Lists.newArrayList();
    int duplicates = 0;
    Set<IndexedBinding> setOfIndexed = Sets.newHashSet();
    Indexer indexer = new Indexer(null);
    boolean collectionOfProvidersMatch = false;
    boolean collectionOfJavaxProvidersMatch = false;
    boolean setOfExtendsMatch = false;
    for (Element element : elements) {
      boolean contains = multibinder.containsElement(element);
      if (!contains) {
        otherElements.add(element);
      }
      boolean matched = false;
      Key<T> key = null;
      if (element instanceof Binding) {
        Binding<T> binding = (Binding) element;
        if (indexer.isIndexable(binding)
            && !setOfIndexed.add((IndexedBinding) binding.acceptTargetVisitor(indexer))) {
          duplicates++;
        }
        key = binding.getKey();
        Object visited = binding.acceptTargetVisitor(visitor);
        if (visited != null) {
          matched = true;
          if (visited.equals(multibinder)) {
            assertTrue(contains);
          } else {
            otherMultibinders.add(visited);
          }
        }
      }

      if (collectionOfProvidersKey.equals(key)) {
        assertTrue(contains);
        assertFalse(matched);
        collectionOfProvidersMatch = true;
      } else if (collectionOfJavaxProvidersKey.equals(key)) {
        assertTrue(contains);
        assertFalse(matched);
        collectionOfJavaxProvidersMatch = true;
      } else if (setOfExtendsKey.equals(key)) {
        assertTrue(contains);
        assertFalse(matched);
        setOfExtendsMatch = true;
      } else if (!matched && contains) {
        otherContains.add(element);
      }
    }

    if (allowDuplicates) {
      assertEquals(
          "wrong contained elements: " + otherContains,
          bindResults.size() + 1 + duplicates,
          otherContains.size());
    } else {
      assertEquals(
          "wrong contained elements: " + otherContains,
          bindResults.size() + duplicates,
          otherContains.size());
    }

    assertEquals(
        "other multibindings found: " + otherMultibinders,
        otherMultibindings,
        otherMultibinders.size());
    assertTrue(collectionOfProvidersMatch);
    assertTrue(collectionOfJavaxProvidersMatch);
    assertTrue(setOfExtendsMatch);

    // Validate that we can construct an injector out of the remaining bindings.
    Guice.createInjector(Elements.getModule(otherElements));
  }

  /**
   * Asserts that OptionalBinderBinding visitors for work correctly.
   *
   * @param <T> The type of the binding
   * @param keyType The key OptionalBinder is binding
   * @param modules The modules that define the bindings
   * @param visitType The kind of test we should perform. A live Injector, a raw Elements (Module)
   *     test, or both.
   * @param expectedOtherOptionalBindings the # of other optional bindings we expect to see.
   * @param expectedDefault the expected default binding, or null if none
   * @param expectedActual the expected actual binding, or null if none
   * @param expectedUserLinkedActual the user binding that is the actual binding, used if neither
   *     the default nor actual are set and a user binding existed for the type.
   */
  static <T> void assertOptionalVisitor(
      Key<T> keyType,
      Iterable<? extends Module> modules,
      VisitType visitType,
      int expectedOtherOptionalBindings,
      BindResult<?> expectedDefault,
      BindResult<?> expectedActual,
      BindResult<?> expectedUserLinkedActual) {
    if (visitType == null) {
      fail("must test something");
    }

    // expect twice as many bindings because of java.util.Optional
    expectedOtherOptionalBindings *= 2;
    if (visitType == BOTH || visitType == INJECTOR) {
      optionalInjectorTest(
          keyType,
          modules,
          expectedOtherOptionalBindings,
          expectedDefault,
          expectedActual,
          expectedUserLinkedActual);
    }

    if (visitType == BOTH || visitType == MODULE) {
      optionalModuleTest(
          keyType,
          modules,
          expectedOtherOptionalBindings,
          expectedDefault,
          expectedActual,
          expectedUserLinkedActual);
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static <T> void optionalInjectorTest(
      Key<T> keyType,
      Iterable<? extends Module> modules,
      int expectedOtherOptionalBindings,
      BindResult<?> expectedDefault,
      BindResult<?> expectedActual,
      BindResult<?> expectedUserLinkedActual) {
    if (expectedUserLinkedActual != null) {
      assertNull("cannot have actual if expecting user binding", expectedActual);
      assertNull("cannot have default if expecting user binding", expectedDefault);
    }

    Key<Optional<T>> optionalKey =
        keyType.ofType(RealOptionalBinder.optionalOf(keyType.getTypeLiteral()));
    Key<?> javaOptionalKey =
        keyType.ofType(RealOptionalBinder.javaOptionalOf(keyType.getTypeLiteral()));
    Injector injector = Guice.createInjector(modules);
    Binding<Optional<T>> optionalBinding = injector.getBinding(optionalKey);
    Visitor visitor = new Visitor();
    OptionalBinderBinding<Optional<T>> optionalBinder =
        (OptionalBinderBinding<Optional<T>>) optionalBinding.acceptTargetVisitor(visitor);
    assertNotNull(optionalBinder);
    assertEquals(optionalKey, optionalBinder.getKey());

    Binding<?> javaOptionalBinding = injector.getBinding(javaOptionalKey);
    OptionalBinderBinding<?> javaOptionalBinder =
        (OptionalBinderBinding<?>) javaOptionalBinding.acceptTargetVisitor(visitor);
    assertNotNull(javaOptionalBinder);
    assertEquals(javaOptionalKey, javaOptionalBinder.getKey());

    if (expectedDefault == null) {
      assertNull("did not expect a default binding", optionalBinder.getDefaultBinding());
      assertNull("did not expect a default binding", javaOptionalBinder.getDefaultBinding());
    } else {
      assertTrue(
          "expectedDefault: "
              + expectedDefault
              + ", actualDefault: "
              + optionalBinder.getDefaultBinding(),
          matches(optionalBinder.getDefaultBinding(), expectedDefault));
      assertTrue(
          "expectedDefault: "
              + expectedDefault
              + ", actualDefault: "
              + javaOptionalBinder.getDefaultBinding(),
          matches(javaOptionalBinder.getDefaultBinding(), expectedDefault));
    }

    if (expectedActual == null && expectedUserLinkedActual == null) {
      assertNull(optionalBinder.getActualBinding());
      assertNull(javaOptionalBinder.getActualBinding());

    } else if (expectedActual != null) {
      assertTrue(
          "expectedActual: "
              + expectedActual
              + ", actualActual: "
              + optionalBinder.getActualBinding(),
          matches(optionalBinder.getActualBinding(), expectedActual));
      assertTrue(
          "expectedActual: "
              + expectedActual
              + ", actualActual: "
              + javaOptionalBinder.getActualBinding(),
          matches(javaOptionalBinder.getActualBinding(), expectedActual));

    } else if (expectedUserLinkedActual != null) {
      assertTrue(
          "expectedUserLinkedActual: "
              + expectedUserLinkedActual
              + ", actualActual: "
              + optionalBinder.getActualBinding(),
          matches(optionalBinder.getActualBinding(), expectedUserLinkedActual));
      assertTrue(
          "expectedUserLinkedActual: "
              + expectedUserLinkedActual
              + ", actualActual: "
              + javaOptionalBinder.getActualBinding(),
          matches(javaOptionalBinder.getActualBinding(), expectedUserLinkedActual));
    }

    Key<Optional<javax.inject.Provider<T>>> optionalJavaxProviderKey =
        keyType.ofType(RealOptionalBinder.optionalOfJavaxProvider(keyType.getTypeLiteral()));
    Key<?> javaOptionalJavaxProviderKey =
        keyType.ofType(RealOptionalBinder.javaOptionalOfJavaxProvider(keyType.getTypeLiteral()));
    Key<Optional<Provider<T>>> optionalProviderKey =
        keyType.ofType(RealOptionalBinder.optionalOfProvider(keyType.getTypeLiteral()));
    Key<?> javaOptionalProviderKey =
        keyType.ofType(RealOptionalBinder.javaOptionalOfProvider(keyType.getTypeLiteral()));
    assertEquals(
        ImmutableSet.of(optionalJavaxProviderKey, optionalProviderKey),
        optionalBinder.getAlternateKeys());
    assertEquals(
        ImmutableSet.of(javaOptionalJavaxProviderKey, javaOptionalProviderKey),
        javaOptionalBinder.getAlternateKeys());

    boolean keyMatch = false;
    boolean optionalKeyMatch = false;
    boolean javaOptionalKeyMatch = false;
    boolean optionalJavaxProviderKeyMatch = false;
    boolean javaOptionalJavaxProviderKeyMatch = false;
    boolean optionalProviderKeyMatch = false;
    boolean javaOptionalProviderKeyMatch = false;
    boolean defaultMatch = false;
    boolean actualMatch = false;
    List<Object> otherOptionalBindings = Lists.newArrayList();
    List<Binding> otherMatches = Lists.newArrayList();
    for (Binding b : injector.getAllBindings().values()) {
      boolean contains = optionalBinder.containsElement(b);
      assertEquals(contains, javaOptionalBinder.containsElement(b));

      Object visited = b.acceptTargetVisitor(visitor);
      if (visited instanceof OptionalBinderBinding) {
        if (visited.equals(optionalBinder)) {
          assertTrue(contains);
        } else if (visited.equals(javaOptionalBinder)) {
          assertTrue(contains);
        } else {
          otherOptionalBindings.add(visited);
        }
      }
      if (b.getKey().equals(keyType)) {
        // keyType might match because a user bound it
        // (which is possible in a purely absent OptionalBinder)
        assertEquals(expectedDefault != null || expectedActual != null, contains);
        if (contains) {
          keyMatch = true;
        }
      } else if (b.getKey().equals(optionalKey)) {
        assertTrue(contains);
        optionalKeyMatch = true;
      } else if (b.getKey().equals(javaOptionalKey)) {
        assertTrue(contains);
        javaOptionalKeyMatch = true;
      } else if (b.getKey().equals(optionalJavaxProviderKey)) {
        assertTrue(contains);
        optionalJavaxProviderKeyMatch = true;
      } else if (b.getKey().equals(javaOptionalJavaxProviderKey)) {
        assertTrue(contains);
        javaOptionalJavaxProviderKeyMatch = true;
      } else if (b.getKey().equals(optionalProviderKey)) {
        assertTrue(contains);
        optionalProviderKeyMatch = true;
      } else if (b.getKey().equals(javaOptionalProviderKey)) {
        assertTrue(contains);
        javaOptionalProviderKeyMatch = true;
      } else if (expectedDefault != null && matches(b, expectedDefault)) {
        assertTrue(contains);
        defaultMatch = true;
      } else if (expectedActual != null && matches(b, expectedActual)) {
        assertTrue(contains);
        actualMatch = true;
      } else if (contains) {
        otherMatches.add(b);
      }
    }

    assertEquals(otherMatches.toString(), 0, otherMatches.size());
    // only expect a keymatch if either default or actual are set
    assertEquals(expectedDefault != null || expectedActual != null, keyMatch);
    assertTrue(optionalKeyMatch);
    assertTrue(optionalJavaxProviderKeyMatch);
    assertTrue(optionalProviderKeyMatch);
    assertTrue(javaOptionalKeyMatch);
    assertTrue(javaOptionalJavaxProviderKeyMatch);
    assertTrue(javaOptionalProviderKeyMatch);
    assertEquals(expectedDefault != null, defaultMatch);
    assertEquals(expectedActual != null, actualMatch);
    assertEquals(
        "other OptionalBindings found: " + otherOptionalBindings,
        expectedOtherOptionalBindings,
        otherOptionalBindings.size());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static <T> void optionalModuleTest(
      Key<T> keyType,
      Iterable<? extends Module> modules,
      int expectedOtherOptionalBindings,
      BindResult<?> expectedDefault,
      BindResult<?> expectedActual,
      BindResult<?> expectedUserLinkedActual) {
    if (expectedUserLinkedActual != null) {
      assertNull("cannot have actual if expecting user binding", expectedActual);
      assertNull("cannot have default if expecting user binding", expectedDefault);
    }
    Set<Element> elements = ImmutableSet.copyOf(Elements.getElements(modules));
    Map<Key<?>, Binding<?>> indexed = index(elements);
    Key<Optional<T>> optionalKey =
        keyType.ofType(RealOptionalBinder.optionalOf(keyType.getTypeLiteral()));
    Key<?> javaOptionalKey =
        keyType.ofType(RealOptionalBinder.javaOptionalOf(keyType.getTypeLiteral()));
    Visitor visitor = new Visitor();
    Key<?> defaultKey = null;
    Key<?> actualKey = null;

    Binding optionalBinding = indexed.get(optionalKey);
    OptionalBinderBinding<Optional<T>> optionalBinder =
        (OptionalBinderBinding<Optional<T>>) optionalBinding.acceptTargetVisitor(visitor);

    Binding javaOptionalBinding = indexed.get(javaOptionalKey);
    OptionalBinderBinding<?> javaOptionalBinder =
        (OptionalBinderBinding) javaOptionalBinding.acceptTargetVisitor(visitor);

    // Locate the defaultKey & actualKey
    for (Element element : elements) {
      if (optionalBinder.containsElement(element) && element instanceof Binding) {
        Binding binding = (Binding) element;
        if (isSourceEntry(binding, RealOptionalBinder.Source.DEFAULT)) {
          defaultKey = binding.getKey();
        } else if (isSourceEntry(binding, RealOptionalBinder.Source.ACTUAL)) {
          actualKey = binding.getKey();
        }
      }
    }
    assertNotNull(optionalBinder);
    assertNotNull(javaOptionalBinder);

    assertEquals(expectedDefault == null, defaultKey == null);
    assertEquals(expectedActual == null, actualKey == null);

    Key<Optional<javax.inject.Provider<T>>> optionalJavaxProviderKey =
        keyType.ofType(RealOptionalBinder.optionalOfJavaxProvider(keyType.getTypeLiteral()));
    Key<?> javaOptionalJavaxProviderKey =
        keyType.ofType(RealOptionalBinder.javaOptionalOfJavaxProvider(keyType.getTypeLiteral()));
    Key<Optional<Provider<T>>> optionalProviderKey =
        keyType.ofType(RealOptionalBinder.optionalOfProvider(keyType.getTypeLiteral()));
    Key<?> javaOptionalProviderKey =
        keyType.ofType(RealOptionalBinder.javaOptionalOfProvider(keyType.getTypeLiteral()));
    boolean keyMatch = false;
    boolean optionalKeyMatch = false;
    boolean javaOptionalKeyMatch = false;
    boolean optionalJavaxProviderKeyMatch = false;
    boolean javaOptionalJavaxProviderKeyMatch = false;
    boolean optionalProviderKeyMatch = false;
    boolean javaOptionalProviderKeyMatch = false;
    boolean defaultMatch = false;
    boolean actualMatch = false;
    List<Object> otherOptionalElements = Lists.newArrayList();
    List<Element> otherContains = Lists.newArrayList();
    List<Element> nonContainedElements = Lists.newArrayList();
    for (Element element : elements) {
      boolean contains = optionalBinder.containsElement(element);
      assertEquals(contains, javaOptionalBinder.containsElement(element));

      if (!contains) {
        nonContainedElements.add(element);
      }
      Key key = null;
      Binding b = null;
      if (element instanceof Binding) {
        b = (Binding) element;
        key = b.getKey();
        Object visited = b.acceptTargetVisitor(visitor);
        if (visited instanceof OptionalBinderBinding) {
          if (visited.equals(optionalBinder)) {
            assertTrue(contains);
          } else if (visited.equals(javaOptionalBinder)) {
            assertTrue(contains);
          } else {
            otherOptionalElements.add(visited);
          }
        }
      } else if (element instanceof ProviderLookup) {
        key = ((ProviderLookup) element).getKey();
      }

      if (key != null && key.equals(keyType)) {
        // keyType might match because a user bound it
        // (which is possible in a purely absent OptionalBinder)
        assertEquals(expectedDefault != null || expectedActual != null, contains);
        if (contains) {
          keyMatch = true;
        }
      } else if (key != null && key.equals(optionalKey)) {
        assertTrue(contains);
        optionalKeyMatch = true;
      } else if (key != null && key.equals(javaOptionalKey)) {
        assertTrue(contains);
        javaOptionalKeyMatch = true;
      } else if (key != null && key.equals(optionalJavaxProviderKey)) {
        assertTrue(contains);
        optionalJavaxProviderKeyMatch = true;
      } else if (key != null && key.equals(javaOptionalJavaxProviderKey)) {
        assertTrue(contains);
        javaOptionalJavaxProviderKeyMatch = true;
      } else if (key != null && key.equals(optionalProviderKey)) {
        assertTrue(contains);
        optionalProviderKeyMatch = true;
      } else if (key != null && key.equals(javaOptionalProviderKey)) {
        assertTrue(contains);
        javaOptionalProviderKeyMatch = true;
      } else if (key != null && key.equals(defaultKey)) {
        assertTrue(contains);
        if (b != null) { // otherwise it might just be a ProviderLookup into it
          assertTrue(
              "expected: " + expectedDefault + ", but was: " + b, matches(b, expectedDefault));
          defaultMatch = true;
        }
      } else if (key != null && key.equals(actualKey)) {
        assertTrue(contains);
        if (b != null) { // otherwise it might just be a ProviderLookup into it
          assertTrue("expected: " + expectedActual + ", but was: " + b, matches(b, expectedActual));
          actualMatch = true;
        }
      } else if (contains) {
        otherContains.add(element);
      }
    }

    // only expect a keymatch if either default or actual are set
    assertEquals(expectedDefault != null || expectedActual != null, keyMatch);
    assertTrue(optionalKeyMatch);
    assertTrue(optionalJavaxProviderKeyMatch);
    assertTrue(optionalProviderKeyMatch);
    assertTrue(javaOptionalKeyMatch);
    assertTrue(javaOptionalJavaxProviderKeyMatch);
    assertTrue(javaOptionalProviderKeyMatch);
    assertEquals(expectedDefault != null, defaultMatch);
    assertEquals(expectedActual != null, actualMatch);
    assertEquals(otherContains.toString(), 0, otherContains.size());
    assertEquals(
        "other OptionalBindings found: " + otherOptionalElements,
        expectedOtherOptionalBindings,
        otherOptionalElements.size());

    // Validate that we can construct an injector out of the remaining bindings.
    Guice.createInjector(Elements.getModule(nonContainedElements));
  }

  private static boolean isSourceEntry(Binding<?> b, RealOptionalBinder.Source type) {
    switch (type) {
      case ACTUAL:
        return b.getKey().getAnnotation() instanceof RealOptionalBinder.Actual;
      case DEFAULT:
        return b.getKey().getAnnotation() instanceof RealOptionalBinder.Default;
      default:
        throw new IllegalStateException("invalid type: " + type);
    }
  }

  /** Returns the subset of elements that have keys, indexed by them. */
  private static Map<Key<?>, Binding<?>> index(Iterable<Element> elements) {
    ImmutableMap.Builder<Key<?>, Binding<?>> builder = ImmutableMap.builder();
    for (Element element : elements) {
      if (element instanceof Binding) {
        builder.put(((Binding) element).getKey(), (Binding) element);
      }
    }
    return builder.build();
  }

  static <K, V> MapResult<K, V> instance(K k, V v) {
    return new MapResult<K, V>(k, new BindResult<V>(INSTANCE, v, null));
  }

  static <K, V> MapResult<K, V> linked(K k, Class<? extends V> clazz) {
    return new MapResult<K, V>(k, new BindResult<V>(LINKED, null, Key.get(clazz)));
  }

  static <K, V> MapResult<K, V> linked(K k, Key<? extends V> key) {
    return new MapResult<K, V>(k, new BindResult<V>(LINKED, null, key));
  }

  static <K, V> MapResult<K, V> providerInstance(K k, V v) {
    return new MapResult<K, V>(k, new BindResult<V>(PROVIDER_INSTANCE, v, null));
  }

  static class MapResult<K, V> {
    private final K k;
    private final BindResult<V> v;

    MapResult(K k, BindResult<V> v) {
      this.k = k;
      this.v = v;
    }

    @Override
    public String toString() {
      return "entry[key[" + k + "],value[" + v + "]]";
    }
  }

  private static boolean matches(Binding<?> item, BindResult<?> result) {
    switch (result.type) {
      case INSTANCE:
        if (item instanceof InstanceBinding
            && ((InstanceBinding) item).getInstance().equals(result.instance)) {
          return true;
        }
        break;
      case LINKED:
        if (item instanceof LinkedKeyBinding
            && ((LinkedKeyBinding) item).getLinkedKey().equals(result.key)) {
          return true;
        }
        break;
      case PROVIDER_INSTANCE:
        if (item instanceof ProviderInstanceBinding
            && Objects.equal(
                ((ProviderInstanceBinding) item).getUserSuppliedProvider().get(),
                result.instance)) {
          return true;
        }
        break;
      case PROVIDER_KEY:
        if (item instanceof ProviderKeyBinding
            && ((ProviderKeyBinding) item).getProviderKey().equals(result.key)) {
          return true;
        }
        break;
    }
    return false;
  }

  static <T> BindResult<T> instance(T t) {
    return new BindResult<T>(INSTANCE, t, null);
  }

  static <T> BindResult<T> linked(Class<? extends T> clazz) {
    return new BindResult<T>(LINKED, null, Key.get(clazz));
  }

  static <T> BindResult<T> linked(Key<? extends T> key) {
    return new BindResult<T>(LINKED, null, key);
  }

  static <T> BindResult<T> providerInstance(T t) {
    return new BindResult<T>(PROVIDER_INSTANCE, t, null);
  }

  static <T> BindResult<T> providerKey(Key<T> key) {
    return new BindResult<T>(PROVIDER_KEY, null, key);
  }

  /** The kind of binding. */
  static enum BindType {
    INSTANCE,
    LINKED,
    PROVIDER_INSTANCE,
    PROVIDER_KEY
  }
  /** The result of the binding. */
  static class BindResult<T> {
    private final BindType type;
    private final Key<?> key;
    private final T instance;

    private BindResult(BindType type, T instance, Key<?> key) {
      this.type = type;
      this.instance = instance;
      this.key = key;
    }

    @Override
    public String toString() {
      switch (type) {
        case INSTANCE:
          return "instance[" + instance + "]";
        case LINKED:
          return "linkedKey[" + key + "]";
        case PROVIDER_INSTANCE:
          return "providerInstance[" + instance + "]";
        case PROVIDER_KEY:
          return "providerKey[" + key + "]";
      }
      return null;
    }
  }

  private static class Visitor<T> extends DefaultBindingTargetVisitor<T, Object>
      implements MultibindingsTargetVisitor<T, Object> {

    @Override
    public Object visit(MultibinderBinding<? extends T> multibinding) {
      return multibinding;
    }

    @Override
    public Object visit(MapBinderBinding<? extends T> mapbinding) {
      return mapbinding;
    }

    @Override
    public Object visit(OptionalBinderBinding<? extends T> optionalbinding) {
      return optionalbinding;
    }
  }
}
