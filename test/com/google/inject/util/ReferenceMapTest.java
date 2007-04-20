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

package com.google.inject.util;

import static com.google.inject.util.ReferenceType.SOFT;
import static com.google.inject.util.ReferenceType.STRONG;
import static com.google.inject.util.ReferenceType.WEAK;
import java.lang.ref.Reference;
import java.util.Iterator;
import java.util.concurrent.ConcurrentMap;
import junit.framework.TestCase;

/**
 * Tests for {@link ReferenceMap}.
 *
 * @author crazybob@google.com (Bob Lee)
 * @author mbostock@google.com (Mike Bostock)
 */
@SuppressWarnings({"unchecked"})
public class ReferenceMapTest extends TestCase {

  private enum CleanupMode {
    ENQUEUE_KEY, ENQUEUE_VALUE, GC;
  }

  public void testValueCleanupWithWeakKey() {
    ReferenceMap map = new ReferenceMap(WEAK, STRONG);
    map.put(new Object(), new Object());
    assertCleanup(map, CleanupMode.GC);
  }

  public void testKeyCleanupWithWeakValue() {
    ReferenceMap map = new ReferenceMap(STRONG, WEAK);
    map.put(new Object(), new Object());
    assertCleanup(map, CleanupMode.GC);
  }

  public void testInternedValueCleanupWithWeakKey() {
    ReferenceMap map = new ReferenceMap(WEAK, STRONG);
    map.put(5, "foo");
    assertCleanup(map, CleanupMode.ENQUEUE_KEY);
  }

  public void testInternedValueCleanupWithSoftKey() {
    ReferenceMap map = new ReferenceMap(SOFT, STRONG);
    map.put(5, "foo");
    assertCleanup(map, CleanupMode.ENQUEUE_KEY);
  }

  public void testInternedKeyCleanupWithWeakValue() {
    ReferenceMap map = new ReferenceMap(STRONG, WEAK);
    map.put(5, "foo");
    assertCleanup(map, CleanupMode.ENQUEUE_VALUE);
  }

  public void testInternedKeyCleanupWithSoftValue() {
    ReferenceMap map = new ReferenceMap(STRONG, SOFT);
    map.put(5, "foo");
    assertCleanup(map, CleanupMode.ENQUEUE_VALUE);
  }

  private static void assertCleanup(ReferenceMap<?, ?> map,
      CleanupMode mode) {
    assertEquals(1, map.delegate.size());

    switch (mode) {
      case ENQUEUE_KEY: {
        ConcurrentMap delegate = map.delegate;
        Iterator keyIterator = delegate.keySet().iterator();
        Reference reference = ((Reference) keyIterator.next());
        reference.enqueue();
        break;
      }
      case ENQUEUE_VALUE: {
        ConcurrentMap delegate = map.delegate;
        Iterator valueIterator = delegate.values().iterator();
        Reference reference = ((Reference) valueIterator.next());
        reference.enqueue();
        break;
      }
    }

    // wait up to 5s
    for (int i = 0; i < 500; i++) {
      if (mode == CleanupMode.GC) {
        System.gc();
      }
      if (map.size() == 0) {
        return;
      }
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) { /* ignore */ }
    }
    fail();
  }
}
