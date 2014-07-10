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

package com.google.inject.multibindings;

import static junit.framework.Assert.assertSame;

import com.google.common.testing.GcFinalization;
import com.google.inject.Injector;
import com.google.inject.Key;

import junit.framework.Assert;
import junit.framework.TestCase;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Utilities for verifying com.google.inject.internal.WeakKeySet is not leaking memory.
 * 
 * @author dweis@google.com (Daniel Weis)
 */
final class WeakKeySetUtils {
  
  private WeakKeySetUtils() {}
  
  static void awaitFullGc() {
    // GcFinalization *should* do it, but doesn't work well in practice...
    // so we put a second latch and wait for a ReferenceQueue to tell us.
    ReferenceQueue<Object> queue = new ReferenceQueue<Object>();
    WeakReference ref = new WeakReference<Object>(new Object(), queue);
    GcFinalization.awaitFullGc();
    try {
      assertSame("queue didn't return ref in time", ref, queue.remove(5000));
    } catch (IllegalArgumentException e) {
      throw new RuntimeException(e);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
  
  static void awaitClear(WeakReference<?> ref) {
    // GcFinalization *should* do it, but doesn't work well in practice...
    // so we put a second latch and wait for a ReferenceQueue to tell us.
    Object data = ref.get();
    ReferenceQueue<Object> queue = null;
    WeakReference extraRef = null;
    if (data != null) {
      queue = new ReferenceQueue<Object>();
      extraRef = new WeakReference<Object>(data, queue);
      data = null;
    }
    GcFinalization.awaitClear(ref);
    if (queue != null) {
      try {
        assertSame("queue didn't return ref in time", extraRef, queue.remove(5000));
      } catch (IllegalArgumentException e) {
        throw new RuntimeException(e);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }
  
  static void assertBlacklisted(Injector injector, Key<?> key) {
    assertBlacklistState(injector, key, true);
  }
 
  static void assertNotBlacklisted(Injector injector, Key<?> key) {
    assertBlacklistState(injector, key, false);
  }

  private static final Field stateField;
  private static final Method isBlacklistedMethod; 
  static {
    try {
      stateField =
          Class.forName("com.google.inject.internal.InjectorImpl").getDeclaredField("state");
      stateField.setAccessible(true);
      isBlacklistedMethod =
          Class.forName("com.google.inject.internal.State").getMethod("isBlacklisted", Key.class);
      isBlacklistedMethod.setAccessible(true);
    } catch (Exception e) {
      throw new Error(e);
    }
  }

  private static void assertBlacklistState(Injector injector, Key<?> key, boolean isBlacklisted) {
    try {
      TestCase.assertEquals(
          isBlacklisted,
          ((Boolean) isBlacklistedMethod.invoke(stateField.get(injector), key)).booleanValue());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
