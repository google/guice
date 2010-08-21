package com.google.inject.lifecycle;

import com.google.inject.Guice;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import junit.framework.TestCase;

/** @author dhanji@gmail.com (Dhanji R. Prasanna) */
public class ParallelizedBroadcastTest extends TestCase {
  private static final AtomicInteger called = new AtomicInteger();

  public final void testCallable() throws InterruptedException {
    called.set(0);
    Lifecycle lifecycle = Guice.createInjector(new LifecycleModule() {

      @Override
      protected void configureLifecycle() {
        bind(Runnable.class).to(AClass.class);

        // Does not get called because the key does not expose the callable.
        bind(Object.class).to(AClass.class);

        bind(BaseClass.class).to(AClass.class);

        callable(Runnable.class);
      }

    }).getInstance(Lifecycle.class);

    final ExecutorService executorService = Executors.newFixedThreadPool(3);
    lifecycle
      .broadcast(Runnable.class, executorService)
      .run();

    executorService.shutdown();
    executorService.awaitTermination(5L, TimeUnit.SECONDS);

    assertEquals(2, called.get());
  }

  public static class BaseClass implements Runnable {

    public void run() {
      called.incrementAndGet();
    }
  }

  public static class AClass extends BaseClass implements Runnable {
    public void run() {
      super.run();
    }
  }
}
