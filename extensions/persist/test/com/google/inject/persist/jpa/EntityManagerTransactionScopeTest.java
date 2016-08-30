/**
 * Copyright (C) 2010 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.inject.persist.jpa;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.Transactional;

import junit.framework.TestCase;

/**
 * Test that when only transaction annotation is used EntityManager gets closed.
 *
 * @author Jarno Jantunen (jarno.jantunen@gmail.com)
 */

public class EntityManagerTransactionScopeTest extends TestCase {
  private Injector injector;

  @Override
  public void setUp() {
    injector = Guice.createInjector(new JpaPersistModule("testUnit"));

    // startup persistence
    injector.getInstance(PersistService.class).start();

  }

  @Override
  public final void tearDown() {
    injector.getInstance(EntityManagerFactory.class).close();
  }

  public void testEntityManagerLifecyclePerTxn() {
    // obtain em
    JpaDao dao = injector.getInstance(JpaDao.class);

    // obtain same em again (bound to txn)
    JpaTestEntity te = new JpaTestEntity();

    dao.persist(te);

    // im not sure this hack works...
    assertNotSame("Entity managers was not closed after transaction scoped unitOfWork",
        injector.getInstance(EntityManager.class), JpaDao.em);

    // try to start a new em in a new txn
    dao = injector.getInstance(JpaDao.class);

    assertFalse("EntityManager was not closed after transaction scoped unitOfWork",
        dao.contains(te));
  }


  public static class JpaDao {
    static EntityManager em;

    @Inject
    public JpaDao(EntityManager em) {
      JpaDao.em = em;
    }

    @Transactional
    public <T> void persist(T t) {
      assertTrue("em is not open!", em.isOpen());
      assertTrue("no active txn!", em.getTransaction().isActive());
      em.persist(t);

      assertTrue("Persisting object failed", em.contains(t));
    }

    @Transactional
    public <T> boolean contains(T t) {
      return em.contains(t);
    }
  }
}
