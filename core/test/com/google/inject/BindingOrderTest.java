/*
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

package com.google.inject;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** @author jessewilson@google.com (Jesse Wilson) */

public class BindingOrderTest {

  @Test
  public void testBindingOutOfOrder() {
    Guice.createInjector(
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(BoundFirst.class);
            bind(BoundSecond.class).to(BoundSecondImpl.class);
          }
        });
  }

  public static class BoundFirst {
    @Inject
    public BoundFirst(BoundSecond boundSecond) {}
  }

  interface BoundSecond {}

  static class BoundSecondImpl implements BoundSecond {}

  @Test
  public void testBindingOrderAndScopes() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(A.class);
                bind(B.class).asEagerSingleton();
              }
            });

    assertSame(injector.getInstance(A.class).b, injector.getInstance(A.class).b);
  }

  @Test
  public void testBindingWithExtraThreads() throws InterruptedException {
    final CountDownLatch ready = new CountDownLatch(1);
    final CountDownLatch done = new CountDownLatch(1);
    final AtomicReference<B> ref = new AtomicReference<>();

    final Object createsAThread =
        new Object() {
          @Inject
          void createAnotherThread(final Injector injector) {
            new Thread() {
              @Override
              public void run() {
                ready.countDown();
                A a = injector.getInstance(A.class);
                ref.set(a.b);
                done.countDown();
              }
            }.start();

            // to encourage collisions, we make sure the other thread is running before returning
            try {
              ready.await();
            } catch (InterruptedException e) {
              throw new RuntimeException(e);
            }
          }
        };

    Guice.createInjector(
        new AbstractModule() {
          @Override
          protected void configure() {
            requestInjection(createsAThread);
            bind(A.class).toInstance(new A());
          }
        });

    done.await();
    assertNotNull(ref.get());
  }

  static class A {
    @Inject B b;
  }

  static class B {}
}
