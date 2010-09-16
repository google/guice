package com.google.inject.service;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import junit.framework.TestCase;

/**
 * Tests using Async Service.
 */
public class SingleServiceIntegrationTest extends TestCase {
  
  public final void testAsyncServiceLifecycle() throws InterruptedException {
    ExecutorService executor = Executors.newSingleThreadExecutor();

    final CountDownLatch startLatch = new CountDownLatch(1);
    final CountDownLatch stopLatch = new CountDownLatch(1);
    AsyncService service = new AsyncService(executor) {
      @Override protected void onStart() {
        assertEquals(1, startLatch.getCount());
        assertEquals(1, stopLatch.getCount());

        startLatch.countDown();
      }

      @Override protected void onStop() {
        assertEquals(0, startLatch.getCount());
        assertEquals(1, stopLatch.getCount());

        stopLatch.countDown();
      }
    };

    service.start();
    // This should not pass!
    assertTrue(startLatch.await(2, TimeUnit.SECONDS));

    service.stop();
    assertTrue(stopLatch.await(2, TimeUnit.SECONDS));

    executor.shutdown();
    assertEquals(0, startLatch.getCount());
    assertEquals(0, stopLatch.getCount());
  }

  public final void testAsyncServiceBlockingLifecycle()
      throws InterruptedException, ExecutionException, TimeoutException {
    ExecutorService executor = Executors.newSingleThreadExecutor();

    final AtomicInteger integer = new AtomicInteger(2);
    AsyncService service = new AsyncService(executor) {
      @Override protected void onStart() {
        assertEquals(2, integer.getAndDecrement());
      }

      @Override protected void onStop() {
        assertEquals(1, integer.getAndDecrement());
      }
    };

    service.start().get(2, TimeUnit.SECONDS);
    service.stop().get(2, TimeUnit.SECONDS);

    executor.shutdown();
    assertEquals(0, integer.get());
  }
}
