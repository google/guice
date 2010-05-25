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
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.persist.PersistModule;
import com.google.inject.persist.PersistenceService;
import com.google.inject.persist.Transactional;
import com.google.inject.persist.UnitOfWork;
import com.google.inject.persist.WorkManager;
import java.util.Date;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Query;
import junit.framework.TestCase;

/**
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */
public class JpaWorkManagerTest extends TestCase {
  private Injector injector;
  private static final String UNIQUE_TEXT_3 = JpaWorkManagerTest.class.getSimpleName()
      + "CONSTRAINT_VIOLATING some other unique text" + new Date();

  @Override
  public void setUp() {
    injector = Guice.createInjector(new PersistModule() {

      @Override
      protected void configurePersistence() {
        workAcross(UnitOfWork.REQUEST).usingJpa("testUnit");
      }
    });

    //startup persistence
    injector.getInstance(PersistenceService.class).start();
  }

  @Override
  public void tearDown() {
    injector.getInstance(EntityManagerFactory.class).close();
  }

  public void testWorkManagerInSession() {
    injector.getInstance(WorkManager.class).begin();
    try {
      injector.getInstance(TransactionalObject.class).runOperationInTxn();
    } finally {
      injector.getInstance(WorkManager.class).end();

    }

    injector.getInstance(WorkManager.class).begin();
    injector.getInstance(EntityManager.class).getTransaction().begin();
    try {
      final Query query = injector.getInstance(EntityManager.class)
          .createQuery("select e from JpaTestEntity as e where text = :text");

      query.setParameter("text", UNIQUE_TEXT_3);
      final Object o = query.getSingleResult();

      assert null != o : "no result!!";
      assert o instanceof JpaTestEntity : "Unknown type returned " + o.getClass();
      JpaTestEntity ent = (JpaTestEntity) o;

      assert UNIQUE_TEXT_3.equals(ent.getText()) :
          "Incorrect result returned or not persisted properly" + ent.getText();

    } finally {
      injector.getInstance(EntityManager.class).getTransaction().commit();
      injector.getInstance(WorkManager.class).end();
    }
  }

  public void testCloseMoreThanOnce() {
    injector.getInstance(PersistenceService.class).shutdown();

    try {
      injector.getInstance(PersistenceService.class).shutdown();
      fail();
    } catch (IllegalStateException e) {
      // Ignored.
    }
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