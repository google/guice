/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 * Other contributors include Andrew Wright, Jeffrey Hayes,
 * Pat Fisher, Mike Judd.
 */

package com.google.inject.internal.util;

import com.google.inject.internal.util.Jsr166HashMap;
import junit.framework.TestCase;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * ConcurrentHashMap tests copied from ConcurrentHashMapTest. Useful as a
 * test case for CustomConcurrentHashMap.
 */
public class Jsr166HashMapTest extends TestCase {

    /*
     * The following two methods and constants were copied from JSR166TestCase.
     */

    /**
     * fail with message "should throw exception"
     */
    public void shouldThrow() {
        fail("Should throw exception");
    }

    /**
     * fail with message "Unexpected exception"
     */
    public void unexpectedException() {
        fail("Unexpected exception");
    }

    static final Integer zero = new Integer(0);
    static final Integer one = new Integer(1);
    static final Integer two = new Integer(2);
    static final Integer three = new Integer(3);
    static final Integer four = new Integer(4);
    static final Integer five = new Integer(5);
    static final Integer six = new Integer(6);
    static final Integer seven = new Integer(7);
    static final Integer eight = new Integer(8);
    static final Integer nine = new Integer(9);
    static final Integer m1 = new Integer(-1);
    static final Integer m2 = new Integer(-2);
    static final Integer m3 = new Integer(-3);
    static final Integer m4 = new Integer(-4);
    static final Integer m5 = new Integer(-5);
    static final Integer m6 = new Integer(-6);
    static final Integer m10 = new Integer(-10);

    /**
     * Create a map from Integers 1-5 to Strings "A"-"E".
     */
    private static Jsr166HashMap map5() {
        Jsr166HashMap map = new Jsr166HashMap(5);
        assertTrue(map.isEmpty());
        map.put(one, "A");
        map.put(two, "B");
        map.put(three, "C");
        map.put(four, "D");
        map.put(five, "E");
        assertFalse(map.isEmpty());
        assertEquals(5, map.size());
        return map;
    }

    /**
     * clear removes all pairs
     */
    public void testClear() {
        Jsr166HashMap map = map5();
        map.clear();
        assertEquals(map.size(), 0);
    }

    /**
     * Maps with same contents are equal
     */
    public void testEquals() {
        Jsr166HashMap map1 = map5();
        Jsr166HashMap map2 = map5();
        assertEquals(map1, map2);
        assertEquals(map2, map1);
        map1.clear();
        assertFalse(map1.equals(map2));
        assertFalse(map2.equals(map1));
    }

    /**
     * containsKey returns true for contained key
     */
    public void testContainsKey() {
        Jsr166HashMap map = map5();
        assertTrue(map.containsKey(one));
        assertFalse(map.containsKey(zero));
    }

    /**
     * containsValue returns true for held values
     */
    public void testContainsValue() {
        Jsr166HashMap map = map5();
        assertTrue(map.containsValue("A"));
        assertFalse(map.containsValue("Z"));
    }

    /**
     * get returns the correct element at the given key, or null if not present
     */
    public void testGet() {
        Jsr166HashMap map = map5();
        assertEquals("A", (String) map.get(one));
        Jsr166HashMap empty = new Jsr166HashMap();
        assertNull(map.get("anything"));
    }

    /**
     * isEmpty is true of empty map and false for non-empty
     */
    public void testIsEmpty() {
        Jsr166HashMap empty = new Jsr166HashMap();
        Jsr166HashMap map = map5();
        assertTrue(empty.isEmpty());
        assertFalse(map.isEmpty());
    }

    /**
     * keySet returns a Set containing all the keys
     */
    public void testKeySet() {
        Jsr166HashMap map = map5();
        Set s = map.keySet();
        assertEquals(5, s.size());
        assertTrue(s.contains(one));
        assertTrue(s.contains(two));
        assertTrue(s.contains(three));
        assertTrue(s.contains(four));
        assertTrue(s.contains(five));
    }

