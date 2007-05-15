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

import static com.google.inject.internal.ReferenceType.SOFT;
import static com.google.inject.internal.ReferenceType.STRONG;
import static com.google.inject.internal.ReferenceType.WEAK;
import java.util.concurrent.CountDownLatch;
import junit.framework.TestCase;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class ReferenceCacheTest extends TestCase {

  public void testRuntimeException() {
    class CreationException extends RuntimeException {}
    try {
      new ReferenceCache() {
        protected Object create(Object key) {
          throw new CreationException();
        }
      }.get(new Object());
      fail();
    } catch (CreationException e) { /* expected */ }
  }

  public void testApply() {
    ReferenceMap<String, Integer> cache = ReferenceCache.of(
        WEAK, WEAK, new SomeFunction());
    assertEquals(Integer.valueOf(1), cache.get("foo"));
    assertEquals(Integer.valueOf(1), cache.get("foo"));
    assertEquals(Integer.valueOf(2), cache.get("bar"));
    assertEquals(Integer.valueOf(1), cache.get("foo"));
    assertEquals(Integer.valueOf(3), cache.get("baz"));
  }

  public void testSleepConcurrency() throws InterruptedException {
    ReferenceMap<String, Integer> cache = ReferenceCache.of(
        WEAK, WEAK, new SleepFunction());
    assertConcurrency(cache, false);
  }

  public void testBusyConcurrency() throws InterruptedException {
    ReferenceMap<String, Integer> cache = ReferenceCache.of(
        WEAK, WEAK, new BusyFunction());
    assertConcurrency(cache, false);
  }

  public void testFastConcurrency() throws InterruptedException {
    ReferenceMap<String, Integer> cache = ReferenceCache.of(
        WEAK, WEAK, new SomeFunction());
    assertConcurrency(cache, false);
  }

  public void testSleepCanonical() throws InterruptedException {
    ReferenceMap<String, Integer> cache = ReferenceCache.of(
        STRONG, SOFT, new SleepFunction());
    assertConcurrency(cache, true);
  }

  public void testBusyCanonical() throws InterruptedException {
    ReferenceMap<String, Integer> cache = ReferenceCache.of(
        STRONG, SOFT, new BusyFunction());
    assertConcurrency(cache, true);
  }

  public void testFastCanonical() throws InterruptedException {
    ReferenceMap<String, Integer> cache = ReferenceCache.of(
        STRONG, SOFT, new SomeFunction());
    assertConcurrency(cache, true);
  }

  private static void assertConcurrency(
      final ReferenceMap<String, Integer> cache,
      final boolean simulateAliasing) throws InterruptedException {
    final int n = 20;
    final CountDownLatch startSignal = new CountDownLatch(1);
    final CountDownLatch doneSignal = new CountDownLatch(n);
    for (int i = 0; i < n; i++) {
      new Thread() {
        public void run() {
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
