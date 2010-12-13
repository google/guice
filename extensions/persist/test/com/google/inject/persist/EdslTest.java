package com.google.inject.persist;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Stage;
import com.google.inject.persist.jpa.JpaPersistModule;
import junit.framework.TestCase;

/**
 * @author dhanji@google.com (Dhanji R. Prasanna)
 */
public class EdslTest extends TestCase {

  public void testModuleConfigUsingJpa() throws Exception {
    Guice.createInjector(Stage.PRODUCTION, new AbstractModule() {
      @Override
      protected void configure() {
        install(new JpaPersistModule("myunit"));
        binder().requireExplicitBindings();
      };
    });
  }
}
