/**
 * Copyright (C) 2014 Google Inc.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.testing.GcFinalization;
import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Scope;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.BindingImpl;
import com.google.inject.internal.Errors;
/*if[AOP]*/
import com.google.inject.internal.MethodAspect;
/*end[AOP]*/
import com.google.inject.internal.State;
import com.google.inject.internal.WeakKeySet;
import com.google.inject.spi.ProvisionListenerBinding;
import com.google.inject.spi.ScopeBinding;
import com.google.inject.spi.TypeConverterBinding;
import com.google.inject.spi.TypeListenerBinding;

import junit.framework.TestCase;

import java.lang.annotation.Annotation;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tests for {@link WeakKeySet}.
 * <p>
 * Multibinding specific tests can be found in MultibinderTest and MapBinderTest.
 * 
 * @author dweis@google.com (Daniel Weis)
 */
public class WeakKeySetTest extends TestCase {

  private WeakKeySet set;

  @Override
  protected void setUp() throws Exception {
    set = new WeakKeySet(new Object());
  }

  public void testEviction() {
    TestState state = new TestState();
    Key<Integer> key = Key.get(Integer.class);
    Object source = new Object();
    
    WeakReference<Key<Integer>> weakKeyRef = new WeakReference<Key<Integer>>(key);

    set.add(key, state, source);
    assertTrue(set.contains(key));
    assertEquals(1, set.getSources(key).size());
    assertTrue(set.getSources(key).contains(source));

    state = null;

    GcFinalization.awaitFullGc();

    assertFalse(set.contains(Key.get(Integer.class)));
    assertNull(set.getSources(Key.get(Integer.class)));

    // Ensure there are no hanging references.
    key = null;
    GcFinalization.awaitClear(weakKeyRef);
  }
  
  public void testEviction_nullSource() {
    TestState state = new TestState();
    Key<Integer> key = Key.get(Integer.class);
    Object source = null;
    
    WeakReference<Key<Integer>> weakKeyRef = new WeakReference<Key<Integer>>(key);

    set.add(key, state, source);
    assertTrue(set.contains(key));
    assertEquals(1, set.getSources(key).size());
    assertTrue(set.getSources(key).contains(source));

    state = null;

    GcFinalization.awaitFullGc();

    assertFalse(set.contains(Key.get(Integer.class)));
    assertNull(set.getSources(Key.get(Integer.class)));

    // Ensure there are no hanging references.
    key = null;
    GcFinalization.awaitClear(weakKeyRef);
  }

  public void testEviction_keyOverlap_2x() {
    TestState state1 = new TestState();
    TestState state2 = new TestState();
    Key<Integer> key1 = Key.get(Integer.class);
    Key<Integer> key2 = Key.get(Integer.class);
    Object source1 = new Object();
    Object source2 = new Object();

    set.add(key1, state1, source1);
    assertTrue(set.contains(key1));
    assertEquals(1, set.getSources(key1).size());
    assertTrue(set.getSources(key1).contains(source1));

    set.add(key2, state2, source2);
    assertTrue(set.contains(key2));
    assertEquals(2, set.getSources(key2).size());
    assertTrue(set.getSources(key2).containsAll(Arrays.asList(source1, source2)));

    WeakReference<Key<Integer>> weakKey1Ref = new WeakReference<Key<Integer>>(key1);
    WeakReference<Key<Integer>> weakKey2Ref = new WeakReference<Key<Integer>>(key2);
    WeakReference<Object> weakSource1Ref = new WeakReference<Object>(source1);
    WeakReference<Object> weakSource2Ref = new WeakReference<Object>(source2);

    Key<Integer> key = key1 = key2 = Key.get(Integer.class);
    state1 = null;

    GcFinalization.awaitFullGc();

    assertTrue(set.contains(key));
    assertEquals(1, set.getSources(key).size());
    assertTrue(set.getSources(key).contains(source2));
    assertFalse(set.getSources(key).contains(source1));

    source1 = source2 = null;
    
    GcFinalization.awaitClear(weakSource1Ref);
    // Key1 will be referenced as the key in the sources backingSet and won't be
    // GC'd.
    
    // Should not be GC'd until state2 goes away.
    assertNotNull(weakSource2Ref.get());

    state2 = null;

    GcFinalization.awaitFullGc();

    assertFalse(set.contains(key));
    assertNull(set.getSources(key));

    GcFinalization.awaitClear(weakKey2Ref);
    GcFinalization.awaitClear(weakSource2Ref);
    // Now that the backing set is emptied, key1 is released.
    GcFinalization.awaitClear(weakKey1Ref);
  }
  
