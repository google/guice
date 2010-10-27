/*
 * Copyright (C) 2009 Google Inc.
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

package com.google.inject.internal.util;

import com.google.inject.internal.util.ComputationException;
import com.google.inject.internal.util.CustomConcurrentHashMap.Impl;
import com.google.inject.internal.util.ExpirationTimer;
import com.google.inject.internal.util.MapMaker;
import com.google.inject.internal.util.Maps;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit tests for MapMaker. Also less directly serves as the test suite for
 * CustomConcurrentHashMap.
 */
public class MapMakerTestSuite extends TestCase {

  public static Test suite() {
    TestSuite suite = new TestSuite();

    suite.addTestSuite(MakerTest.class);
    suite.addTestSuite(RecursiveComputationTest.class);
    suite.addTestSuite(ReferenceMapTest.class);
    suite.addTestSuite(ComputingTest.class);
    suite.addTest(ReferenceCombinationTestSuite.suite());
    suite.addTestSuite(ExpiringReferenceMapTest.class);
    suite.addTestSuite(ExpiringComputingReferenceMapTest.class);

    return suite;
  }

  public static class MakerTest extends TestCase {
    public void testSizingDefaults() {
      Impl<?, ?, ?> map = makeCustomMap(new MapMaker());
      assertEquals(16, map.segments.length); // concurrency level
      assertEquals(1, map.segments[0].table.length()); // capacity / conc level
      assertEquals(0.75f, map.loadFactor);
    }

    public void testInitialCapacity_small() {
      MapMaker maker = new MapMaker().initialCapacity(17);
      Impl<?, ?, ?> map = makeCustomMap(maker);

      assertEquals(2, map.segments[0].table.length());
    }

    public void testInitialCapacity_smallest() {
      MapMaker maker = new MapMaker().initialCapacity(0);
      Impl<?, ?, ?> map = makeCustomMap(maker);

      // 1 is as low as it goes, not 0. it feels dirty to know this/test this.
      assertEquals(1, map.segments[0].table.length());
    }

    public void testInitialCapacity_large() {
      new MapMaker().initialCapacity(Integer.MAX_VALUE);
      // that the maker didn't blow up is enough;
      // don't actually create this monster!
    }

    public void testInitialCapacity_negative() {
      MapMaker maker = new MapMaker();
      try {
        maker.initialCapacity(-1);
        fail();
      } catch (IllegalArgumentException expected) {
      }
    }

    // TODO: enable when ready
    public void xtestInitialCapacity_setTwice() {
      MapMaker maker = new MapMaker().initialCapacity(16);
      try {
        // even to the same value is not allowed
        maker.initialCapacity(16);
        fail();
      } catch (IllegalArgumentException expected) {
      }
    }

    public void testLoadFactor_small() {
      MapMaker maker = new MapMaker().loadFactor(Float.MIN_VALUE);
      Impl<?, ?, ?> map = makeCustomMap(maker);
      assertEquals(Float.MIN_VALUE, map.loadFactor);

      // has no other effect until we add an entry (which would be bad)
      assertEquals(1, map.segments[0].table.length());
    }

    public void testLoadFactor_large() {
      MapMaker maker = new MapMaker().loadFactor(Float.MAX_VALUE);
      Impl<?, ?, ?> map = makeCustomMap(maker);
      assertEquals(Float.MAX_VALUE, map.loadFactor);

      // these tables will never grow... we could add a ton of entries to
      // check that if we wanted to.
      assertEquals(1, map.segments[0].table.length());
    }

    public void testLoadFactor_zero() {
      MapMaker maker = new MapMaker();
      try {
        maker.loadFactor(0);
        fail();
      } catch (IllegalArgumentException expected) {
      }
    }

    // TODO: enable when ready
    public void xtestLoadFactor_setTwice() {
      MapMaker maker = new MapMaker().loadFactor(0.75f);
      try {
        // even to the same value is not allowed
        maker.loadFactor(0.75f);
        fail();
      } catch (IllegalArgumentException expected) {
      }
    }

    public void testConcurrencyLevel_small() {
      MapMaker maker = new MapMaker().concurrencyLevel(1);
      Impl<?, ?, ?> map = makeCustomMap(maker);
      assertEquals(1, map.segments.length);
    }

    public void testConcurrencyLevel_large() {
      new MapMaker().concurrencyLevel(Integer.MAX_VALUE);
      // don't actually build this beast
    }

