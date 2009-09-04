package com.google.inject.lifecycle;

import com.google.inject.Guice;
import com.google.inject.Singleton;
import junit.framework.TestCase;

/** @author dhanji@google.com (Dhanji R. Prasanna) */
public class ArbitraryBroadcastTest extends TestCase {
  private static int called;

  public final void testCallable() {
    called = 0;
    Lifecycle lifecycle = Guice.createInjector(new LifecycleModule() {

      @Override
      protected void configureLifecycle() {
        bind(Runnable.class).to(AClass.class).in(Singleton.class);
        bind(AClass.class).in(Singleton.class);

        callable(Runnable.class);
      }

    }).getInstance(Lifecycle.class);

    lifecycle
      .broadcast(Runnable.class)
      .run();

    assertEquals(2, called);
  }

  public static class AClass implements Runnable {
    public void run() {
      called++;
    }
  }
}