  public void testNoEviction_keyOverlap_2x() {
    TestState state1 = new TestState();
    TestState state2 = new TestState();
    Key<Integer> key1 = Key.get(Integer.class);
    Key<Integer> key2 = Key.get(Integer.class);
    Object source1 = new Object();
    Object source2 = new Object();

    set.add(key1, state1, source1);
    assertTrue(set.contains(key1));
    assertEquals(1, set.getSources(key1).size());
    assertTrue(set.getSources(key1).contains(source1));

    set.add(key2, state2, source2);
    assertTrue(set.contains(key2));
    assertEquals(2, set.getSources(key2).size());
    assertTrue(set.getSources(key1).containsAll(Arrays.asList(source1, source2)));

    WeakReference<Key<Integer>> weakKey1Ref = new WeakReference<Key<Integer>>(key1);
    WeakReference<Key<Integer>> weakKey2Ref = new WeakReference<Key<Integer>>(key2);

    Key<Integer> key = key1 = key2 = Key.get(Integer.class);

    GcFinalization.awaitFullGc();

    assertTrue(set.contains(key));
    assertEquals(2, set.getSources(key).size());
    assertTrue(set.getSources(key1).containsAll(Arrays.asList(source1, source2)));

    // Ensure the keys don't get GC'd when states are still referenced. key1 will be present in the
    // as the map key but key2 could be GC'd if the implementation does something wrong.
    assertNotNull(weakKey1Ref.get());
    assertNotNull(weakKey2Ref.get());
  }

  public void testEviction_keyAndSourceOverlap_null() {
    TestState state1 = new TestState();
    TestState state2 = new TestState();
    Key<Integer> key1 = Key.get(Integer.class);
    Key<Integer> key2 = Key.get(Integer.class);
    Object source = null;

    set.add(key1, state1, source);
    assertTrue(set.contains(key1));
    assertEquals(1, set.getSources(key1).size());
    assertTrue(set.getSources(key1).contains(source));

    set.add(key2, state2, source);
    assertTrue(set.contains(key2));

    // Same source so still only one value.
    assertEquals(1, set.getSources(key2).size());
    assertTrue(set.getSources(key1).contains(source));

    WeakReference<Key<Integer>> weakKey1Ref = new WeakReference<Key<Integer>>(key1);
    WeakReference<Key<Integer>> weakKey2Ref = new WeakReference<Key<Integer>>(key2);
    WeakReference<Object> weakSourceRef = new WeakReference<Object>(source);

    Key<Integer> key = key1 = key2 = Key.get(Integer.class);
    state1 = null;

    GcFinalization.awaitFullGc();

    assertTrue(set.contains(key));

    // Should still have a single source.
    assertEquals(1, set.getSources(key).size());
    assertTrue(set.getSources(key1).contains(source));
    
    source = null;

    GcFinalization.awaitClear(weakSourceRef);
    // Key1 will be referenced as the key in the sources backingSet and won't be
    // GC'd.

    state2 = null;

    GcFinalization.awaitFullGc();

    assertFalse(set.contains(key));
    assertNull(set.getSources(key));

    GcFinalization.awaitClear(weakKey2Ref);
    GcFinalization.awaitClear(weakSourceRef);
    // Now that the backing set is emptied, key1 is released.
    GcFinalization.awaitClear(weakKey1Ref);
  }
  
