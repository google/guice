/**
 * Copyright (C) 2010 Google, Inc.
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

package com.google.inject.persist.jpa;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.persist.PersistModule;
import com.google.inject.persist.PersistenceService;
import com.google.inject.persist.UnitOfWork;
import com.google.inject.persist.WorkManager;
import java.util.Properties;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import junit.framework.TestCase;

/**
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */
public class CustomPropsEntityManagerFactoryProvisionTest extends TestCase {
  private Injector injector;

  @Override
  public void setUp() {
    injector = Guice.createInjector(new PersistModule() {

      protected void configurePersistence() {
        Properties props = new Properties();
        props.put("blah", "blah");
        workAcross(UnitOfWork.TRANSACTION).usingJpa("testUnit", props);
      }
    });
  }

  @Override
  public final void tearDown() {
    injector.getInstance(WorkManager.class).end();
    injector.getInstance(EntityManagerFactory.class).close();
  }

  public void testSessionCreateOnInjection() {

    assertEquals("SINGLETON VIOLATION " + PersistenceService.class.getName(),
        injector.getInstance(PersistenceService.class),
        injector.getInstance(PersistenceService.class));

    //startup persistence
    injector.getInstance(PersistenceService.class).start();

    //obtain em
    assertTrue(injector.getInstance(EntityManager.class).isOpen());
  }
}