    public void testConcurrencyLevel_zero() {
      MapMaker maker = new MapMaker();
      try {
        maker.concurrencyLevel(0);
        fail();
      } catch (IllegalArgumentException expected) {
      }
    }

    // TODO: enable when ready
    public void xtestConcurrencyLevel_setTwice() {
      MapMaker maker = new MapMaker().concurrencyLevel(16);
      try {
        // even to the same value is not allowed
        maker.concurrencyLevel(16);
        fail();
      } catch (IllegalArgumentException expected) {
      }
    }

    public void testKeyStrengthSetTwice() {
      MapMaker maker1 = new MapMaker().weakKeys();
      try {
        maker1.weakKeys();
        fail();
      } catch (IllegalStateException expected) {
      }

      MapMaker maker2 = new MapMaker().softKeys();
      try {
        maker2.softKeys();
        fail();
      } catch (IllegalStateException expected) {
      }

      MapMaker maker3 = new MapMaker().weakKeys();
      try {
        maker3.softKeys();
        fail();
      } catch (IllegalStateException expected) {
      }
    }

    public void testValueStrengthSetTwice() {
      MapMaker maker1 = new MapMaker().weakValues();
      try {
        maker1.weakValues();
        fail();
      } catch (IllegalStateException expected) {
      }

      MapMaker maker2 = new MapMaker().softValues();
      try {
        maker2.softValues();
        fail();
      } catch (IllegalStateException expected) {
      }

      MapMaker maker3 = new MapMaker().weakValues();
      try {
        maker3.softValues();
        fail();
      } catch (IllegalStateException expected) {
      }
    }

    public void testExpiration_small() {
      new MapMaker().expiration(1, NANOSECONDS);
      // well, it didn't blow up.
    }

    public void testExpiration_setTwice() {
      MapMaker maker = new MapMaker().expiration(3600, SECONDS);
      try {
        // even to the same value is not allowed
        maker.expiration(3600, SECONDS);
        fail();
      } catch (IllegalStateException expected) {
      }
    }

    public void testReturnsPlainConcurrentHashMapWhenPossible() {
      Map<?, ?> map = new MapMaker()
          .concurrencyLevel(5)
          .loadFactor(0.5f)
          .initialCapacity(5)
          .makeMap();
      assertTrue(map instanceof ConcurrentHashMap);
    }

    private static Impl<?, ?, ?> makeCustomMap(MapMaker maker) {
      // Use makeComputingMap() to force it to return CCHM.Impl, not
      // ConcurrentHashMap.
      return (Impl<?, ?, ?>) maker.makeComputingMap(new Function<Object, Object>() {
        public Object apply(@Nullable Object from) {
          return from;
        }
      });
    }
  }

  public static class RecursiveComputationTest extends TestCase {

    Function<Integer, String> recursiveComputer
        = new Function<Integer, String>() {
      public String apply(Integer key) {
        if (key > 0) {
          return key + ", " + recursiveMap.get(key - 1);
        } else {
          return "0";
        }
      }
    };

    ConcurrentMap<Integer, String> recursiveMap = new MapMaker()
        .weakKeys()
        .weakValues()
        .makeComputingMap(recursiveComputer);

    public void testRecursiveComputation() {
      assertEquals("3, 2, 1, 0", recursiveMap.get(3));
    }
  }

  /**
   * Tests for basic map functionality.
   */
  public static class ReferenceMapTest extends TestCase {

    public void testValueCleanupWithWeakKey() {
      ConcurrentMap<Object, Object> map =
          new MapMaker().weakKeys().makeMap();
      map.put(new Object(), new Object());
      assertCleanup(map);
    }

    public void testValueCleanupWithSoftKey() {
      ConcurrentMap<Object, Object> map =
          new MapMaker().softKeys().makeMap();
      map.put(new Object(), new Object());
      assertCleanup(map);
    }

    public void testKeyCleanupWithWeakValue() {
      ConcurrentMap<Object, Object> map =
          new MapMaker().weakValues().makeMap();
      map.put(new Object(), new Object());
      assertCleanup(map);
    }

    public void testKeyCleanupWithSoftValue() {
      ConcurrentMap<Object, Object> map =
          new MapMaker().softValues().makeMap();
      map.put(new Object(), new Object());
      assertCleanup(map);
    }

    public void testInternedValueCleanupWithWeakKey() {
      ConcurrentMap<Object, Object> map =
          new MapMaker().weakKeys().makeMap();
      map.put(new Integer(5), "foo");
      assertCleanup(map);
    }