  public void testEviction_keyAndSourceOverlap_nonNull() {
    TestState state1 = new TestState();
    TestState state2 = new TestState();
    Key<Integer> key1 = Key.get(Integer.class);
    Key<Integer> key2 = Key.get(Integer.class);
    Object source = new Object();

    set.add(key1, state1, source);
    assertTrue(set.contains(key1));
    assertEquals(1, set.getSources(key1).size());
    assertTrue(set.getSources(key1).contains(source));

    set.add(key2, state2, source);
    assertTrue(set.contains(key2));

    // Same source so still only one value.
    assertEquals(1, set.getSources(key2).size());
    assertTrue(set.getSources(key1).contains(source));

    WeakReference<Key<Integer>> weakKey1Ref = new WeakReference<Key<Integer>>(key1);
    WeakReference<Key<Integer>> weakKey2Ref = new WeakReference<Key<Integer>>(key2);
    WeakReference<Object> weakSourceRef = new WeakReference<Object>(source);

    Key<Integer> key = key1 = key2 = Key.get(Integer.class);
    state1 = null;

    GcFinalization.awaitFullGc();

    assertTrue(set.contains(key));

    // Should still have a single source.
    assertEquals(1, set.getSources(key).size());
    assertTrue(set.getSources(key1).contains(source));
    
    source = null;

    GcFinalization.awaitFullGc();
    assertNotNull(weakSourceRef.get());
    // Key1 will be referenced as the key in the sources backingSet and won't be
    // GC'd.

    state2 = null;

    GcFinalization.awaitFullGc();

    assertFalse(set.contains(key));
    assertNull(set.getSources(key));

    GcFinalization.awaitClear(weakKey2Ref);
    GcFinalization.awaitClear(weakSourceRef);
    // Now that the backing set is emptied, key1 is released.
    GcFinalization.awaitClear(weakKey1Ref);
  }

  public void testEviction_keyOverlap_3x() {
    TestState state1 = new TestState();
    TestState state2 = new TestState();
    TestState state3 = new TestState();
    Key<Integer> key1 = Key.get(Integer.class);
    Key<Integer> key2 = Key.get(Integer.class);
    Key<Integer> key3 = Key.get(Integer.class);
    Object source1 = new Object();
    Object source2 = new Object();
    Object source3 = new Object();

    set.add(key1, state1, source1);
    assertTrue(set.contains(key1));
    assertEquals(1, set.getSources(key1).size());
    assertTrue(set.getSources(key1).contains(source1));

    set.add(key2, state2, source2);
    assertTrue(set.contains(key2));
    assertEquals(2, set.getSources(key2).size());
    assertTrue(set.getSources(key1).containsAll(Arrays.asList(source1, source2)));

    set.add(key3, state3, source3);
    assertTrue(set.contains(key3));
    assertEquals(3, set.getSources(key3).size());
    assertTrue(set.getSources(key1).containsAll(Arrays.asList(source1, source2, source3)));

    WeakReference<Key<Integer>> weakKey1Ref = new WeakReference<Key<Integer>>(key1);
    WeakReference<Key<Integer>> weakKey2Ref = new WeakReference<Key<Integer>>(key2);
    WeakReference<Key<Integer>> weakKey3Ref = new WeakReference<Key<Integer>>(key3);
    WeakReference<Object> weakSource1Ref = new WeakReference<Object>(source1);
    WeakReference<Object> weakSource2Ref = new WeakReference<Object>(source2);
    WeakReference<Object> weakSource3Ref = new WeakReference<Object>(source3);

    Key<Integer> key = key1 = key2 = key3 = Key.get(Integer.class);
    state1 = null;

    GcFinalization.awaitFullGc();

    assertTrue(set.contains(key));
    assertEquals(2, set.getSources(key).size());
    assertTrue(set.getSources(key).containsAll(Arrays.asList(source2, source3)));

    source1 = null;
    // Key1 will be referenced as the key in the sources backingSet and won't be
    // GC'd.
    GcFinalization.awaitClear(weakSource1Ref);

    state2 = null;
    GcFinalization.awaitFullGc();

    assertTrue(set.contains(key));
    assertEquals(1, set.getSources(key).size());
    assertTrue(set.getSources(key).contains(source3));

    GcFinalization.awaitClear(weakKey2Ref);
    
    source2 = null;
    GcFinalization.awaitClear(weakSource2Ref);
    // Key1 will be referenced as the key in the sources backingSet and won't be
    // GC'd.

    state3 = null;
    GcFinalization.awaitFullGc();

    assertFalse(set.contains(Key.get(Integer.class)));
    assertNull(set.getSources(Key.get(Integer.class)));

    GcFinalization.awaitClear(weakKey3Ref);
    source3 = null;
    GcFinalization.awaitClear(weakSource3Ref);
    // Now that the backing set is emptied, key1 is released.
    GcFinalization.awaitClear(weakKey1Ref);
  }

