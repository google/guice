/*
 * Copyright (C) 2025 Google Inc.
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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.util.concurrent.Uninterruptibles;
import com.google.inject.spi.Dependency;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class InternalFactoryTest {

  @Test
  public void testMethodHandleCaching_returnsOneAcrossConcurrentThreads() throws Exception {
    int numThreads = 10;
    // race 10 threads to make the handle, check that they all return the same thing.
    var makeHandleLatch = new CountDownLatch(numThreads);
    var factory =
        new InternalFactory<String>() {
          @Override
          public String get(InternalContext context, Dependency<?> dependency, boolean linked) {
            return "Hello, World!";
          }

          @Override
          MethodHandleResult makeHandle(LinkageContext context, boolean linked) {
            makeHandleLatch.countDown();
            Uninterruptibles.awaitUninterruptibly(makeHandleLatch);
            return makeCachable(InternalMethodHandles.constantFactoryGetHandle("Hello, World!"));
          }
        };
    var pool = Executors.newFixedThreadPool(numThreads);
    var futures =
        pool.invokeAll(
            Collections.nCopies(numThreads, () -> factory.getHandle(new LinkageContext(), false)));
    pool.shutdown();
    var cachedResult = factory.getHandle(new LinkageContext(), false);
    for (var future : futures) {
      assertThat(future.get()).isSameInstanceAs(cachedResult);
    }
  }

  @Test
  public void testMethodHandleCaching_uncachableDoesntCache() throws Exception {
    var factory =
        new InternalFactory<String>() {
          @Override
          public String get(InternalContext context, Dependency<?> dependency, boolean linked) {
            return "Hello, World!";
          }

          @Override
          MethodHandleResult makeHandle(LinkageContext context, boolean linked) {
            return makeUncachable(InternalMethodHandles.constantFactoryGetHandle("Hello, World!"));
          }
        };

    assertThat(factory.getHandle(new LinkageContext(), false))
        .isNotSameInstanceAs(factory.getHandle(new LinkageContext(), false));
  }

  @Test
  public void testMethodHandleCaching_linkedCachesForEach() throws Exception {
    var factory =
        new InternalFactory<String>() {
          @Override
          public String get(InternalContext context, Dependency<?> dependency, boolean linked) {
            return "Hello, World!";
          }

          @Override
          MethodHandleResult makeHandle(LinkageContext context, boolean linked) {
            return makeCachableOnLinkedSetting(
                InternalMethodHandles.constantFactoryGetHandle("Hello, World!"));
          }
        };

    assertThat(factory.getHandle(new LinkageContext(), false))
        .isSameInstanceAs(factory.getHandle(new LinkageContext(), false));
    assertThat(factory.getHandle(new LinkageContext(), true))
        .isSameInstanceAs(factory.getHandle(new LinkageContext(), true));

    assertThat(factory.getHandle(new LinkageContext(), true))
        .isNotSameInstanceAs(factory.getHandle(new LinkageContext(), false));
  }
}
