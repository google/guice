/*
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.UnitOfWork;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** @author Dhanji R. Prasanna (dhanji@gmail.com) */

public class EntityManagerFactoryProvisionTest {
  private Injector injector;

  @BeforeEach
  public void setUp() {
    injector = Guice.createInjector(new JpaPersistModule("testUnit"));
  }

  @AfterEach
  public final void tearDown() {
    injector.getInstance(UnitOfWork.class).end();
    injector.getInstance(EntityManagerFactory.class).close();
  }

  @Test
  public void testSessionCreateOnInjection() {

    assertEquals(
        injector.getInstance(UnitOfWork.class),
        injector.getInstance(UnitOfWork.class),
        "SINGLETON VIOLATION " + UnitOfWork.class.getName());

    //startup persistence
    injector.getInstance(PersistService.class).start();

    injector.getInstance(UnitOfWork.class).begin();

    //obtain em
    assertTrue(injector.getInstance(EntityManager.class).isOpen());
  }
}