  public void testWeakKeySet_integration() {
    Injector parentInjector = Guice.createInjector(new AbstractModule() {
          @Override protected void configure() {
            bind(Integer.class).toInstance(4);
          }
        });
    assertNotBlacklisted(parentInjector, Key.get(String.class));

    Injector childInjector = parentInjector.createChildInjector(new AbstractModule() {
      @Override protected void configure() {
        bind(String.class).toInstance("bar");
      }
    });
    WeakReference<Injector> weakRef = new WeakReference<Injector>(childInjector);
    assertBlacklisted(parentInjector, Key.get(String.class));
    
    // Clear the ref, GC, and ensure that we are no longer blacklisting.
    childInjector = null;
    GcFinalization.awaitClear(weakRef);
    assertNotBlacklisted(parentInjector, Key.get(String.class));
  }
  
  public void testWeakKeySet_integration_multipleChildren() {
    Injector parentInjector = Guice.createInjector(new AbstractModule() {
          @Override protected void configure() {
            bind(Integer.class).toInstance(4);
          }
        });
    assertNotBlacklisted(parentInjector, Key.get(String.class));
    assertNotBlacklisted(parentInjector, Key.get(Long.class));

    Injector childInjector1 = parentInjector.createChildInjector(new AbstractModule() {
      @Override protected void configure() {
        bind(String.class).toInstance("foo");
      }
    });
    WeakReference<Injector> weakRef1 = new WeakReference<Injector>(childInjector1);
    assertBlacklisted(parentInjector, Key.get(String.class));
    assertNotBlacklisted(parentInjector, Key.get(Long.class));
    
    Injector childInjector2 = parentInjector.createChildInjector(new AbstractModule() {
      @Override protected void configure() {
        bind(Long.class).toInstance(6L);
      }
    });
    WeakReference<Injector> weakRef2 = new WeakReference<Injector>(childInjector2);
    assertBlacklisted(parentInjector, Key.get(String.class));
    assertBlacklisted(parentInjector, Key.get(Long.class));
    
    // Clear ref1, GC, and ensure that we still blacklist.
    childInjector1 = null;
    GcFinalization.awaitClear(weakRef1);
    assertNotBlacklisted(parentInjector, Key.get(String.class));
    assertBlacklisted(parentInjector, Key.get(Long.class));

    // Clear the ref, GC, and ensure that we are no longer blacklisting.
    childInjector2 = null;
    GcFinalization.awaitClear(weakRef2);
    assertNotBlacklisted(parentInjector, Key.get(String.class));
    assertNotBlacklisted(parentInjector, Key.get(Long.class));
  }
  
