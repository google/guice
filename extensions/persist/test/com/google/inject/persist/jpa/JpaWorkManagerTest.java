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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.Transactional;
import com.google.inject.persist.UnitOfWork;
import java.util.Date;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Query;
import org.hibernate.HibernateException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */

public class JpaWorkManagerTest {
  private Injector injector;
  private static final String UNIQUE_TEXT_3 =
      JpaWorkManagerTest.class.getSimpleName()
          + "CONSTRAINT_VIOLATING some other unique text"
          + new Date();

  @BeforeEach
  public void setUp() {
    injector = Guice.createInjector(new JpaPersistModule("testUnit"));
  }

  @AfterEach
  public void tearDown() {
    try {
      injector.getInstance(EntityManagerFactory.class).close();
    } catch (HibernateException ex) {
      // Expected if the persist service has already been stopped.
    }
  }

  @Test
  public void testWorkManagerInSession() {
    injector.getInstance(PersistService.class).start();
    injector.getInstance(UnitOfWork.class).begin();
    try {
      injector.getInstance(TransactionalObject.class).runOperationInTxn();
    } finally {
      injector.getInstance(UnitOfWork.class).end();
    }

    injector.getInstance(UnitOfWork.class).begin();
    injector.getInstance(EntityManager.class).getTransaction().begin();
    try {
      final Query query =
          injector
              .getInstance(EntityManager.class)
              .createQuery("select e from JpaTestEntity as e where text = :text");

      query.setParameter("text", UNIQUE_TEXT_3);
      final Object o = query.getSingleResult();

      assertNotNull(o, "no result!!");
      assertTrue(o instanceof JpaTestEntity, "Unknown type returned " + o.getClass());
      JpaTestEntity ent = (JpaTestEntity) o;

      assertEquals(
          UNIQUE_TEXT_3,
          ent.getText(),
          "Incorrect result returned or not persisted properly" + ent.getText());

    } finally {
      injector.getInstance(EntityManager.class).getTransaction().commit();
      injector.getInstance(UnitOfWork.class).end();
    }
  }

  @Test
  public void testStartMoreThanOnce() {
    injector.getInstance(PersistService.class).start();
    // No exception is thrown on subsequent start.
    injector.getInstance(PersistService.class).start();
  }

  @Test
  public void testCloseMoreThanOnce() {
    injector.getInstance(PersistService.class).start();
    injector.getInstance(PersistService.class).stop();
    // No exception is thrown on subsequent stop.
    injector.getInstance(PersistService.class).stop();
  }

  @Test
  public void testCloseWithoutStart() {
    // No exception.
    injector.getInstance(PersistService.class).stop();
    // (but also start it so we can have one tearDown that unconditionally stops started things)
    injector.getInstance(PersistService.class).start();
  }

  public static class TransactionalObject {
    @Inject EntityManager em;

    @Transactional
    public void runOperationInTxn() {
      JpaTestEntity testEntity = new JpaTestEntity();

      testEntity.setText(UNIQUE_TEXT_3);
      em.persist(testEntity);
    }

    @Transactional
    public void runOperationInTxnError() {

      JpaTestEntity testEntity = new JpaTestEntity();

      testEntity.setText(UNIQUE_TEXT_3 + "transient never in db!" + hashCode());
      em.persist(testEntity);
    }
  }
}