    public void testInternedValueCleanupWithSoftKey() {
      ConcurrentMap<Object, Object> map =
          new MapMaker().softKeys().makeMap();
      map.put(new Integer(5), "foo");
      assertCleanup(map);
    }

    public void testInternedKeyCleanupWithWeakValue() {
      ConcurrentMap<Object, Object> map =
          new MapMaker().weakValues().makeMap();
      map.put(5, new String("foo"));
      assertCleanup(map);
    }

    public void testInternedKeyCleanupWithSoftValue() {
      ConcurrentMap<Object, Object> map =
          new MapMaker().softValues().makeMap();
      map.put(5, new String("foo"));
      assertCleanup(map);
    }

    public void testReplace() {
      ConcurrentMap<Object, Object> map =
          new MapMaker().makeMap();
      assertNull(map.replace("one", 1));
    }

    private static void assertCleanup(ConcurrentMap<?, ?> map) {
      assertEquals(1, map.size());

      // wait up to 5s
      byte[] filler = new byte[1024];
      for (int i = 0; i < 500; i++) {
        System.gc();
        if (map.isEmpty()) {
          return;
        }
        try {
          Thread.sleep(10);
        } catch (InterruptedException e) { /* ignore */ }
        try {
          // Fill up heap so soft references get cleared.
          filler = new byte[filler.length * 2];
        } catch (OutOfMemoryError e) {}
      }

      fail();
    }

    public void testWeakKeyIdentityLookup() {
      ConcurrentMap<Integer, String> map =
          new MapMaker().weakKeys().makeMap();
      Integer key1 = new Integer(12357);
      Integer key2 = new Integer(12357);
      map.put(key1, "a");
      assertTrue(map.containsKey(key1));
      assertFalse(map.containsKey(key2));
    }

    public void testSoftKeyIdentityLookup() {
      ConcurrentMap<Integer, String> map =
          new MapMaker().softKeys().makeMap();
      Integer key1 = new Integer(12357);
      Integer key2 = new Integer(12357);
      map.put(key1, "a");
      assertTrue(map.containsKey(key1));
      assertFalse(map.containsKey(key2));
    }

    public void testWeakValueIdentityLookup() {
      ConcurrentMap<String, Integer> map =
          new MapMaker().weakValues().makeMap();
      Integer value1 = new Integer(12357);
      Integer value2 = new Integer(12357);
      map.put("a", value1);
      assertTrue(map.containsValue(value1));
      assertFalse(map.containsValue(value2));
    }

    public void testSoftValueIdentityLookup() {
      ConcurrentMap<String, Integer> map =
          new MapMaker().softValues().makeMap();
      Integer value1 = new Integer(12357);
      Integer value2 = new Integer(12357);
      map.put("a", value1);
      assertTrue(map.containsValue(value1));
      assertFalse(map.containsValue(value2));
    }

    public void testWeakKeyEntrySetRemove() {
      ConcurrentMap<Integer, String> map =
          new MapMaker().weakKeys().makeMap();
      Integer key1 = new Integer(12357);
      Integer key2 = new Integer(12357);
      map.put(key1, "a");
      assertFalse(map.entrySet().remove(Maps.immutableEntry(key2, "a")));
      assertEquals(1, map.size());
      assertTrue(map.entrySet().remove(Maps.immutableEntry(key1, "a")));
      assertEquals(0, map.size());
    }

    public void testEntrySetIteratorRemove() {
      ConcurrentMap<String, Integer> map =
          new MapMaker().makeMap();
      map.put("foo", 1);
      map.put("bar", 2);
      assertEquals(2, map.size());
      Iterator<Map.Entry<String, Integer>> iterator = map.entrySet().iterator();
      try {
        iterator.remove();
        fail();
      } catch (IllegalStateException expected) {}
      iterator.next();
      iterator.remove();
      assertEquals(1, map.size());
      try {
        iterator.remove();
        fail();
      } catch (IllegalStateException expected) {}
      iterator.next();
      iterator.remove();
      assertEquals(0, map.size());
    }
  }

  /**
   * Tests for computing functionality.
   */
  public static class ComputingTest extends TestCase {

