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
import com.google.inject.persist.WorkManager;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import junit.framework.TestCase;

/**
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */
public class EntityManagerFactoryProvisionTest extends TestCase {
  private Injector injector;

  public void setUp() {
    injector = Guice.createInjector(new JpaPersistModule("testUnit"));
  }

  public final void tearDown() {
    injector.getInstance(WorkManager.class).end();
    injector.getInstance(EntityManagerFactory.class).close();
  }

  public void testSessionCreateOnInjection() {

    assertTrue("SINGLETON VIOLATION " + WorkManager.class.getName(),
        injector.getInstance(WorkManager.class)
        .equals(injector.getInstance(WorkManager.class)));

    //startup persistence
    injector.getInstance(WorkManager.class).startPersistence();

    //obtain em
    assertTrue(injector.getInstance(EntityManager.class).isOpen());
  }
}