    /**
     * keySet.toArray returns contains all keys
     */
    public void testKeySetToArray() {
        Jsr166HashMap map = map5();
        Set s = map.keySet();
        Object[] ar = s.toArray();
        assertTrue(s.containsAll(Arrays.asList(ar)));
        assertEquals(5, ar.length);
        ar[0] = m10;
        assertFalse(s.containsAll(Arrays.asList(ar)));
    }

    /**
     * Values.toArray contains all values
     */
    public void testValuesToArray() {
        Jsr166HashMap map = map5();
        Collection v = map.values();
        Object[] ar = v.toArray();
        ArrayList s = new ArrayList(Arrays.asList(ar));
        assertEquals(5, ar.length);
        assertTrue(s.contains("A"));
        assertTrue(s.contains("B"));
        assertTrue(s.contains("C"));
        assertTrue(s.contains("D"));
        assertTrue(s.contains("E"));
    }

    /**
     * entrySet.toArray contains all entries
     */
    public void testEntrySetToArray() {
        Jsr166HashMap map = map5();
        Set s = map.entrySet();
        Object[] ar = s.toArray();
        assertEquals(5, ar.length);
        for (int i = 0; i < 5; ++i) {
            assertTrue(map.containsKey(((Map.Entry) (ar[i])).getKey()));
            assertTrue(map.containsValue(((Map.Entry) (ar[i])).getValue()));
        }
    }

    /**
     * values collection contains all values
     */
    public void testValues() {
        Jsr166HashMap map = map5();
        Collection s = map.values();
        assertEquals(5, s.size());
        assertTrue(s.contains("A"));
        assertTrue(s.contains("B"));
        assertTrue(s.contains("C"));
        assertTrue(s.contains("D"));
        assertTrue(s.contains("E"));
    }

    /**
     * entrySet contains all pairs
     */
    public void testEntrySet() {
        Jsr166HashMap map = map5();
        Set s = map.entrySet();
        assertEquals(5, s.size());
        Iterator it = s.iterator();
        while (it.hasNext()) {
            Map.Entry e = (Map.Entry) it.next();
            assertTrue(
                    (e.getKey().equals(one) && e.getValue().equals("A")) ||
                            (e.getKey().equals(two) && e.getValue().equals("B"))
                            ||
                            (e.getKey().equals(three) && e.getValue()
                                    .equals("C")) ||
                            (e.getKey().equals(four) && e.getValue()
                                    .equals("D")) ||
                            (e.getKey().equals(five) && e.getValue()
                                    .equals("E")));
        }
    }

    /**
     * putAll  adds all key-value pairs from the given map
     */
    public void testPutAll() {
        Jsr166HashMap empty = new Jsr166HashMap();
        Jsr166HashMap map = map5();
        empty.putAll(map);
        assertEquals(5, empty.size());
        assertTrue(empty.containsKey(one));
        assertTrue(empty.containsKey(two));
        assertTrue(empty.containsKey(three));
        assertTrue(empty.containsKey(four));
        assertTrue(empty.containsKey(five));
    }

    /**
     * putIfAbsent works when the given key is not present
     */
    public void testPutIfAbsent() {
        Jsr166HashMap map = map5();
        map.putIfAbsent(six, "Z");
        assertTrue(map.containsKey(six));
    }

    /**
     * putIfAbsent does not add the pair if the key is already present
     */
    public void testPutIfAbsent2() {
        Jsr166HashMap map = map5();
        assertEquals("A", map.putIfAbsent(one, "Z"));
    }

    /**
     * replace fails when the given key is not present
     */
    public void testReplace() {
        Jsr166HashMap map = map5();
        assertNull(map.replace(six, "Z"));
        assertFalse(map.containsKey(six));
    }

    /**
     * replace succeeds if the key is already present
     */
    public void testReplace2() {
        Jsr166HashMap map = map5();
        assertNotNull(map.replace(one, "Z"));
        assertEquals("Z", map.get(one));
    }


    /**
     * replace value fails when the given key not mapped to expected value
     */
    public void testReplaceValue() {
        Jsr166HashMap map = map5();
        assertEquals("A", map.get(one));
        assertFalse(map.replace(one, "Z", "Z"));
        assertEquals("A", map.get(one));
    }

