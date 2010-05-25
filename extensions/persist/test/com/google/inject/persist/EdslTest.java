package com.google.inject.persist;

import com.google.inject.InjectorBuilder;
import com.google.inject.Stage;
import junit.framework.TestCase;

/**
 * @author dhanji@google.com (Dhanji R. Prasanna)
 */
public class EdslTest extends TestCase {

  public void testModuleConfig() throws Exception {
    new InjectorBuilder().addModules(
      new PersistModule() {
        @Override
        protected void configurePersistence() {
          workAcross(UnitOfWork.REQUEST).usingJpa("myunit");
        }

      }).stage(Stage.TOOL)
        .requireExplicitBindings()
        .build();
  }
}
