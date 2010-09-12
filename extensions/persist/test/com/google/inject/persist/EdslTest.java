package com.google.inject.persist;

import com.google.inject.InjectorBuilder;
import com.google.inject.Stage;
import com.google.inject.persist.jpa.JpaPersistModule;
import junit.framework.TestCase;

/**
 * @author dhanji@google.com (Dhanji R. Prasanna)
 */
public class EdslTest extends TestCase {

  public void testModuleConfigUsingJpa() throws Exception {
    new InjectorBuilder()
        .addModules(new JpaPersistModule("myunit"))
        .stage(Stage.PRODUCTION)
        .requireExplicitBindings()
        .build();
  }
}