    /**
     * replace value succeeds when the given key mapped to expected value
     */
    public void testReplaceValue2() {
        Jsr166HashMap map = map5();
        assertEquals("A", map.get(one));
        assertTrue(map.replace(one, "A", "Z"));
        assertEquals("Z", map.get(one));
    }


    /**
     * remove removes the correct key-value pair from the map
     */
    public void testRemove() {
        Jsr166HashMap map = map5();
        map.remove(five);
        assertEquals(4, map.size());
        assertFalse(map.containsKey(five));
    }

    /**
     * remove(key,value) removes only if pair present
     */
    public void testRemove2() {
        Jsr166HashMap map = map5();
        map.remove(five, "E");
        assertEquals(4, map.size());
        assertFalse(map.containsKey(five));
        map.remove(four, "A");
        assertEquals(4, map.size());
        assertTrue(map.containsKey(four));

    }

    /**
     * size returns the correct values
     */
    public void testSize() {
        Jsr166HashMap map = map5();
        Jsr166HashMap empty = new Jsr166HashMap();
        assertEquals(0, empty.size());
        assertEquals(5, map.size());
    }

    /**
     * toString contains toString of elements
     */
    public void testToString() {
        Jsr166HashMap map = map5();
        String s = map.toString();
        for (int i = 1; i <= 5; ++i) {
            assertTrue(s.indexOf(String.valueOf(i)) >= 0);
        }
    }

    // Exception tests

    /**
     * Cannot create with negative capacity
     */
    public void testConstructor1() {
        try {
            new Jsr166HashMap(-1, 0, 1);
            shouldThrow();
        } catch (IllegalArgumentException e) {
        }
    }

    /**
     * Cannot create with negative concurrency level
     */
    public void testConstructor2() {
        try {
            new Jsr166HashMap(1, 0, -1);
            shouldThrow();
        } catch (IllegalArgumentException e) {
        }
    }

    /**
     * Cannot create with only negative capacity
     */
    public void testConstructor3() {
        try {
            new Jsr166HashMap(-1);
            shouldThrow();
        } catch (IllegalArgumentException e) {
        }
    }

    /**
     * get(null) throws NPE
     */
    public void testGet_NullPointerException() {
        try {
            Jsr166HashMap c = new Jsr166HashMap(5);
            c.get(null);
            shouldThrow();
        } catch (NullPointerException e) {
        }
    }

    /**
     * containsKey(null) throws NPE
     */
    public void testContainsKey_NullPointerException() {
        try {
            Jsr166HashMap c = new Jsr166HashMap(5);
            c.containsKey(null);
            shouldThrow();
        } catch (NullPointerException e) {
        }
    }

    /**
     * containsValue(null) throws NPE
     */
    public void testContainsValue_NullPointerException() {
        try {
            Jsr166HashMap c = new Jsr166HashMap(5);
            c.containsValue(null);
            shouldThrow();
        } catch (NullPointerException e) {
        }
    }

    /**
     * put(null,x) throws NPE
     */
    public void testPut1_NullPointerException() {
        try {
            Jsr166HashMap c = new Jsr166HashMap(5);
            c.put(null, "whatever");
            shouldThrow();
        } catch (NullPointerException e) {
        }
    }

    /**
     * put(x, null) throws NPE
     */
    public void testPut2_NullPointerException() {
        try {
            Jsr166HashMap c = new Jsr166HashMap(5);
            c.put("whatever", null);
            shouldThrow();
        } catch (NullPointerException e) {
        }
    }

    /**
     * putIfAbsent(null, x) throws NPE
     */
    public void testPutIfAbsent1_NullPointerException() {
        try {
            Jsr166HashMap c = new Jsr166HashMap(5);
            c.putIfAbsent(null, "whatever");
            shouldThrow();
        } catch (NullPointerException e) {
        }
    }

    /**
     * replace(null, x) throws NPE
     */
    public void testReplace_NullPointerException() {
        try {
            Jsr166HashMap c = new Jsr166HashMap(5);
            c.replace(null, "whatever");
            shouldThrow();
        } catch (NullPointerException e) {
        }
    }

