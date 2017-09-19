package com.google.inject;

import junit.framework.TestCase;

public class SingletonModuleTest extends TestCase {

  static int counter = 0;

  @Override
  public void setUp() throws Exception {
    counter = 0;
  }

  public void testInstallBaseModuleOnlyOnce() {
    Guice.createInjector(new ModuleOne(), new ModuleOne());
    assertEquals(1, counter);
  }

  public void testInstallAbstractModuleMoreThanOnce() {
    Guice.createInjector(new ModuleTwo(), new ModuleTwo());
    assertEquals(2, counter);
  }

  static class ModuleOne extends SingletonModule {
    @Override
    protected void configure() {
      counter++;
    }
  }

  static class ModuleTwo extends AbstractModule {
    @Override
    protected void configure() {
      counter++;
    }
  }
}
