package com.google.inject.lifecycle;

import com.google.inject.Guice;
import com.google.inject.Singleton;
import junit.framework.TestCase;

/** @author dhanji@google.com (Dhanji R. Prasanna) */
public class MultipleStartableTest extends TestCase {
  private static int started;

  public final void testMultiStartable() {
    started = 0;
    Guice.createInjector(new LifecycleModule() {

      @Override
      protected void configureLifecycle() {
        bind(AClass.class).in(Singleton.class);
        bind(Startable.class)
            .annotatedWith(ListOfMatchers.class)
            .to(BClass.class)
            .in(Singleton.class);
        bind(Startable.class).to(CClass.class).in(Singleton.class);
      }

    }).getInstance(Lifecycle.class)
      .start();

    assertEquals(3, started);
  }

  public static class AClass implements Startable {

    public void start() {
      started++;
    }
  }

  public static class BClass implements Startable {

    public void start() {
      started++;
    }
  }

  public static class CClass implements Startable {

    public void start() {
      started++;
    }
  }
}