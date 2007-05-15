/**
 * Copyright (C) 2006 Google Inc.
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * @author crazybob@google.com (Bob Lee)
 */
@SuppressWarnings({"unchecked"})
public class ReferenceMapTestSuite {

  public static Test suite() {
    TestSuite suite = new TestSuite();

    Set<ReferenceType> referenceTypes =
        new HashSet<ReferenceType>();
    referenceTypes.addAll(Arrays.asList(ReferenceType.values()));
    referenceTypes.remove(ReferenceType.PHANTOM);

    // create test cases for each key and value type.
    for (Method method : MapTest.class.getMethods()) {
      String name = method.getName();
      if (name.startsWith("test")) {
        for (ReferenceType keyType : referenceTypes) {
          for (ReferenceType valueType : referenceTypes) {
            suite.addTest(new MapTest(name, keyType, valueType));
          }
        }
      }
    }

    return suite;
  }

  public static class MapTest extends TestCase {

    final ReferenceType keyType;
    final ReferenceType valueType;

    public MapTest(String name, ReferenceType keyType,
        ReferenceType valueType) {
      super(name);
      this.keyType = keyType;
      this.valueType = valueType;
    }

    public String getName() {
      return super.getName()
          + "For"
          + Strings.capitalize(keyType.toString().toLowerCase())
          + Strings.capitalize(valueType.toString().toLowerCase());
    }

    ReferenceMap newInstance() {
      return new ReferenceMap(keyType, valueType);
    }

    public void testContainsKey() {
      ReferenceMap map = newInstance();
      Object k = "key";
      map.put(k, "value");
      assertTrue(map.containsKey(k));
    }

    public void testClear() {
      ReferenceMap map = newInstance();
      String k = "key";
      map.put(k, "value");
      assertFalse(map.isEmpty());
      map.clear();
      assertTrue(map.isEmpty());
      assertNull(map.get(k));
    }

    public void testKeySet() {
      ReferenceMap map = newInstance();
      map.put("a", "foo");
      map.put("b", "foo");
      Set expected = new HashSet(Arrays.asList("a", "b"));
      assertEquals(expected, map.keySet());
    }

    public void testValues() {
      ReferenceMap map = newInstance();
      map.put("a", "1");
      map.put("b", "2");
      Set expected = new HashSet(Arrays.asList("1", "2"));
      Set actual = new HashSet();
      actual.addAll(map.values());
      assertEquals(expected, actual);
    }

    public void testPutIfAbsent() {
      ReferenceMap map = newInstance();
      map.putIfAbsent("a", "1");
      assertEquals("1", map.get("a"));
      map.putIfAbsent("a", "2");
      assertEquals("1", map.get("a"));
    }

    public void testReplace() {
      ReferenceMap map = newInstance();
      map.put("a", "1");
      map.replace("a", "2", "2");
      assertEquals("1", map.get("a"));
      map.replace("a", "1", "2");
      assertEquals("2", map.get("a"));
    }

    public void testContainsValue() {
      ReferenceMap map = newInstance();
      Object v = "value";
      map.put("key", v);
      assertTrue(map.containsValue(v));
    }

    public void testEntrySet() {
      final ReferenceMap map = newInstance();
      map.put("a", "1");
      map.put("b", "2");
      Set expected = new HashSet(Arrays.asList(
        map.new Entry("a", "1"),
        map.new Entry("b", "2")
      ));
      assertEquals(expected, map.entrySet());
    }

    public void testPutAll() {
      ReferenceMap map = newInstance();
      Object k = "key";
      Object v = "value";
      map.putAll(Collections.singletonMap(k, v));
      assertSame(v, map.get(k));
    }

    public void testRemove() {
      ReferenceMap map = newInstance();
      Object k = "key";
      map.put(k, "value");
      map.remove(k);
      assertFalse(map.containsKey(k));
    }

    public void testPutGet() {
      final Object k = new Object();
      final Object v = new Object();
      ReferenceMap map = newInstance();
      map.put(k, v);
      assertEquals(1, map.size());
      assertSame(v, map.get(k));
      assertEquals(1, map.size());
      assertNull(map.get(new Object()));
    }

    public void testCreate() {
      final Object k = new Object();
      final Object v = new Object();
      ReferenceMap map = new ReferenceCache(
          keyType, valueType) {
        @Override protected Object create(Object key) {
          return key == k ? v : null;
        }
      };
      assertEquals(0, map.size());
      assertSame(v, map.get(k));
      assertSame(v, map.get(k));
      assertEquals(1, map.size());

      try {
        // create can't return null.
        map.get(new Object());
        fail();
      } catch (NullPointerException e) {}
    }

    public void testReferenceMapSerialization() throws IOException,
        ClassNotFoundException {
      Map map = newInstance();
      map.put(Key.FOO, Value.FOO);
      map = (Map) serializeAndDeserialize(map);
      map.put(Key.BAR, Value.BAR);
      assertSame(Value.FOO, map.get(Key.FOO));
      assertSame(Value.BAR, map.get(Key.BAR));
      assertNull(map.get(Key.TEE));
    }

    static class MockReferenceCache extends ReferenceCache {

      int count;

      public MockReferenceCache(ReferenceType keyReferenceType,
          ReferenceType valueReferenceType) {
        super(keyReferenceType, valueReferenceType);
      }

      protected Object create(Object key) {
        count++;
        return Value.valueOf(((Key) key).toString());
      }
    }

    public void testReferenceCacheSerialization() throws IOException,
        ClassNotFoundException {
      MockReferenceCache map = new MockReferenceCache(keyType, valueType);
      assertSame(Value.FOO, map.get(Key.FOO));
      assertSame(Value.BAR, map.get(Key.BAR));
      map = (MockReferenceCache) serializeAndDeserialize(map);
      assertSame(Value.FOO, map.get(Key.FOO));
      assertSame(Value.BAR, map.get(Key.BAR));
      assertSame(Value.TEE, map.get(Key.TEE));
      assertEquals(3, map.count);
    }

    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    public Object serializeAndDeserialize(Object o) throws IOException,
        ClassNotFoundException {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      new ObjectOutputStream(out).writeObject(o);
      return new ObjectInputStream(new ByteArrayInputStream(out.toByteArray()))
          .readObject();
    }
  }

  /**
   * Enums conveniently maintain instance identity across serialization.
   */
  enum Key {
    FOO, BAR, TEE;
  }

  enum Value {
    FOO, BAR, TEE;
  }
}