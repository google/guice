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
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import junit.framework.TestCase;

/**
 * A test around providing sessions (starting, closing etc.)
 *
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */
public class EntityManagerPerRequestProvisionTest extends TestCase {
  private Injector injector;

  @Override
  public void setUp() {
    injector = Guice.createInjector(new PersistModule() {

      protected void configurePersistence() {
        workAcross(UnitOfWork.REQUEST).usingJpa("testUnit");
      }
    });

    //startup persistence
    injector.getInstance(PersistenceService.class).start();

    injector.getInstance(WorkManager.class).begin();
  }

  @Override
  public final void tearDown() {
    injector.getInstance(WorkManager.class).end();
    injector.getInstance(EntityManagerFactory.class).close();
  }

  public void testEntityManagerLifecyclePerTxn() {
    //obtain em
    JpaDao dao = injector.getInstance(JpaDao.class);

    //obtain same em again (bound to txn)
    JpaTestEntity te = new JpaTestEntity();

    dao.persist(te);

    //im not sure this hack works...
    assertEquals("Entity managers closed inside same thread-scope",
        injector.getInstance(EntityManager.class), JpaDao.em);

    //try to start a new em in a new txn
    dao = injector.getInstance(JpaDao.class);

    assertTrue("EntityManager was closed and reopened around txn"
        + " (persistent object does not persist)", dao.contains(te));
  }

  public void testEntityManagerLifecyclePerTxn2() {
    //obtain em
    JpaDao dao = injector.getInstance(JpaDao.class);

    //obtain same em again (bound to txn)
    JpaTestEntity te = new JpaTestEntity();

    dao.persist(te);

    //im not sure this hack works...
    assertEquals("Duplicate entity managers crossing-scope",
        injector.getInstance(EntityManager.class), JpaDao.em);
    assertEquals("Duplicate entity managers crossing-scope",
        injector.getInstance(EntityManager.class), JpaDao.em);

    //try to start a new em in a new txn
    dao = injector.getInstance(JpaDao.class);

    assertTrue("EntityManager was closed and reopened around txn"
        + " (persistent object doesnt persist)", dao.contains(te));
  }

  public static class JpaDao {
    static EntityManager em;

    @Inject
    public JpaDao(EntityManager em) {
      JpaDao.em = em;
    }

    @Transactional
    public <T> void persist(T t) {
      assert em.isOpen() : "em is not open!";
      assert em.getTransaction().isActive() : "no active txn!";
      em.persist(t);

      assert em.contains(t) : "Persisting object failed";
    }

    @Transactional
    public <T> boolean contains(T t) {
      return em.contains(t);
    }
  }
}