    /**
     * replace(null, x, y) throws NPE
     */
    public void testReplaceValue_NullPointerException() {
        try {
            Jsr166HashMap c = new Jsr166HashMap(5);
            c.replace(null, one, "whatever");
            shouldThrow();
        } catch (NullPointerException e) {
        }
    }

    /**
     * putIfAbsent(x, null) throws NPE
     */
    public void testPutIfAbsent2_NullPointerException() {
        try {
            Jsr166HashMap c = new Jsr166HashMap(5);
            c.putIfAbsent("whatever", null);
            shouldThrow();
        } catch (NullPointerException e) {
        }
    }


    /**
     * replace(x, null) throws NPE
     */
    public void testReplace2_NullPointerException() {
        try {
            Jsr166HashMap c = new Jsr166HashMap(5);
            c.replace("whatever", null);
            shouldThrow();
        } catch (NullPointerException e) {
        }
    }

    /**
     * replace(x, null, y) throws NPE
     */
    public void testReplaceValue2_NullPointerException() {
        try {
            Jsr166HashMap c = new Jsr166HashMap(5);
            c.replace("whatever", null, "A");
            shouldThrow();
        } catch (NullPointerException e) {
        }
    }

    /**
     * replace(x, y, null) throws NPE
     */
    public void testReplaceValue3_NullPointerException() {
        try {
            Jsr166HashMap c = new Jsr166HashMap(5);
            c.replace("whatever", one, null);
            shouldThrow();
        } catch (NullPointerException e) {
        }
    }


    /**
     * remove(null) throws NPE
     */
    public void testRemove1_NullPointerException() {
        try {
            Jsr166HashMap c = new Jsr166HashMap(5);
            c.put("sadsdf", "asdads");
            c.remove(null);
            shouldThrow();
        } catch (NullPointerException e) {
        }
    }

    /**
     * remove(null, x) throws NPE
     */
    public void testRemove2_NullPointerException() {
        try {
            Jsr166HashMap c = new Jsr166HashMap(5);
            c.put("sadsdf", "asdads");
            c.remove(null, "whatever");
            shouldThrow();
        } catch (NullPointerException e) {
        }
    }

    /**
     * remove(x, null) returns false
     */
    public void testRemove3() {
        try {
            Jsr166HashMap c = new Jsr166HashMap(5);
            c.put("sadsdf", "asdads");
            assertFalse(c.remove("sadsdf", null));
        } catch (NullPointerException e) {
            fail();
        }
    }

    /**
     * A deserialized map equals original
     */
    public void testSerialization() {
        Jsr166HashMap q = map5();

        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream(10000);
            ObjectOutputStream out =
                    new ObjectOutputStream(new BufferedOutputStream(bout));
            out.writeObject(q);
            out.close();

            ByteArrayInputStream bin =
                    new ByteArrayInputStream(bout.toByteArray());
            ObjectInputStream in =
                    new ObjectInputStream(new BufferedInputStream(bin));
            Jsr166HashMap r = (Jsr166HashMap) in.readObject();
            assertEquals(q.size(), r.size());
            assertTrue(q.equals(r));
            assertTrue(r.equals(q));
        } catch (Exception e) {
            e.printStackTrace();
            unexpectedException();
        }
    }


    /**
     * SetValue of an EntrySet entry sets value in the map.
     */
    public void testSetValueWriteThrough() {
        // Adapted from a bug report by Eric Zoerner
        Jsr166HashMap map = new Jsr166HashMap(2, 5.0f, 1);
        assertTrue(map.isEmpty());
        for (int i = 0; i < 20; i++) {
            map.put(new Integer(i), new Integer(i));
        }
        assertFalse(map.isEmpty());
        Map.Entry entry1 = (Map.Entry) map.entrySet().iterator().next();

        // assert that entry1 is not 16
        assertTrue("entry is 16, test not valid",
                !entry1.getKey().equals(new Integer(16)));

        // remove 16 (a different key) from map
        // which just happens to cause entry1 to be cloned in map
        map.remove(new Integer(16));
        entry1.setValue("XYZ");
        assertTrue(map.containsValue("XYZ")); // fails
    }

}
