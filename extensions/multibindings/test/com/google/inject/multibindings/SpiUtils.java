/**
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

package com.google.inject.multibindings;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.inject.multibindings.MapBinder.entryOfProviderOf;
import static com.google.inject.multibindings.MapBinder.mapOf;
import static com.google.inject.multibindings.MapBinder.mapOfJavaxProviderOf;
import static com.google.inject.multibindings.MapBinder.mapOfProviderOf;
import static com.google.inject.multibindings.MapBinder.mapOfSetOfProviderOf;
import static com.google.inject.multibindings.Multibinder.setOf;
import static com.google.inject.multibindings.OptionalBinder.optionalOfJavaxProvider;
import static com.google.inject.multibindings.OptionalBinder.optionalOfProvider;
import static com.google.inject.multibindings.SpiUtils.BindType.INSTANCE;
import static com.google.inject.multibindings.SpiUtils.BindType.LINKED;
import static com.google.inject.multibindings.SpiUtils.BindType.PROVIDER_INSTANCE;
import static com.google.inject.multibindings.SpiUtils.BindType.PROVIDER_KEY;
import static com.google.inject.multibindings.SpiUtils.VisitType.BOTH;
import static com.google.inject.multibindings.SpiUtils.VisitType.INJECTOR;
import static com.google.inject.multibindings.SpiUtils.VisitType.MODULE;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.OptionalBinder.Source;
import com.google.inject.spi.DefaultBindingTargetVisitor;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.google.inject.spi.HasDependencies;
import com.google.inject.spi.InstanceBinding;
import com.google.inject.spi.LinkedKeyBinding;
import com.google.inject.spi.ProviderInstanceBinding;
import com.google.inject.spi.ProviderKeyBinding;
import com.google.inject.spi.ProviderLookup;

import java.lang.reflect.ParameterizedType;
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

  /** The kind of test we should perform.  A live Injector, a raw Elements (Module) test, or both. */
  enum VisitType { INJECTOR, MODULE, BOTH }
  
  /**
   * Asserts that MapBinderBinding visitors for work correctly.
   * 
   * @param <T> The type of the binding
   * @param mapKey The key the map belongs to.
   * @param keyType the TypeLiteral of the key of the map
   * @param valueType the TypeLiteral of the value of the map
   * @param modules The modules that define the mapbindings
   * @param visitType The kind of test we should perform.  A live Injector, a raw Elements (Module) test, or both.
   * @param allowDuplicates If duplicates are allowed.
   * @param expectedMapBindings The number of other mapbinders we expect to see.
   * @param results The kind of bindings contained in the mapbinder.
   */
  static <T> void assertMapVisitor(Key<T> mapKey, TypeLiteral<?> keyType, TypeLiteral<?> valueType,
      Iterable<? extends Module> modules, VisitType visitType, boolean allowDuplicates,
      int expectedMapBindings, MapResult... results) {
    if(visitType == null) {
      fail("must test something");
    }

    if (visitType == BOTH || visitType == INJECTOR) {
      mapInjectorTest(mapKey, keyType, valueType, modules, allowDuplicates, expectedMapBindings,
          results);
    }

    if (visitType == BOTH || visitType == MODULE) {
      mapModuleTest(mapKey, keyType, valueType, modules, allowDuplicates, expectedMapBindings,
          results);
    }
  }
  
  @SuppressWarnings("unchecked")
  private static <T> void mapInjectorTest(Key<T> mapKey, TypeLiteral<?> keyType,
      TypeLiteral<?> valueType, Iterable<? extends Module> modules, boolean allowDuplicates,
      int expectedMapBindings, MapResult... results) {
    Injector injector = Guice.createInjector(modules);
    Visitor<T> visitor = new Visitor<T>();
    Binding<T> mapBinding = injector.getBinding(mapKey);
    MapBinderBinding<T> mapbinder = (MapBinderBinding<T>)mapBinding.acceptTargetVisitor(visitor);
    assertNotNull(mapbinder);
    assertEquals(keyType, mapbinder.getKeyTypeLiteral());
    assertEquals(valueType, mapbinder.getValueTypeLiteral());
    assertEquals(allowDuplicates, mapbinder.permitsDuplicates());
    List<Map.Entry<?, Binding<?>>> entries = Lists.newArrayList(mapbinder.getEntries());
    List<MapResult> mapResults = Lists.newArrayList(results);
    assertEquals("wrong entries, expected: " + mapResults + ", but was: " + entries,
        mapResults.size(), entries.size());

    for(MapResult result : mapResults) {
      Map.Entry<?, Binding<?>> found = null;
      for(Map.Entry<?, Binding<?>> entry : entries) {
        Object key = entry.getKey();
        Binding<?> value = entry.getValue();
        if(key.equals(result.k) && matches(value, result.v)) {
          found = entry;
          break;
        }
      }
      if(found == null) {
        fail("Could not find entry: " + result + " in remaining entries: " + entries);
      } else {
        assertTrue("mapBinder doesn't contain: " + found.getValue(), 
            mapbinder.containsElement(found.getValue()));
        entries.remove(found);
      }
    }
    
    if(!entries.isEmpty()) {
      fail("Found all entries of: " + mapResults + ", but more were left over: " + entries);
    }
    
    Key<?> mapOfJavaxProvider = mapKey.ofType(mapOfJavaxProviderOf(keyType, valueType));
    Key<?> mapOfProvider = mapKey.ofType(mapOfProviderOf(keyType, valueType));
    Key<?> mapOfSetOfProvider = mapKey.ofType(mapOfSetOfProviderOf(keyType, valueType));
    Key<?> mapOfSet = mapKey.ofType(mapOf(keyType, setOf(valueType)));
    Key<?> setOfEntry = mapKey.ofType(setOf(entryOfProviderOf(keyType, valueType)));
    boolean entrySetMatch = false;
    boolean mapJavaxProviderMatch = false;
    boolean mapProviderMatch = false;
    boolean mapSetMatch = false; 
    boolean mapSetProviderMatch = false;
    List<Object> otherMapBindings = Lists.newArrayList();
    List<Binding> otherMatches = Lists.newArrayList();
    for(Binding b : injector.getAllBindings().values()) {
      boolean contains = mapbinder.containsElement(b);      
      Object visited = b.acceptTargetVisitor(visitor);
      if(visited instanceof MapBinderBinding) {
        if(visited.equals(mapbinder)) {
          assertTrue(contains);
        } else {
          otherMapBindings.add(visited);
        }
      } else if(b.getKey().equals(mapOfProvider)) {
        assertTrue(contains);
        mapProviderMatch = true;
      } else if (b.getKey().equals(mapOfJavaxProvider)) {
        assertTrue(contains);
        mapJavaxProviderMatch = true;
      } else if(b.getKey().equals(mapOfSet)) {
        assertTrue(contains);
        mapSetMatch = true;
      } else if(b.getKey().equals(mapOfSetOfProvider)) {
        assertTrue(contains);
        mapSetProviderMatch = true;
      } else if(b.getKey().equals(setOfEntry)) {
        assertTrue(contains);
        entrySetMatch = true;
        // Validate that this binding is also a MultibinderBinding.
        assertTrue(b.acceptTargetVisitor(visitor) instanceof MultibinderBinding);
      } else if (contains) {
        otherMatches.add(b);
      }
    }
    
    int sizeOfOther = otherMatches.size();
    if(allowDuplicates) {
      sizeOfOther--; // account for 1 duplicate binding
    }
    sizeOfOther = sizeOfOther / 2; // account for 1 value & 1 Map.Entry of each expected binding.
    assertEquals("Incorrect other matches: " + otherMatches, mapResults.size(), sizeOfOther);
    assertTrue(entrySetMatch);
    assertTrue(mapProviderMatch);
    assertTrue(mapJavaxProviderMatch);
    assertEquals(allowDuplicates, mapSetMatch);
    assertEquals(allowDuplicates, mapSetProviderMatch);
    assertEquals("other MapBindings found: " + otherMapBindings, expectedMapBindings,
        otherMapBindings.size());
  }
  
  @SuppressWarnings("unchecked")
  private static <T> void mapModuleTest(Key<T> mapKey, TypeLiteral<?> keyType,
      TypeLiteral<?> valueType, Iterable<? extends Module> modules, boolean allowDuplicates,
      int expectedMapBindings, MapResult... results) {
    Set<Element> elements = ImmutableSet.copyOf(Elements.getElements(modules));
    Visitor<T> visitor = new Visitor<T>();
    MapBinderBinding<T> mapbinder = null;
    for(Element element : elements) {
      if(element instanceof Binding && ((Binding)element).getKey().equals(mapKey)) {
        mapbinder = (MapBinderBinding<T>)((Binding)element).acceptTargetVisitor(visitor);
        break;
      }
    }
    assertNotNull(mapbinder);
    
    assertEquals(keyType, mapbinder.getKeyTypeLiteral());
    assertEquals(valueType, mapbinder.getValueTypeLiteral());
    List<MapResult> mapResults = Lists.newArrayList(results);
    
    Key<?> mapOfProvider = mapKey.ofType(mapOfProviderOf(keyType, valueType));
    Key<?> mapOfSetOfProvider = mapKey.ofType(mapOfSetOfProviderOf(keyType, valueType));
    Key<?> mapOfSet = mapKey.ofType(mapOf(keyType, setOf(valueType)));
    Key<?> setOfEntry = mapKey.ofType(setOf(entryOfProviderOf(keyType, valueType)));    
    boolean entrySetMatch = false;
    boolean mapProviderMatch = false;
    boolean mapSetMatch = false; 
    boolean mapSetProviderMatch = false;
    List<Object> otherMapBindings = Lists.newArrayList();
    List<Element> otherMatches = Lists.newArrayList();
    List<Element> otherElements = Lists.newArrayList();
    for(Element element : elements) {
      boolean contains = mapbinder.containsElement(element);
      if(!contains) {
        otherElements.add(element);
      }
      boolean matched = false;
      Key key = null;
      Binding b = null;
      if(element instanceof Binding) {
        b = (Binding)element;
        key = b.getKey();
        Object visited = b.acceptTargetVisitor(visitor);
        if(visited instanceof MapBinderBinding) {
          matched = true;
          if(visited.equals(mapbinder)) {
            assertTrue(contains);
          } else {
            otherMapBindings.add(visited);
          }
        }
      } else if(element instanceof ProviderLookup) {
        key = ((ProviderLookup)element).getKey();
      }
      
      if(!matched && key != null) {
        if(key.equals(mapOfProvider)) {
          matched = true;
          assertTrue(contains);
          mapProviderMatch = true;
        } else if(key.equals(mapOfSet)) {
          matched = true;
          assertTrue(contains);
          mapSetMatch = true;
        } else if(key.equals(mapOfSetOfProvider)) {
          matched = true;
          assertTrue(contains);
          mapSetProviderMatch = true;
        } else if(key.equals(setOfEntry)) {
          matched = true;
          assertTrue(contains);
          entrySetMatch = true;
          // Validate that this binding is also a MultibinderBinding.
          if(b != null) {
            assertTrue(b.acceptTargetVisitor(visitor) instanceof MultibinderBinding);
          }
        }
      }
      
      if(!matched && contains) {
        otherMatches.add(element);
      }
    }
    
    int otherMatchesSize = otherMatches.size();
    if(allowDuplicates) {
      otherMatchesSize--; // allow for 1 duplicate binding
    }
    otherMatchesSize = otherMatchesSize / 3; // value, ProviderLookup per value, Map.Entry per value
    assertEquals("incorrect number of contains, leftover matches: " + otherMatches, mapResults
        .size(), otherMatchesSize);

    assertTrue(entrySetMatch);
    assertTrue(mapProviderMatch);
    assertEquals(allowDuplicates, mapSetMatch);
    assertEquals(allowDuplicates, mapSetProviderMatch);
    assertEquals("other MapBindings found: " + otherMapBindings, expectedMapBindings,
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
   * @param visitType The kind of test we should perform.  A live Injector, a raw Elements (Module) test, or both.
   * @param allowDuplicates If duplicates are allowed.
   * @param expectedMultibindings The number of other multibinders we expect to see.
   * @param results The kind of bindings contained in the multibinder.
   */
  static <T> void assertSetVisitor(Key<T> setKey, TypeLiteral<?> elementType,
      Iterable<? extends Module> modules, VisitType visitType, boolean allowDuplicates,
      int expectedMultibindings, BindResult... results) {
    if(visitType == null) {
      fail("must test something");
    }
    
    if(visitType == BOTH || visitType == INJECTOR) {
      setInjectorTest(setKey, elementType, modules, allowDuplicates, expectedMultibindings, results);
    }
    
    if(visitType == BOTH || visitType == MODULE) {
      setModuleTest(setKey, elementType, modules, allowDuplicates, expectedMultibindings, results);
    }
  }
  
  @SuppressWarnings("unchecked")
  private static <T> void setInjectorTest(Key<T> setKey, TypeLiteral<?> elementType,
      Iterable<? extends Module> modules, boolean allowDuplicates, int otherMultibindings,
      BindResult... results) {
    Injector injector = Guice.createInjector(modules);
    Visitor<T> visitor = new Visitor<T>();
    Binding<T> binding = injector.getBinding(setKey);
    MultibinderBinding<T> multibinder = (MultibinderBinding<T>)binding.acceptTargetVisitor(visitor);
    assertNotNull(multibinder);
    assertEquals(elementType, multibinder.getElementTypeLiteral());
    assertEquals(allowDuplicates, multibinder.permitsDuplicates());
    List<Binding<?>> elements = Lists.newArrayList(multibinder.getElements());
    List<BindResult> bindResults = Lists.newArrayList(results);
    assertEquals("wrong bind elements, expected: " + bindResults + ", but was: " + multibinder.getElements(),
        bindResults.size(), elements.size());
    
    for(BindResult result : bindResults) {
      Binding found = null;
      for(Binding item : elements) {
        if (matches(item, result)) {
          found = item;
          break;
        }
      }
      if(found == null) {
        fail("Could not find element: " + result + " in remaining elements: " + elements);
      } else {
        elements.remove(found);
      }
    }
    
    if(!elements.isEmpty()) {
      fail("Found all elements of: " + bindResults + ", but more were left over: " + elements);
    }
    
    Set<Binding> setOfElements = new HashSet<Binding>(multibinder.getElements()); 
    
    List<Object> otherMultibinders = Lists.newArrayList();
    List<Binding> otherContains = Lists.newArrayList();
    for(Binding b : injector.getAllBindings().values()) {
      boolean contains = multibinder.containsElement(b);
      Object visited = b.acceptTargetVisitor(visitor);
      if(visited != null) {
        if(visited.equals(multibinder)) {
          assertTrue(contains);
        } else {
          otherMultibinders.add(visited);
        }
      } else if(setOfElements.contains(b)) {
        assertTrue(contains);
      } else if(contains) {
        otherContains.add(b);
      }
    }
    
    if(allowDuplicates) {
      assertEquals("contained more than it should: " + otherContains, 1, otherContains.size());
    } else {
      assertTrue("contained more than it should: " + otherContains, otherContains.isEmpty());
    }
    assertEquals("other multibindings found: " + otherMultibinders, otherMultibindings,
        otherMultibinders.size());
    
  }
  
  @SuppressWarnings("unchecked")
  private static <T> void setModuleTest(Key<T> setKey, TypeLiteral<?> elementType,
      Iterable<? extends Module> modules, boolean allowDuplicates, int otherMultibindings,
      BindResult... results) {
    List<BindResult> bindResults = Lists.newArrayList(results);
    List<Element> elements = Elements.getElements(modules);
    Visitor<T> visitor = new Visitor<T>();
    MultibinderBinding<T> multibinder = null;
    for(Element element : elements) {
      if(element instanceof Binding && ((Binding)element).getKey().equals(setKey)) {
        multibinder = (MultibinderBinding<T>)((Binding)element).acceptTargetVisitor(visitor);
        break;
      }
    }
    assertNotNull(multibinder);

    assertEquals(elementType, multibinder.getElementTypeLiteral());
    List<Object> otherMultibinders = Lists.newArrayList();
    Set<Element> otherContains = new HashSet<Element>();
    List<Element> otherElements = Lists.newArrayList();
    for(Element element : elements) {
      boolean contains = multibinder.containsElement(element);
      if(!contains) {
        otherElements.add(element);
      }
      boolean matched = false;
      if(element instanceof Binding) {
        Binding binding = (Binding)element;
        Object visited = binding.acceptTargetVisitor(visitor);
        if(visited != null) {
          matched = true;
          if(visited.equals(multibinder)) {
            assertTrue(contains);
          } else {
            otherMultibinders.add(visited);
          }
        }
      }
      
      if(!matched && contains) {
        otherContains.add(element);
      }
    }
    
    if(allowDuplicates) {
      assertEquals("wrong contained elements: " + otherContains, bindResults.size() + 1, otherContains.size());
    } else {
      assertEquals("wrong contained elements: " + otherContains, bindResults.size(), otherContains.size());
    }
     
    assertEquals("other multibindings found: " + otherMultibinders, otherMultibindings,
        otherMultibinders.size());
    
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
   *        test, or both.
   * @param expectedOtherOptionalBindings the # of other optional bindings we expect to see.
   * @param expectedDefault the expected default binding, or null if none
   * @param expectedActual the expected actual binding, or null if none
   */
  static <T> void assertOptionalVisitor(Key<T> keyType,
      Iterable<? extends Module> modules,
      VisitType visitType,
      int expectedOtherOptionalBindings,
      BindResult<?> expectedDefault,
      BindResult<?> expectedActual) {
    if (visitType == null) {
      fail("must test something");
    }

    if (visitType == BOTH || visitType == INJECTOR) {
      optionalInjectorTest(keyType, modules, expectedOtherOptionalBindings, expectedDefault,
          expectedActual);
    }

    if (visitType == BOTH || visitType == MODULE) {
      optionalModuleTest(keyType, modules, expectedOtherOptionalBindings, expectedDefault,
          expectedActual);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> void optionalInjectorTest(Key<T> keyType, Iterable<? extends Module> modules,
      int expectedOtherOptionalBindings, BindResult<?> expectedDefault,
      BindResult<?> expectedActual) {
    Key<Optional<T>> optionalKey =
        keyType.ofType(OptionalBinder.optionalOf(keyType.getTypeLiteral()));
    Injector injector = Guice.createInjector(modules);
    Binding<Optional<T>> optionalBinding = injector.getBinding(optionalKey);
    Visitor<Optional<T>> visitor = new Visitor<Optional<T>>();
    OptionalBinderBinding<T> optionalBinder =
        (OptionalBinderBinding<T>) optionalBinding.acceptTargetVisitor(visitor);
    assertNotNull(optionalBinder);
    assertEquals(optionalKey, optionalBinder.getKey());

    if (expectedDefault == null) {
      assertNull("did not expect a default binding", optionalBinder.getDefaultBinding());
    } else {
      assertTrue("expectedDefault: " + expectedDefault + ", actualDefault: "
          + optionalBinder.getDefaultBinding(),
          matches(optionalBinder.getDefaultBinding(), expectedDefault));
    }

    if (expectedActual == null) {
      assertNull(optionalBinder.getActualBinding());
    } else {
      assertTrue("expectedActual: " + expectedActual + ", actualActual: "
          + optionalBinder.getActualBinding(),
          matches(optionalBinder.getActualBinding(), expectedActual));
    }


    Key<Optional<javax.inject.Provider<T>>> optionalJavaxProviderKey =
        keyType.ofType(optionalOfJavaxProvider(keyType.getTypeLiteral()));
    Key<Optional<Provider<T>>> optionalProviderKey =
        keyType.ofType(optionalOfProvider(keyType.getTypeLiteral()));
    Binding<Map<Source, T>> mapBinding = injector.getBinding(
        keyType.ofType(mapOf(TypeLiteral.get(Source.class), keyType.getTypeLiteral())));
    MapBinderBinding<Map<Source, T>> mapbinderBinding = (MapBinderBinding<
        Map<Source, T>>) mapBinding.acceptTargetVisitor(new Visitor<Map<Source, T>>());

    boolean keyMatch = false;
    boolean optionalKeyMatch = false;
    boolean optionalJavaxProviderKeyMatch = false;
    boolean optionalProviderKeyMatch = false;
    boolean mapBindingMatch = false;
    boolean defaultMatch = false;
    boolean actualMatch = false;
    List<Object> otherOptionalBindings = Lists.newArrayList();
    List<Binding> otherMatches = Lists.newArrayList();
    for (Binding b : injector.getAllBindings().values()) {
      boolean contains = optionalBinder.containsElement(b);
      Object visited = b.acceptTargetVisitor(visitor);
      if (visited instanceof OptionalBinderBinding) {
        if (visited.equals(optionalBinder)) {
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
      } else if (b.getKey().equals(optionalJavaxProviderKey)) {
        assertTrue(contains);
        optionalJavaxProviderKeyMatch = true;
      } else if (b.getKey().equals(optionalProviderKey)) {
        assertTrue(contains);
        optionalProviderKeyMatch = true;
      } else if (b.getKey().equals(mapBinding.getKey())) {
        assertTrue(contains);
        mapBindingMatch = true;
        // Validate that this binding is also a MapBinding.
        assertEquals(mapbinderBinding, b.acceptTargetVisitor(visitor));
      } else if (expectedDefault != null && matches(b, expectedDefault)) {
        assertTrue(contains);
        defaultMatch = true;
      } else if (expectedActual != null && matches(b, expectedActual)) {
        assertTrue(contains);
        actualMatch = true;
      } else if (mapbinderBinding.containsElement(b)) {
        assertTrue(contains);
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
    assertTrue(mapBindingMatch);
    assertEquals(expectedDefault != null, defaultMatch);
    assertEquals(expectedActual != null, actualMatch);
    assertEquals("other OptionalBindings found: " + otherOptionalBindings,
        expectedOtherOptionalBindings, otherOptionalBindings.size());
  }

  @SuppressWarnings("unchecked")
  private static <T> void optionalModuleTest(Key<T> keyType, Iterable<? extends Module> modules,
      int expectedOtherOptionalBindings, BindResult<?> expectedDefault,
      BindResult<?> expectedActual) {
    Set<Element> elements = ImmutableSet.copyOf(Elements.getElements(modules));
    Map<Key<?>, Binding<?>> indexed = index(elements);
    Key<Optional<T>> optionalKey =
        keyType.ofType(OptionalBinder.optionalOf(keyType.getTypeLiteral()));
    Key<Map<Source, T>> sourceMapKey =
        keyType.ofType(mapOf(TypeLiteral.get(Source.class), keyType.getTypeLiteral()));
    Visitor<Optional<T>> visitor = new Visitor<Optional<T>>();
    OptionalBinderBinding<T> optionalBinder = null;
    MapBinderBinding<Map<Source, T>> mapbinderBinding = null;
    Key<?> defaultKey = null;
    Key<?> actualKey = null;

    Binding optionalBinding = indexed.get(optionalKey);
    optionalBinder = (OptionalBinderBinding<T>) optionalBinding.acceptTargetVisitor(visitor);
    Binding mapBinding = indexed.get(sourceMapKey);
    mapbinderBinding = (MapBinderBinding<Map<Source, T>>) mapBinding.acceptTargetVisitor(visitor);

    // Locate the defaultKey & actualKey
    for (Element element : elements) {
      if (optionalBinder.containsElement(element) && element instanceof Binding) {
        Binding binding = (Binding) element;
        if (isSourceEntry(binding, Source.DEFAULT)) {
          defaultKey = keyFromOptionalSourceBinding(binding, indexed);
        } else if (isSourceEntry(binding, Source.ACTUAL)) {
          actualKey = keyFromOptionalSourceBinding(binding, indexed);
        }
      }
    }
    assertNotNull(optionalBinder);
    assertEquals(expectedDefault == null, defaultKey == null);
    assertEquals(expectedActual == null, actualKey == null);

    Key<Optional<javax.inject.Provider<T>>> optionalJavaxProviderKey =
        keyType.ofType(optionalOfJavaxProvider(keyType.getTypeLiteral()));
    Key<Optional<Provider<T>>> optionalProviderKey =
        keyType.ofType(optionalOfProvider(keyType.getTypeLiteral()));
    boolean keyMatch = false;
    boolean optionalKeyMatch = false;
    boolean optionalJavaxProviderKeyMatch = false;
    boolean optionalProviderKeyMatch = false;
    boolean mapBindingMatch = false;
    boolean defaultMatch = false;
    boolean actualMatch = false;
    List<Object> otherOptionalElements = Lists.newArrayList();
    List<Element> otherContains = Lists.newArrayList();
    List<Element> nonContainedElements = Lists.newArrayList();
    for (Element element : elements) {
      boolean contains = optionalBinder.containsElement(element);
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
      } else if (key != null && key.equals(optionalJavaxProviderKey)) {
        assertTrue(contains);
        optionalJavaxProviderKeyMatch = true;
      } else if (key != null && key.equals(optionalProviderKey)) {
        assertTrue(contains);
        optionalProviderKeyMatch = true;
      } else if (key != null && key.equals(sourceMapKey)) {
        assertTrue(contains);
        mapBindingMatch = true;
        // Validate that this binding is also a MapBinding.
        assertEquals(mapbinderBinding, b.acceptTargetVisitor(visitor));
      } else if (key != null && key.equals(defaultKey)) {
        assertTrue(contains);
        if (b != null) { // otherwise it might just be a ProviderLookup into it
          assertTrue("expected: " + expectedDefault + ", but was: " + b,
              matches(b, expectedDefault));
          defaultMatch = true;
        }
      } else if (key != null && key.equals(actualKey)) {
        assertTrue(contains);
        if (b != null) { // otherwise it might just be a ProviderLookup into it
          assertTrue("expected: " + expectedActual + ", but was: " + b, matches(b, expectedActual));
          actualMatch = true;
        }
      } else if (mapbinderBinding.containsElement(element)) {
        assertTrue(contains);
      } else if (contains) {
        otherContains.add(element);
      }
    }
    
    // only expect a keymatch if either default or actual are set
    assertEquals(expectedDefault != null || expectedActual != null, keyMatch);
    assertTrue(optionalKeyMatch);
    assertTrue(optionalJavaxProviderKeyMatch);
    assertTrue(optionalProviderKeyMatch);
    assertTrue(mapBindingMatch);
    assertEquals(expectedDefault != null, defaultMatch);
    assertEquals(expectedActual != null, actualMatch);
    assertEquals(otherContains.toString(), 0, otherContains.size());
    assertEquals("other OptionalBindings found: " + otherOptionalElements,
        expectedOtherOptionalBindings, otherOptionalElements.size());
    
     // Validate that we can construct an injector out of the remaining bindings.
    Guice.createInjector(Elements.getModule(nonContainedElements));
  }
  
  private static Key<?> keyFromOptionalSourceBinding(Binding<?> binding,
      Map<Key<?>, Binding<?>> elements) {
    // Flow is:
    //  binding == ProviderInstanceBinding<Map.Entry<Source, Provider<String>>
    //   dependency on: Provider<String> that maps to ProviderInstanceBinding<String> in MapBinder
    //      dependency on: Provider<String> of user set value.
    Key<?> mapKey =
        genericOf(getOnlyElement(((HasDependencies) binding).getDependencies()).getKey());
    Binding<?> mapBinding = elements.get(mapKey);
    Key<?> userKey =
        genericOf(getOnlyElement(((HasDependencies) mapBinding).getDependencies()).getKey());
    return userKey;
  }

  /** Returns {@code Key<T>} for something like {@code Key<Provider<T>>} */
  private static Key<?> genericOf(Key<?> key) {
    ParameterizedType type = (ParameterizedType) key.getTypeLiteral().getType();
    assertEquals(1, type.getActualTypeArguments().length);
    Key<?> result = key.ofType(type.getActualTypeArguments()[0]);
    return result;
  }

  private static boolean isSourceEntry(Binding b, Source type) {
    if (b instanceof ProviderInstanceBinding && b.getKey().getAnnotation() instanceof RealElement) {
      javax.inject.Provider provider = ((ProviderInstanceBinding) b).getUserSuppliedProvider();
      if (provider instanceof Map.Entry) {
        Map.Entry entry = (Map.Entry) provider;
        if (entry.getKey() == type) {
          return true;
        }
      }
    }
    return false;
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
  
  static <K, V> MapResult instance(K k, V v) {
    return new MapResult<K, V>(k, new BindResult<V>(INSTANCE, v, null));
  }

  static <K, V> MapResult linked(K k, Class<? extends V> clazz) {
    return new MapResult<K, V>(k, new BindResult<V>(LINKED, null, Key.get(clazz)));
  }

  static <K, V> MapResult linked(K k, Key<? extends V> key) {
    return new MapResult<K, V>(k, new BindResult<V>(LINKED, null, key));
  }

  static <K, V> MapResult providerInstance(K k, V v) {
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
          && Objects.equal(((ProviderInstanceBinding) item).getUserSuppliedProvider().get(),
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
  static enum BindType { INSTANCE, LINKED, PROVIDER_INSTANCE, PROVIDER_KEY }
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
      switch(type) {
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
  
  private static class Visitor<T> extends
      DefaultBindingTargetVisitor<T, Object> implements MultibindingsTargetVisitor<T, Object> {
  
    public Object visit(MultibinderBinding<? extends T> multibinding) {
      return multibinding;
    }
  
    public Object visit(MapBinderBinding<? extends T> mapbinding) {
      return mapbinding;
    }
    
    public Object visit(OptionalBinderBinding<? extends T> optionalbinding) {
      return optionalbinding;
    }
  }
}

