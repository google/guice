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

import com.google.inject.Injector;
import com.google.inject.Key;

import junit.framework.TestCase;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Utilities for verifying com.google.inject.internal.WeakKeySet is not leaking memory.
 * 
 * @author dweis@google.com (Daniel Weis)
 */
final class WeakKeySetUtils {
  
  private WeakKeySetUtils() {}
  
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
