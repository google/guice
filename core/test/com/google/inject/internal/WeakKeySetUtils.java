/*
 * Copyright (C) 2014 Google Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.inject.internal;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

import com.google.inject.Injector;
import com.google.inject.Key;
import java.util.Set;

/**
 * Utilities for verifying com.google.inject.internal.WeakKeySet is not leaking memory.
 *
 * @author dweis@google.com (Daniel Weis)
 */
public final class WeakKeySetUtils {

  private WeakKeySetUtils() {}

  public static void assertBlacklisted(Injector injector, Key<?> key) {
    assertBlacklistState(injector, key, true);
  }

  public static void assertNotBlacklisted(Injector injector, Key<?> key) {
    assertBlacklistState(injector, key, false);
  }

  public static void assertNotInSet(WeakKeySet set, Key<?> key) {
    // if we're expecting it to not be in the set, loop around and wait for threads to run.
    for (int i = 0; i < 10; i++) {
      if (!set.contains(key)) {
        break;
      }
      sleep();
    }
    assertFalse(set.contains(key));
    assertNull(set.getSources(Key.get(Integer.class)));
  }

  public static void assertInSet(
      WeakKeySet set, Key<?> key, int expectedSources, Object... sources) {
    assertTrue(set.contains(key));
    assertEquals(expectedSources, set.getSources(key).size());
    for (Object source : sources) {
      assertTrue("didn't contain source: " + source, set.getSources(key).contains(source));
    }
  }

  public static void assertSourceNotInSet(WeakKeySet set, Key<?> key, Object source) {
    // if we're expecting it to not be a source, loop around and wait for threads to run.
    for (int i = 0; i < 10; i++) {
      Set<Object> sources = set.getSources(key);
      assertNotNull("expected at least one source", source);
      if (!sources.contains(source)) {
        break;
      }
      sleep();
    }
    Set<Object> sources = set.getSources(key);
    assertNotNull("expected at least one source", source);
    assertFalse(sources.contains(source));
  }

  private static void assertBlacklistState(Injector injector, Key<?> key, boolean isBlacklisted) {
    // if we're expecting it to not be blacklisted, loop around and wait for threads to run.
    if (!isBlacklisted) {
      for (int i = 0; i < 10; i++) {
        if (!((InjectorImpl) injector).getJitBindingData().isBannedKey(key)) {
          break;
        }
        sleep();
      }
    }
    assertEquals(isBlacklisted, ((InjectorImpl) injector).getJitBindingData().isBannedKey(key));
  }

  private static void sleep() {
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    // TODO(b/160912368): fix the ThreadPriorityCheck errorprone warning on this line.
    Thread.yield();
  }
}
