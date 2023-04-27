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
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.Transactional;
import com.google.inject.persist.UnitOfWork;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A test around providing sessions (starting, closing etc.)
 *
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */

public class EntityManagerPerRequestProvisionTest {
  private Injector injector;

  @BeforeEach
  public void setUp() {
    injector = Guice.createInjector(new JpaPersistModule("testUnit"));

    //startup persistence
    injector.getInstance(PersistService.class).start();

    injector.getInstance(UnitOfWork.class).begin();
  }

  @AfterEach
  public final void tearDown() {
    injector.getInstance(UnitOfWork.class).end();
    injector.getInstance(EntityManagerFactory.class).close();
  }

  @Test
  public void testEntityManagerLifecyclePerTxn() {
    //obtain em
    JpaDao dao = injector.getInstance(JpaDao.class);

    //obtain same em again (bound to txn)
    JpaTestEntity te = new JpaTestEntity();

    dao.persist(te);

    //im not sure this hack works...
    assertEquals(
        injector.getInstance(EntityManager.class),
        JpaDao.em,
        "Entity managers closed inside same thread-scope");

    //try to start a new em in a new txn
    dao = injector.getInstance(JpaDao.class);

    assertTrue(
        dao.contains(te),
        "EntityManager was closed and reopened around txn"
            + " (persistent object does not persist)");
  }

  @Test
  public void testEntityManagerLifecyclePerTxn2() {
    //obtain em
    JpaDao dao = injector.getInstance(JpaDao.class);

    //obtain same em again (bound to txn)
    JpaTestEntity te = new JpaTestEntity();

    dao.persist(te);

    //im not sure this hack works...
    assertEquals(
        injector.getInstance(EntityManager.class),
        JpaDao.em,
        "Duplicate entity managers crossing-scope");
    assertEquals(
        injector.getInstance(EntityManager.class),
        JpaDao.em,
        "Duplicate entity managers crossing-scope");

    //try to start a new em in a new txn
    dao = injector.getInstance(JpaDao.class);

    assertTrue(
        dao.contains(te),
        "EntityManager was closed and reopened around txn" + " (persistent object doesnt persist)");
  }

  public static class JpaDao {
    static EntityManager em;

    @Inject
    public JpaDao(EntityManager em) {
      JpaDao.em = em;
    }

    @Transactional
    public <T> void persist(T t) {
      assertTrue(em.isOpen(), "em is not open!");
      assertTrue(em.getTransaction().isActive(), "no active txn!");
      em.persist(t);

      assertTrue(em.contains(t), "Persisting object failed");
    }

    @Transactional
    public <T> boolean contains(T t) {
      return em.contains(t);
    }
  }
}
