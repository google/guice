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

import static com.google.inject.multibindings.MapBinder.entryOfProviderOf;
import static com.google.inject.multibindings.MapBinder.mapOf;
import static com.google.inject.multibindings.MapBinder.mapOfProviderOf;
import static com.google.inject.multibindings.MapBinder.mapOfSetOfProviderOf;
import static com.google.inject.multibindings.Multibinder.setOf;
import static com.google.inject.multibindings.SpiUtils.BindType.INSTANCE;
import static com.google.inject.multibindings.SpiUtils.BindType.LINKED;
import static com.google.inject.multibindings.SpiUtils.BindType.PROVIDER_INSTANCE;
import static com.google.inject.multibindings.SpiUtils.VisitType.BOTH;
import static com.google.inject.multibindings.SpiUtils.VisitType.INJECTOR;
import static com.google.inject.multibindings.SpiUtils.VisitType.MODULE;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.util.Lists;
import com.google.inject.spi.DefaultBindingTargetVisitor;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.google.inject.spi.InstanceBinding;
import com.google.inject.spi.LinkedKeyBinding;
import com.google.inject.spi.ProviderInstanceBinding;
import com.google.inject.spi.ProviderLookup;

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
        if(!key.equals(result.k)) {
          continue;
        }
        switch (result.v.type) {
          case INSTANCE:
            if (value instanceof InstanceBinding
                && ((InstanceBinding) value).getInstance().equals(result.v.instance)) {
              found = entry;
          }
          break;
        case LINKED:
          if (value instanceof LinkedKeyBinding
              && ((LinkedKeyBinding) value).getKey().equals(result.v.key)) {
            found = entry;
          }
          break;
        case PROVIDER_INSTANCE:
          if (value instanceof ProviderInstanceBinding
              && ((ProviderInstanceBinding) value).getProviderInstance().get().equals(
                  result.v.instance)) {
            found = entry;
          }
          break;
        }
      }
      if(found == null) {
        fail("Could not find entry: " + result + " in remaining entries: " + entries);
      } else {
        assertTrue(mapbinder.containsElement(found.getValue()));
        entries.remove(found);
      }
    }
    
    if(!entries.isEmpty()) {
      fail("Found all entries of: " + mapResults + ", but more were left over: " + entries);
    }
    
    Key<?> mapOfProvider = adapt(mapKey, mapOfProviderOf(keyType, valueType));
    Key<?> mapOfSetOfProvider = adapt(mapKey, mapOfSetOfProviderOf(keyType, valueType));
    Key<?> mapOfSet = adapt(mapKey, mapOf(keyType, setOf(valueType)));
    Key<?> setOfEntry = adapt(mapKey, setOf(entryOfProviderOf(keyType, valueType)));
    boolean entrySetMatch = false;
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
    assertEquals(allowDuplicates, mapSetMatch);
    assertEquals(allowDuplicates, mapSetProviderMatch);
    assertEquals("other MapBindings found: " + otherMapBindings, expectedMapBindings,
        otherMapBindings.size());
  }
  
  /** Adapts a key, keeping the original annotation, using the new type literal. */
  private static Key<?> adapt(Key<?> mapKey, TypeLiteral<?> resultType) {
    if(mapKey.getAnnotation() != null) {
      return Key.get(resultType, mapKey.getAnnotation());
    } else if(mapKey.getAnnotationType() != null) {
      return Key.get(resultType, mapKey.getAnnotationType());
    } else {
      return Key.get(resultType);
    }
  }
  
  @SuppressWarnings("unchecked")
  private static <T> void mapModuleTest(Key<T> mapKey, TypeLiteral<?> keyType,
      TypeLiteral<?> valueType, Iterable<? extends Module> modules, boolean allowDuplicates,
      int expectedMapBindings, MapResult... results) {
    List<Element> elements = Elements.getElements(modules);
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
    
    Key<?> mapOfProvider = adapt(mapKey, mapOfProviderOf(keyType, valueType));
    Key<?> mapOfSetOfProvider = adapt(mapKey, mapOfSetOfProviderOf(keyType, valueType));
    Key<?> mapOfSet = adapt(mapKey, mapOf(keyType, setOf(valueType)));
    Key<?> setOfEntry = adapt(mapKey, setOf(entryOfProviderOf(keyType, valueType)));    
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
        switch (result.type) {
        case INSTANCE:
          if (item instanceof InstanceBinding
              && ((InstanceBinding) item).getInstance().equals(result.instance)) {
            found = item;
          }
          break;
        case LINKED:
          if (item instanceof LinkedKeyBinding
              && ((LinkedKeyBinding) item).getKey().equals(result.key)) {
            found = item;
          }
          break;
        case PROVIDER_INSTANCE:
          if (item instanceof ProviderInstanceBinding
              && ((ProviderInstanceBinding) item).getProviderInstance().get().equals(
                  result.instance)) {
            found = item;
          }
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

  private static class MapResult<K, V> {
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

  static <T> BindResult instance(T t) {
    return new BindResult<T>(INSTANCE, t, null);
  }

  static <T> BindResult linked(Class<? extends T> clazz) {
    return new BindResult<T>(LINKED, null, Key.get(clazz));
  }

  static <T> BindResult linked(Key<? extends T> key) {
    return new BindResult<T>(LINKED, null, key);
  }

  static <T> BindResult providerInstance(T t) {
    return new BindResult<T>(PROVIDER_INSTANCE, t, null);
  }
  
  /** The kind of binding. */
  static enum BindType { INSTANCE, LINKED, PROVIDER_INSTANCE }
  /** The result of the binding. */
  private static class BindResult<T> {
    private final BindType type;
    private final Key<? extends T> key;
    private final T instance;
    
    private BindResult(BindType type, T instance, Key<? extends T> key) {
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
  }
}