    public void testComputerThatReturnsNull() {
      ConcurrentMap<Integer, String> map = new MapMaker()
          .makeComputingMap(new Function<Integer, String>() {
            public String apply(Integer key) {
              return null;
            }
          });
      try {
        map.get(1);
        fail();
      } catch (NullPointerException e) { /* expected */ }
    }

    public void testRecomputeAfterReclamation()
        throws InterruptedException {
      ConcurrentMap<Integer, String> map = new MapMaker()
          .weakValues()
          .makeComputingMap(new Function<Integer, String>() {
            @SuppressWarnings("RedundantStringConstructorCall")
            public String apply(Integer key) {
              return new String("one");
            }
          });

      for (int i = 0; i < 10; i++) {
        // The entry should get garbage collected and recomputed.
        assertEquals("on iteration " + i, "one", map.get(1));
        Thread.sleep(i);
        System.gc();
      }
    }

    public void testRuntimeException() {
      final RuntimeException e = new RuntimeException();

      ConcurrentMap<Object, Object> map = new MapMaker().makeComputingMap(
          new Function<Object, Object>() {
        public Object apply(Object from) {
          throw e;
        }
      });

      try {
        map.get(new Object());
        fail();
      } catch (ComputationException ce) {
        assertSame(e, ce.getCause());
      }
    }

    public void testSleepConcurrency() throws InterruptedException {
      ConcurrentMap<String, Integer> cache = new MapMaker()
          .weakKeys().weakValues().makeComputingMap(new SleepFunction());
      assertConcurrency(cache, false);
    }

    public void testBusyConcurrency() throws InterruptedException {
      ConcurrentMap<String, Integer> cache = new MapMaker()
          .weakKeys().weakValues().makeComputingMap(new BusyFunction());
      assertConcurrency(cache, false);
    }

    public void testFastConcurrency() throws InterruptedException {
      ConcurrentMap<String, Integer> cache = new MapMaker()
          .weakKeys().weakValues().makeComputingMap(new SomeFunction());
      assertConcurrency(cache, false);
    }

    public void testSleepCanonical() throws InterruptedException {
      ConcurrentMap<String, Integer> cache = new MapMaker()
          .softValues().makeComputingMap(new SleepFunction());
      assertConcurrency(cache, true);
    }

    public void testBusyCanonical() throws InterruptedException {
      ConcurrentMap<String, Integer> cache = new MapMaker()
          .softValues().makeComputingMap(new BusyFunction());
      assertConcurrency(cache, true);
    }

    public void testFastCanonical() throws InterruptedException {
      ConcurrentMap<String, Integer> cache = new MapMaker()
          .softValues().makeComputingMap(new SomeFunction());
      assertConcurrency(cache, true);
    }

    private static void assertConcurrency(
        final ConcurrentMap<String, Integer> cache,
        final boolean simulateAliasing) throws InterruptedException {
      final int n = 20;
      final CountDownLatch startSignal = new CountDownLatch(1);
      final CountDownLatch doneSignal = new CountDownLatch(n);
      for (int i = 0; i < n; i++) {
        new Thread() {
          @Override public void run() {
            try {
              startSignal.await();
              for (int j = 0; j < n; j++) {
                cache.get(simulateAliasing ? new String("foo") : "foo");
              }
              doneSignal.countDown();
            } catch (InterruptedException ignored) {}
          }
        }.start();
      }

      startSignal.countDown();
      doneSignal.await();
      assertEquals(Integer.valueOf(1), cache.get("foo"));
      assertEquals(Integer.valueOf(2), cache.get("bar"));
    }

    private static class SomeFunction implements Function<String, Integer> {
      private int numApplies = 0;
      public Integer apply(String s) {
        return ++numApplies;
      }
    }

    private static class SleepFunction implements Function<String, Integer> {
      private int numApplies = 0;
      public Integer apply(String s) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
        return ++numApplies;
      }
    }