  public void testWeakKeySet_integration_multipleChildren_overlappingKeys() {
    Injector parentInjector = Guice.createInjector(new AbstractModule() {
          @Override protected void configure() {
            bind(Integer.class).toInstance(4);
          }
        });
    assertNotBlacklisted(parentInjector, Key.get(String.class));

    Injector childInjector1 = parentInjector.createChildInjector(new AbstractModule() {
      @Override protected void configure() {
        bind(String.class).toInstance("foo");
      }
    });
    WeakReference<Injector> weakRef1 = new WeakReference<Injector>(childInjector1);
    assertBlacklisted(parentInjector, Key.get(String.class));
    
    Injector childInjector2 = parentInjector.createChildInjector(new AbstractModule() {
      @Override protected void configure() {
        bind(String.class).toInstance("bar");
      }
    });
    WeakReference<Injector> weakRef2 = new WeakReference<Injector>(childInjector2);
    assertBlacklisted(parentInjector, Key.get(String.class));
    
    // Clear ref1, GC, and ensure that we still blacklist.
    childInjector1 = null;
    GcFinalization.awaitClear(weakRef1);
    assertBlacklisted(parentInjector, Key.get(String.class));

    // Clear the ref, GC, and ensure that we are no longer blacklisting.
    childInjector2 = null;
    GcFinalization.awaitClear(weakRef2);
    assertNotBlacklisted(parentInjector, Key.get(String.class));
  }
  
  private static void assertBlacklisted(Injector injector, Key<?> key) {
    assertBlacklistState(injector, key, true);
  }
  
  private static void assertNotBlacklisted(Injector injector, Key<?> key) {
    assertBlacklistState(injector, key, false);
  }

  private static void assertBlacklistState(Injector injector, Key<?> key, boolean isBlacklisted) {
    assertEquals(isBlacklisted, ((InjectorImpl) injector).state.isBlacklisted(key));
  }
  
  private static class TestState implements State {
    public State parent() {
      return new TestState();
    }

    public <T> BindingImpl<T> getExplicitBinding(Key<T> key) {
      return null;
    }

    public Map<Key<?>, Binding<?>> getExplicitBindingsThisLevel() {
      throw new UnsupportedOperationException();
    }

    public void putBinding(Key<?> key, BindingImpl<?> binding) {
      throw new UnsupportedOperationException();
    }

    public ScopeBinding getScopeBinding(Class<? extends Annotation> scopingAnnotation) {
      return null;
    }

    public void putScopeBinding(Class<? extends Annotation> annotationType, ScopeBinding scope) {
      throw new UnsupportedOperationException();
    }

    public void addConverter(TypeConverterBinding typeConverterBinding) {
      throw new UnsupportedOperationException();
    }

    public TypeConverterBinding getConverter(String stringValue, TypeLiteral<?> type, Errors errors,
        Object source) {
      throw new UnsupportedOperationException();
    }

    public Iterable<TypeConverterBinding> getConvertersThisLevel() {
      return ImmutableSet.of();
    }

    /*if[AOP]*/
    public void addMethodAspect(MethodAspect methodAspect) {
      throw new UnsupportedOperationException();
    }

    public ImmutableList<MethodAspect> getMethodAspects() {
      return ImmutableList.of();
    }
    /*end[AOP]*/

    public void addTypeListener(TypeListenerBinding typeListenerBinding) {
      throw new UnsupportedOperationException();
    }

    public List<TypeListenerBinding> getTypeListenerBindings() {
      return ImmutableList.of();
    }

    public void addProvisionListener(ProvisionListenerBinding provisionListenerBinding) {
      throw new UnsupportedOperationException();
    }

    public List<ProvisionListenerBinding> getProvisionListenerBindings() {
      return ImmutableList.of();
    }

    public void blacklist(Key<?> key, State state, Object source) {
    }

    public boolean isBlacklisted(Key<?> key) {
      return true;
    }

    public Set<Object> getSourcesForBlacklistedKey(Key<?> key) {
      throw new UnsupportedOperationException();
    }

    public Object lock() {
      throw new UnsupportedOperationException();
    }

    public Map<Class<? extends Annotation>, Scope> getScopes() {
      return ImmutableMap.of();
    }
  }
}
