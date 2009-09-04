package com.google.inject.lifecycle;

import com.google.inject.Guice;
import com.google.inject.Singleton;
import junit.framework.TestCase;

/** @author dhanji@google.com (Dhanji R. Prasanna) */
public class StartableTest extends TestCase {
  private static boolean started;

  public final void testStartable() {
    started = false;
    Guice.createInjector(new LifecycleModule() {

      @Override
      protected void configureLifecycle() {
        bind(AClass.class).in(Singleton.class);
      }

    }).getInstance(Lifecycle.class)
      .start();

    assertTrue(started);
  }

  public static class AClass implements Startable {

    public void start() {
      started = true;
    }
  }
}