    private static class BusyFunction implements Function<String, Integer> {
      private int numApplies = 0;
      public Integer apply(String s) {
        for (int i = 0; i < 1000; i++) {
          Math.sqrt(i);
        }
        return ++numApplies;
      }
    }
  }

  /**
   * Tests combinations of key and value reference types.
   */
  public static class ReferenceCombinationTestSuite extends TestCase {

    interface BuilderOption {
      void applyTo(MapMaker maker);
    }

    public static Test suite() {
      TestSuite suite = new TestSuite();

      BuilderOption[] keyOptions = {
        new BuilderOption() {
          public void applyTo(MapMaker maker) {
            // strong keys
          }
          @Override public String toString() {
            return "Strong";
          }
        },
        new BuilderOption() {
          public void applyTo(MapMaker maker) {
            maker.weakKeys();
          }
          @Override public String toString() {
            return "Weak";
          }
        },
        new BuilderOption() {
          public void applyTo(MapMaker maker) {
            maker.softKeys();
          }
          @Override public String toString() {
            return "Soft";
          }
        },
      };

      BuilderOption[] valueOptions = {
        new BuilderOption() {
          public void applyTo(MapMaker maker) {
            // strong values
          }
          @Override public String toString() {
            return "Strong";
          }
        },
        new BuilderOption() {
          public void applyTo(MapMaker maker) {
            maker.weakValues();
          }
          @Override public String toString() {
            return "Weak";
          }
        },
        new BuilderOption() {
          public void applyTo(MapMaker maker) {
            maker.softValues();
          }
          @Override public String toString() {
            return "Soft";
          }
        },
      };

      // create test cases for each key and value type.
      for (Method method : MapTest.class.getMethods()) {
        String name = method.getName();
        if (name.startsWith("test")) {
          for (BuilderOption keyOption : keyOptions) {
            for (BuilderOption valueOption : valueOptions) {
              suite.addTest(new MapTest(name, keyOption, valueOption));
            }
          }
        }
      }

      return suite;
    }

    public static class MapTest extends TestCase {

      final BuilderOption keyOption;
      final BuilderOption valueOption;

      public MapTest(String name,
          BuilderOption keyOption,
          BuilderOption valueOption) {
        super(name);
        this.keyOption = keyOption;
        this.valueOption = valueOption;
      }

      @Override public String getName() {
        return super.getName() + "For" + keyOption + valueOption;
      }

      MapMaker newBuilder() {
        MapMaker maker = new MapMaker();
        keyOption.applyTo(maker);
        valueOption.applyTo(maker);
        return maker;
      }

      <K, V> ConcurrentMap<K, V> newMap() {
        MapMaker maker = new MapMaker();
        keyOption.applyTo(maker);
        valueOption.applyTo(maker);
        return maker.makeMap();
      }

      public void testContainsKey() {
        ConcurrentMap<Object, String> map = newMap();
        Object k = "key";
        map.put(k, "value");
        assertTrue(map.containsKey(k));
      }

      public void testClear() {
        ConcurrentMap<String, String> map = newMap();
        String k = "key";
        map.put(k, "value");
        assertFalse(map.isEmpty());
        map.clear();
        assertTrue(map.isEmpty());
        assertNull(map.get(k));
      }

      public void testKeySet() {
        ConcurrentMap<String, String> map = newMap();
        map.put("a", "foo");
        map.put("b", "foo");
        Set<String> expected = set("a", "b");
        assertEquals(expected, map.keySet());
      }

      public void testValues() {
        ConcurrentMap<String, String> map = newMap();
        map.put("a", "1");
        map.put("b", "2");
        Set<String> expected = set("1", "2");
        Set<String> actual = new HashSet<String>();
        actual.addAll(map.values());
        assertEquals(expected, actual);
      }

      public void testPutIfAbsent() {
        ConcurrentMap<String, String> map = newMap();
        map.putIfAbsent("a", "1");
        assertEquals("1", map.get("a"));
        map.putIfAbsent("a", "2");
        assertEquals("1", map.get("a"));
      }

      public void testReplace() {
        ConcurrentMap<String, String> map = newMap();
        map.put("a", "1");
        map.replace("a", "2", "2");
        assertEquals("1", map.get("a"));
        map.replace("a", "1", "2");
        assertEquals("2", map.get("a"));
      }

      public void testContainsValue() {
        ConcurrentMap<String, Object> map = newMap();
        Object v = "value";
        map.put("key", v);
        assertTrue(map.containsValue(v));
      }

      public void testEntrySet() {
        final ConcurrentMap<String, String> map = newMap();
        map.put("a", "1");
        map.put("b", "2");
        @SuppressWarnings("unchecked")
        Set<Map.Entry<String, String>> expected
            = set(Maps.immutableEntry("a", "1"), Maps.immutableEntry("b", "2"));
        assertEquals(expected, map.entrySet());
      }

      public void testPutAll() {
        ConcurrentMap<Object, Object> map = newMap();
        Object k = "key";
        Object v = "value";
        map.putAll(Collections.singletonMap(k, v));
        assertSame(v, map.get(k));
      }

      public void testRemove() {
        ConcurrentMap<Object, String> map = newMap();
        Object k = "key";
        map.put(k, "value");
        map.remove(k);
        assertFalse(map.containsKey(k));
      }

      public void testPutGet() {
        final Object k = new Object();
        final Object v = new Object();
        ConcurrentMap<Object, Object> map = newMap();
        map.put(k, v);
        assertEquals(1, map.size());
        assertSame(v, map.get(k));
        assertEquals(1, map.size());
        assertNull(map.get(new Object()));
      }

      public void testCompute() {
        final Object k = new Object();
        final Object v = new Object();
        ConcurrentMap<?, ?> map = newBuilder().makeComputingMap(
            new Function<Object, Object>() {
          public Object apply(Object key) {
            return key == k ? v : null;
          }
        });

        assertEquals(0, map.size());
        assertSame(v, map.get(k));
        assertSame(v, map.get(k));
        assertEquals(1, map.size());

        try {
          map.get(new Object());
          fail();
        } catch (NullPointerException e) { /* expected */ }
        assertEquals(1, map.size());
      }

      static class MockFunction implements Function<Object, Object>,
          Serializable {
        int count;
        public Object apply(Object key) {
          count++;
          return Value.valueOf(key.toString());
        }
        private static final long serialVersionUID = 0;
      }
    }

    /**
     * Enums conveniently maintain instance identity across serialization.
     */
    enum Key {
      FOO, BAR, TEE
    }

    enum Value {
      FOO, BAR, TEE
    }
  }

  public static class ExpiringReferenceMapTest extends TestCase {

    private static final long EXPIRING_TIME = 10;
    private static final int VALUE_PREFIX = 12345;
    private static final String KEY_PREFIX = "key prefix:";

    Timer oldTimer;
    final List<TimerTask> tasks = new ArrayList<TimerTask>();

    @Override
    protected void setUp() throws Exception {
      oldTimer = ExpirationTimer.instance;
      ExpirationTimer.instance = new Timer() {
        @Override
        public void schedule(TimerTask task, long delay) {
          tasks.add(task);
        }
      };
    }

    @Override
    protected void tearDown() throws Exception {
      ExpirationTimer.instance = oldTimer;
    }

    private void runTasks() {
      for (TimerTask task : tasks) {
        task.run();
      }
      tasks.clear();
    }

    public void testExpiringPut() {
      ConcurrentMap<String, Integer> map = new MapMaker()
          .expiration(EXPIRING_TIME, TimeUnit.MILLISECONDS).makeMap();

      for (int i = 0; i < 10; i++) {
        map.put(KEY_PREFIX + i, VALUE_PREFIX + i);
        assertEquals(Integer.valueOf(VALUE_PREFIX + i),
            map.get(KEY_PREFIX + i));
      }

      runTasks();

      assertEquals("Map must be empty by now", 0, map.size());
    }

    public void testExpiringPutIfAbsent() {
      ConcurrentMap<String, Integer> map = new MapMaker()
          .expiration(EXPIRING_TIME, TimeUnit.MILLISECONDS).makeMap();

      for (int i = 0; i < 10; i++) {
        map.putIfAbsent(KEY_PREFIX + i, VALUE_PREFIX + i);
        assertEquals(Integer.valueOf(VALUE_PREFIX + i),
            map.get(KEY_PREFIX + i));
      }

      runTasks();

      assertEquals("Map must be empty by now", 0, map.size());
    }

    public void testExpiringGetForSoft() {
      ConcurrentMap<String, Integer> map = new MapMaker()
          .softValues().expiration(EXPIRING_TIME, TimeUnit.MILLISECONDS)
          .makeMap();

      runExpirationTest(map);
    }

    public void testExpiringGetForStrong() {
      ConcurrentMap<String, Integer> map = new MapMaker()
          .expiration(EXPIRING_TIME, TimeUnit.MILLISECONDS).makeMap();

      runExpirationTest(map);
    }

    public void testRemovalSchedulerForStrong() {
      ConcurrentMap<String, Integer> map = new MapMaker()
          .expiration(EXPIRING_TIME, TimeUnit.MILLISECONDS).makeMap();

      runRemovalScheduler(map, KEY_PREFIX, EXPIRING_TIME);
    }

    public void testRemovalSchedulerForSoft() {
      ConcurrentMap<String, Integer> map = new MapMaker()
          .softValues().expiration(EXPIRING_TIME, TimeUnit.MILLISECONDS)
          .makeMap();

      runRemovalScheduler(map, KEY_PREFIX, EXPIRING_TIME);
    }

    private void runExpirationTest(ConcurrentMap<String, Integer> map) {
      for (int i = 0; i < 10; i++) {
        map.put(KEY_PREFIX + i, VALUE_PREFIX + i);
        assertEquals(Integer.valueOf(VALUE_PREFIX + i),
            map.get(KEY_PREFIX + i));
      }

      for (int i = 0; i < 10; i++) {
        assertEquals(Integer.valueOf(i + VALUE_PREFIX),
            map.get(KEY_PREFIX + i));
      }

      runTasks();

      for (int i = 0; i < 10; i++) {
        assertEquals(null, map.get(KEY_PREFIX + i));
      }
    }

    private void runRemovalScheduler(ConcurrentMap<String, Integer> map,
        String keyPrefix, long ttl) {

      int shift1 = 10 + VALUE_PREFIX;
      // fill with initial data
      for (int i = 0; i < 10; i++) {
        map.put(keyPrefix + i, i + shift1);
        assertEquals(Integer.valueOf(i + shift1), map.get(keyPrefix + i));
      }

      // wait, so that entries have just 10 ms to live
      try {
        Thread.sleep(ttl * 2 / 3);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }

      int shift2 = shift1 + 10;
      // fill with new data - has to live for 20 ms more
      for (int i = 0; i < 10; i++) {
        map.put(keyPrefix + i, i + shift2);
        assertEquals("key: " + keyPrefix + i,
            Integer.valueOf(i + shift2), map.get(keyPrefix + i));
      }

      // old timeouts must expire after this wait
      try {
        Thread.sleep(ttl * 2 / 3);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }

      // check that new values are still there - they still have 10 ms to live
      for (int i = 0; i < 10; i++) {
        assertEquals(Integer.valueOf(i + shift2), map.get(keyPrefix + i));
      }
    }
  }

  public static class ExpiringComputingReferenceMapTest extends TestCase {

    static final long VERY_LONG = 100000L;
    static final String KEY_PREFIX = "THIS IS AN ARBITRARY KEY PREFIX";
    static final int VALUE_SUFFIX = 77777;

    Timer oldTimer;
    final List<TimerTask> tasks = new ArrayList<TimerTask>();

    @Override
    protected void setUp() throws Exception {
      oldTimer = ExpirationTimer.instance;
      ExpirationTimer.instance = new Timer() {
        @Override
        public void schedule(TimerTask task, long delay) {
          tasks.add(task);
        }
      };
    }

    @Override
    protected void tearDown() throws Exception {
      ExpirationTimer.instance = oldTimer;
    }

    private void runTasks() {
      for (TimerTask task : tasks) {
        task.run();
      }
      tasks.clear();
    }

    public void testExpiringPut() {
      ConcurrentMap<String, Integer> cache = new MapMaker()
          .expiration(50, TimeUnit.MILLISECONDS)
          .makeComputingMap(WATCHED_CREATOR);

      for (int i = 0; i < 10; i++) {
        cache.put(KEY_PREFIX + i, i + VALUE_SUFFIX);
        assertEquals(Integer.valueOf(i + VALUE_SUFFIX),
            cache.get(KEY_PREFIX + i));
      }

      for (int i = 0; i < 10; i++) {
        WATCHED_CREATOR.reset();
        assertEquals(Integer.valueOf(i + VALUE_SUFFIX),
            cache.get(KEY_PREFIX + i));
        assertFalse("Creator should not have been called @#" + i,
            WATCHED_CREATOR.wasCalled());
      }

      runTasks();

      assertEquals("Cache must be empty by now", 0, cache.size());
    }

    public void testExpiringPutIfAbsent() {
      ConcurrentMap<String, Integer> cache = new MapMaker()
          .expiration(50, TimeUnit.MILLISECONDS)
          .makeComputingMap(WATCHED_CREATOR);

      for (int i = 0; i < 10; i++) {
        cache.putIfAbsent(KEY_PREFIX + i, i + VALUE_SUFFIX);
        assertEquals(Integer.valueOf(i + VALUE_SUFFIX),
            cache.get(KEY_PREFIX + i));
      }

      runTasks();

      assertEquals("Cache must be empty by now", 0, cache.size());
    }

    public void testExpiringGetForSoft() {
      ConcurrentMap<String, Integer> cache = new MapMaker()
          .expiration(5, TimeUnit.MILLISECONDS)
          .softValues().makeComputingMap(WATCHED_CREATOR);

      runExpirationTest(cache);
    }

    public void testExpiringGetForStrong() {
      ConcurrentMap<String, Integer> cache = new MapMaker()
          .expiration(10, TimeUnit.MILLISECONDS)
          .makeComputingMap(WATCHED_CREATOR);

      runExpirationTest(cache);
    }

    private void runExpirationTest(ConcurrentMap<String, Integer> cache) {
      for (int i = 0; i < 10; i++) {
        assertEquals(Integer.valueOf(i + VALUE_SUFFIX),
            cache.get(KEY_PREFIX + i));
      }

      for (int i = 0; i < 10; i++) {
        WATCHED_CREATOR.reset();
        assertEquals(Integer.valueOf(i + VALUE_SUFFIX),
            cache.get(KEY_PREFIX + i));
        assertFalse("Creator should NOT have been called @#" + i,
            WATCHED_CREATOR.wasCalled());
      }

      runTasks();

      for (int i = 0; i < 10; i++) {
        WATCHED_CREATOR.reset();
        assertEquals(Integer.valueOf(i + VALUE_SUFFIX),
            cache.get(KEY_PREFIX + i));
        assertTrue("Creator should have been called @#" + i,
            WATCHED_CREATOR.wasCalled());
      }
    }

    public void testRemovalSchedulerForStrong() {
      String keyPrefix = "TRSTRONG_";
      int ttl = 300;
      ConcurrentMap<String, Integer> cache = new MapMaker()
          .expiration(ttl, TimeUnit.MILLISECONDS)
          .makeComputingMap(new WatchedCreatorFunction(keyPrefix));
      runRemovalScheduler(cache, keyPrefix, ttl);
    }

    public void testRemovalSchedulerForSoft() {
      String keyPrefix = "TRSOFT_";
      int ttl = 300;
      ConcurrentMap<String, Integer> cache = new MapMaker()
          .expiration(ttl, TimeUnit.MILLISECONDS).softValues()
          .makeComputingMap(new WatchedCreatorFunction(keyPrefix));
      runRemovalScheduler(cache, keyPrefix, ttl);
    }

    private void runRemovalScheduler(ConcurrentMap<String, Integer> cache,
        String keyPrefix, int ttl) {
      int shift1 = 10 + VALUE_SUFFIX;
      // fill with initial data
      for (int i = 0; i < 10; i++) {
        cache.put(keyPrefix + i, i + shift1);
        assertEquals(Integer.valueOf(i + shift1), cache.get(keyPrefix + i));
      }

      // wait, so that entries have just 10 ms to live
      try {
        Thread.sleep(ttl * 2 / 3);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }

      int shift2 = shift1 + 10;
      // fill with new data - has to live for 20 ms more
      for (int i = 0; i < 10; i++) {
        cache.put(keyPrefix + i, i + shift2);
        assertEquals("key: " + keyPrefix + i,
            Integer.valueOf(i + shift2), cache.get(keyPrefix + i));
      }

      // old timeouts must expire after this wait
      try {
        Thread.sleep(ttl * 2 / 3);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }

      // check that new values are still there - they still have 10 ms to live
      for (int i = 0; i < 10; i++) {
        assertEquals(Integer.valueOf(i + shift2), cache.get(keyPrefix + i));
      }
    }

    private static class WatchedCreatorFunction
        implements Function<String, Integer> {
      boolean wasCalled = false; // must be set in apply()

      public WatchedCreatorFunction() {
        this(KEY_PREFIX);
      }

      public WatchedCreatorFunction(String prefix) {
        setPrefix(prefix);
      }

      public void reset() {
        wasCalled = false;
      }

      protected String prefix = KEY_PREFIX;

      public void setPrefix(String prefix) {
        this.prefix = prefix;
      }

      public boolean wasCalled() {
        return wasCalled;
      }

      public Integer apply(String s) {
        wasCalled = true;
        return Integer.parseInt(s.substring(prefix.length())) + VALUE_SUFFIX;
      }
    }

    static final WatchedCreatorFunction WATCHED_CREATOR =
        new WatchedCreatorFunction();
  }

  static <E> Set<E> set(E... elements) {
    return new HashSet<E>(Arrays.asList(elements));
  }
}
