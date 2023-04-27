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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.Transactional;
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

public class EntityManagerProvisionTest {
  private Injector injector;

  @BeforeEach
  public void setUp() {
    injector = Guice.createInjector(new JpaPersistModule("testUnit"));

    //startup persistence
    injector.getInstance(PersistService.class).start();
  }

  @AfterEach
  public final void tearDown() {
    injector.getInstance(EntityManagerFactory.class).close();
  }

  @Test
  public void testEntityManagerLifecyclePerTxn() {
    //obtain em
    JpaDao dao = injector.getInstance(JpaDao.class);

    //obtain same em again (bound to txn)
    JpaTestEntity te = new JpaTestEntity();

    dao.persist(te);

    //try to start a new em in a new txn
    dao = injector.getInstance(JpaDao.class);

    assertFalse(
        dao.contains(te),
        "EntityManager wasnt closed and reopened properly around txn"
            + " (persistent object persists)");
  }

  @Test
  public void testEntityManagerLifecyclePerTxn2() {
    //obtain em
    JpaDao dao = injector.getInstance(JpaDao.class);

    //obtain same em again (bound to txn)
    JpaTestEntity te = new JpaTestEntity();

    dao.persist(te);

    //try to start a new em in a new txn
    dao = injector.getInstance(JpaDao.class);

    assertFalse(
        dao.contains(te),
        "EntityManager wasnt closed and reopened properly around txn"
            + " (persistent object persists)");
  }

  public static class JpaDao {
    private final Provider<EntityManager> em;
    EntityManager lastEm;

    @Inject
    public JpaDao(Provider<EntityManager> em) {
      this.em = em;
    }

    @Transactional
    public <T> void persist(T t) {
      lastEm = em.get();
      assertTrue(lastEm.isOpen(), "em is not open!");
      assertTrue(lastEm.getTransaction().isActive(), "no active txn!");
      lastEm.persist(t);

      assertTrue(lastEm.contains(t), "Persisting object failed");
    }

    @Transactional
    public <T> boolean contains(T t) {
      if (null == lastEm) {
        lastEm = em.get();
      }
      return lastEm.contains(t);
    }
  }
}
