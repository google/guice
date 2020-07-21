/*
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

import static com.google.inject.Asserts.awaitClear;
import static com.google.inject.Asserts.awaitFullGc;
import static com.google.inject.internal.WeakKeySetUtils.assertBlacklisted;
import static com.google.inject.internal.WeakKeySetUtils.assertInSet;
import static com.google.inject.internal.WeakKeySetUtils.assertNotBlacklisted;
import static com.google.inject.internal.WeakKeySetUtils.assertNotInSet;
import static com.google.inject.internal.WeakKeySetUtils.assertSourceNotInSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Scope;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.InjectionRequest;
import com.google.inject.spi.MembersInjectorLookup;
import com.google.inject.spi.ModuleAnnotatedMethodScannerBinding;
import com.google.inject.spi.ProviderLookup;
import com.google.inject.spi.ProvisionListenerBinding;
import com.google.inject.spi.ScopeBinding;
import com.google.inject.spi.StaticInjectionRequest;
import com.google.inject.spi.TypeConverterBinding;
import com.google.inject.spi.TypeListenerBinding;
import java.lang.annotation.Annotation;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import junit.framework.TestCase;

/**
 * Tests for {@link WeakKeySet}.
 *
 * <p>Multibinding specific tests can be found in MultibinderTest and MapBinderTest.
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
    TestInjectorBindingData bindingData = new TestInjectorBindingData();
    Key<Integer> key = Key.get(Integer.class);
    Object source = new Object();

    WeakReference<Key<Integer>> weakKeyRef = new WeakReference<>(key);

    set.add(key, bindingData, source);
    assertInSet(set, key, 1, source);

    bindingData = null;

    awaitFullGc();

    assertNotInSet(set, Key.get(Integer.class));

    // Ensure there are no hanging references.
    key = null;
    awaitClear(weakKeyRef);
  }

  public void testEviction_nullSource() {
    TestInjectorBindingData bindingData = new TestInjectorBindingData();
    Key<Integer> key = Key.get(Integer.class);
    Object source = null;

    WeakReference<Key<Integer>> weakKeyRef = new WeakReference<>(key);

    set.add(key, bindingData, source);
    assertInSet(set, key, 1, source);

    bindingData = null;

    awaitFullGc();

    assertNotInSet(set, Key.get(Integer.class));

    // Ensure there are no hanging references.
    key = null;
    awaitClear(weakKeyRef);
  }

  public void testEviction_keyOverlap_2x() {
    TestInjectorBindingData bindingData1 = new TestInjectorBindingData();
    TestInjectorBindingData bindingData2 = new TestInjectorBindingData();
    Key<Integer> key1 = Key.get(Integer.class);
    Key<Integer> key2 = Key.get(Integer.class);
    Object source1 = new Object();
    Object source2 = new Object();

    set.add(key1, bindingData1, source1);
    assertInSet(set, key1, 1, source1);

    set.add(key2, bindingData2, source2);
    assertInSet(set, key2, 2, source1, source2);

    WeakReference<Key<Integer>> weakKey1Ref = new WeakReference<>(key1);
    WeakReference<Key<Integer>> weakKey2Ref = new WeakReference<>(key2);
    WeakReference<Object> weakSource1Ref = new WeakReference<>(source1);
    WeakReference<Object> weakSource2Ref = new WeakReference<>(source2);

    Key<Integer> key = key1 = key2 = Key.get(Integer.class);
    bindingData1 = null;

    awaitFullGc();

    assertSourceNotInSet(set, key, source1);
    assertInSet(set, key, 1, source2);

    // Clear source1 and source2 fields so the objects can be GCed.
    Object unused = source1 = source2 = null;

    awaitClear(weakSource1Ref);
    // Key1 will be referenced as the key in the sources backingSet and won't be
    // GC'd.

    // Should not be GC'd until bindingData2 goes away.
    assertNotNull(weakSource2Ref.get());

    bindingData2 = null;

    awaitFullGc();

    assertNotInSet(set, key);

    awaitClear(weakKey2Ref);
    awaitClear(weakSource2Ref);
    // Now that the backing set is emptied, key1 is released.
    awaitClear(weakKey1Ref);
  }

  public void testNoEviction_keyOverlap_2x() {
    TestInjectorBindingData bindingData1 = new TestInjectorBindingData();
    TestInjectorBindingData bindingData2 = new TestInjectorBindingData();
    Key<Integer> key1 = Key.get(Integer.class);
    Key<Integer> key2 = Key.get(Integer.class);
    Object source1 = new Object();
    Object source2 = new Object();

    set.add(key1, bindingData1, source1);
    assertInSet(set, key1, 1, source1);

    set.add(key2, bindingData2, source2);
    assertInSet(set, key2, 2, source1, source2);

    WeakReference<Key<Integer>> weakKey1Ref = new WeakReference<>(key1);
    WeakReference<Key<Integer>> weakKey2Ref = new WeakReference<>(key2);

    Key<Integer> key = key1 = key2 = Key.get(Integer.class);

    awaitFullGc();
    assertInSet(set, key, 2, source1, source2);

    // Ensure the keys don't get GC'd when InjectorBindingData objects are still referenced. key1
    // will be present in the
    // as the map key but key2 could be GC'd if the implementation does something wrong.
    assertNotNull(weakKey1Ref.get());
    assertNotNull(weakKey2Ref.get());
  }

  public void testEviction_keyAndSourceOverlap_null() {
    TestInjectorBindingData bindingData1 = new TestInjectorBindingData();
    TestInjectorBindingData bindingData2 = new TestInjectorBindingData();
    Key<Integer> key1 = Key.get(Integer.class);
    Key<Integer> key2 = Key.get(Integer.class);
    Object source = null;

    set.add(key1, bindingData1, source);
    assertInSet(set, key1, 1, source);

    set.add(key2, bindingData2, source);
    // Same source so still only one value.
    assertInSet(set, key2, 1, source);
    assertInSet(set, key1, 1, source);

    WeakReference<Key<Integer>> weakKey1Ref = new WeakReference<>(key1);
    WeakReference<Key<Integer>> weakKey2Ref = new WeakReference<>(key2);
    WeakReference<Object> weakSourceRef = new WeakReference<>(source);

    Key<Integer> key = key1 = key2 = Key.get(Integer.class);
    bindingData1 = null;

    awaitFullGc();
    // Should still have a single source.
    assertInSet(set, key, 1, source);

    source = null;

    awaitClear(weakSourceRef);
    // Key1 will be referenced as the key in the sources backingSet and won't be
    // GC'd.

    bindingData2 = null;

    awaitFullGc();
    assertNotInSet(set, key);

    awaitClear(weakKey2Ref);
    awaitClear(weakSourceRef);
    // Now that the backing set is emptied, key1 is released.
    awaitClear(weakKey1Ref);
  }

  public void testEviction_keyAndSourceOverlap_nonNull() {
    TestInjectorBindingData bindingData1 = new TestInjectorBindingData();
    TestInjectorBindingData bindingData2 = new TestInjectorBindingData();
    Key<Integer> key1 = Key.get(Integer.class);
    Key<Integer> key2 = Key.get(Integer.class);
    Object source = new Object();

    set.add(key1, bindingData1, source);
    assertInSet(set, key1, 1, source);

    set.add(key2, bindingData2, source);
    // Same source so still only one value.
    assertInSet(set, key2, 1, source);

    WeakReference<Key<Integer>> weakKey1Ref = new WeakReference<>(key1);
    WeakReference<Key<Integer>> weakKey2Ref = new WeakReference<>(key2);
    WeakReference<Object> weakSourceRef = new WeakReference<>(source);

    Key<Integer> key = key1 = key2 = Key.get(Integer.class);
    bindingData1 = null;

    awaitFullGc();

    // Same source so still only one value.
    assertInSet(set, key, 1, source);
    assertInSet(set, key1, 1, source);

    source = null;

    awaitFullGc();
    assertNotNull(weakSourceRef.get());
    // Key1 will be referenced as the key in the sources backingSet and won't be
    // GC'd.

    bindingData2 = null;

    awaitFullGc();

    assertNotInSet(set, key);

    awaitClear(weakKey2Ref);
    awaitClear(weakSourceRef);
    // Now that the backing set is emptied, key1 is released.
    awaitClear(weakKey1Ref);
  }

  public void testEviction_keyOverlap_3x() {
    TestInjectorBindingData bindingData1 = new TestInjectorBindingData();
    TestInjectorBindingData bindingData2 = new TestInjectorBindingData();
    TestInjectorBindingData bindingData3 = new TestInjectorBindingData();
    Key<Integer> key1 = Key.get(Integer.class);
    Key<Integer> key2 = Key.get(Integer.class);
    Key<Integer> key3 = Key.get(Integer.class);
    Object source1 = new Object();
    Object source2 = new Object();
    Object source3 = new Object();

    set.add(key1, bindingData1, source1);
    assertInSet(set, key1, 1, source1);

    set.add(key2, bindingData2, source2);
    assertInSet(set, key1, 2, source1, source2);

    set.add(key3, bindingData3, source3);
    assertInSet(set, key1, 3, source1, source2, source3);

    WeakReference<Key<Integer>> weakKey1Ref = new WeakReference<>(key1);
    WeakReference<Key<Integer>> weakKey2Ref = new WeakReference<>(key2);
    WeakReference<Key<Integer>> weakKey3Ref = new WeakReference<>(key3);
    WeakReference<Object> weakSource1Ref = new WeakReference<>(source1);
    WeakReference<Object> weakSource2Ref = new WeakReference<>(source2);
    WeakReference<Object> weakSource3Ref = new WeakReference<>(source3);

    Key<Integer> key = key1 = key2 = key3 = Key.get(Integer.class);
    bindingData1 = null;

    awaitFullGc();
    assertSourceNotInSet(set, key, source1);
    assertInSet(set, key, 2, source2, source3);

    source1 = null;
    // Key1 will be referenced as the key in the sources backingSet and won't be
    // GC'd.
    awaitClear(weakSource1Ref);

    bindingData2 = null;
    awaitFullGc();
    assertSourceNotInSet(set, key, source2);
    assertInSet(set, key, 1, source3);

    awaitClear(weakKey2Ref);

    source2 = null;
    awaitClear(weakSource2Ref);
    // Key1 will be referenced as the key in the sources backingSet and won't be
    // GC'd.

    bindingData3 = null;
    awaitFullGc();
    assertNotInSet(set, key);

    awaitClear(weakKey3Ref);
    source3 = null;
    awaitClear(weakSource3Ref);
    // Now that the backing set is emptied, key1 is released.
    awaitClear(weakKey1Ref);
  }

  public void testWeakKeySet_integration() {
    Injector parentInjector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(Integer.class).toInstance(4);
              }
            });
    assertNotBlacklisted(parentInjector, Key.get(String.class));

    Injector childInjector =
        parentInjector.createChildInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(String.class).toInstance("bar");
              }
            });
    WeakReference<Injector> weakRef = new WeakReference<>(childInjector);
    assertBlacklisted(parentInjector, Key.get(String.class));

    // Clear the ref, GC, and ensure that we are no longer blacklisting.
    childInjector = null;
    awaitClear(weakRef);
    assertNotBlacklisted(parentInjector, Key.get(String.class));
  }

  public void testWeakKeySet_integration_multipleChildren() {
    Injector parentInjector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(Integer.class).toInstance(4);
              }
            });
    assertNotBlacklisted(parentInjector, Key.get(String.class));
    assertNotBlacklisted(parentInjector, Key.get(Long.class));

    Injector childInjector1 =
        parentInjector.createChildInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(String.class).toInstance("foo");
              }
            });
    WeakReference<Injector> weakRef1 = new WeakReference<>(childInjector1);
    assertBlacklisted(parentInjector, Key.get(String.class));
    assertNotBlacklisted(parentInjector, Key.get(Long.class));

    Injector childInjector2 =
        parentInjector.createChildInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(Long.class).toInstance(6L);
              }
            });
    WeakReference<Injector> weakRef2 = new WeakReference<>(childInjector2);
    assertBlacklisted(parentInjector, Key.get(String.class));
    assertBlacklisted(parentInjector, Key.get(Long.class));

    // Clear ref1, GC, and ensure that we still blacklist.
    childInjector1 = null;
    awaitClear(weakRef1);
    assertNotBlacklisted(parentInjector, Key.get(String.class));
    assertBlacklisted(parentInjector, Key.get(Long.class));

    // Clear the ref, GC, and ensure that we are no longer blacklisting.
    childInjector2 = null;
    awaitClear(weakRef2);
    assertNotBlacklisted(parentInjector, Key.get(String.class));
    assertNotBlacklisted(parentInjector, Key.get(Long.class));
  }

  public void testWeakKeySet_integration_multipleChildren_overlappingKeys() {
    Injector parentInjector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(Integer.class).toInstance(4);
              }
            });
    assertNotBlacklisted(parentInjector, Key.get(String.class));

    Injector childInjector1 =
        parentInjector.createChildInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(String.class).toInstance("foo");
              }
            });
    WeakReference<Injector> weakRef1 = new WeakReference<>(childInjector1);
    assertBlacklisted(parentInjector, Key.get(String.class));

    Injector childInjector2 =
        parentInjector.createChildInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(String.class).toInstance("bar");
              }
            });
    WeakReference<Injector> weakRef2 = new WeakReference<>(childInjector2);
    assertBlacklisted(parentInjector, Key.get(String.class));

    // Clear ref1, GC, and ensure that we still blacklist.
    childInjector1 = null;
    awaitClear(weakRef1);
    assertBlacklisted(parentInjector, Key.get(String.class));

    // Clear the ref, GC, and ensure that we are no longer blacklisting.
    childInjector2 = null;
    awaitClear(weakRef2);
    assertNotBlacklisted(parentInjector, Key.get(String.class));
  }

  private static class TestInjectorBindingData extends InjectorBindingData {
    TestInjectorBindingData() {
      super(Optional.empty());
    }

    @Override
    public Optional<InjectorBindingData> parent() {
      return Optional.of(new TestInjectorBindingData());
    }

    @Override
    public <T> BindingImpl<T> getExplicitBinding(Key<T> key) {
      return null;
    }

    @Override
    public Map<Key<?>, Binding<?>> getExplicitBindingsThisLevel() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void putBinding(Key<?> key, BindingImpl<?> binding) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void putProviderLookup(ProviderLookup<?> lookup) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Set<ProviderLookup<?>> getProviderLookupsThisLevel() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void putStaticInjectionRequest(StaticInjectionRequest staticInjectionRequest) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Set<StaticInjectionRequest> getStaticInjectionRequestsThisLevel() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Set<InjectionRequest<?>> getInjectionRequestsThisLevel() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Set<MembersInjectorLookup<?>> getMembersInjectorLookupsThisLevel() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void putInjectionRequest(InjectionRequest<?> injectionRequest) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void putMembersInjectorLookup(MembersInjectorLookup<?> membersInjectorLookup) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ScopeBinding getScopeBinding(Class<? extends Annotation> scopingAnnotation) {
      return null;
    }

    @Override
    public void putScopeBinding(Class<? extends Annotation> annotationType, ScopeBinding scope) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void addConverter(TypeConverterBinding typeConverterBinding) {
      throw new UnsupportedOperationException();
    }

    @Override
    public TypeConverterBinding getConverter(
        String stringValue, TypeLiteral<?> type, Errors errors, Object source) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Iterable<TypeConverterBinding> getConvertersThisLevel() {
      return ImmutableSet.of();
    }

    /*if[AOP]*/
    @Override
    public void addMethodAspect(MethodAspect methodAspect) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableList<MethodAspect> getMethodAspects() {
      return ImmutableList.of();
    }
    /*end[AOP]*/

    @Override
    public void addTypeListener(TypeListenerBinding typeListenerBinding) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableList<TypeListenerBinding> getTypeListenerBindings() {
      return ImmutableList.of();
    }

    @Override
    public void addProvisionListener(ProvisionListenerBinding provisionListenerBinding) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableList<ProvisionListenerBinding> getProvisionListenerBindings() {
      return ImmutableList.of();
    }

    @Override
    public void addScanner(ModuleAnnotatedMethodScannerBinding scanner) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableList<ModuleAnnotatedMethodScannerBinding> getScannerBindings() {
      return ImmutableList.of();
    }

    @Override
    public Map<Class<? extends Annotation>, Scope> getScopes() {
      return ImmutableMap.of();
    }

    @Override
    public List<ScopeBinding> getScopeBindingsThisLevel() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableList<TypeListenerBinding> getTypeListenerBindingsThisLevel() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableList<ProvisionListenerBinding> getProvisionListenerBindingsThisLevel() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableList<ModuleAnnotatedMethodScannerBinding> getScannerBindingsThisLevel() {
      throw new UnsupportedOperationException();
    }
  }
}
