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

import static com.google.inject.persist.utils.PersistenceUtils.sessionOpenCount;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.google.inject.persist.utils.PersistenceInjectorResource;
import com.google.inject.persist.utils.PersistenceUtils;
import java.util.Arrays;
import javax.persistence.EntityManager;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * A test around providing sessions (starting, closing etc.)
 *
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */

@RunWith(Parameterized.class)
public class EntityManagerPerRequestProvisionTest {

  @ClassRule
  @Rule
  public static PersistenceInjectorResource injector = new PersistenceInjectorResource("testUnit");

  @Parameter(0)
  public int repetitions;

  @Parameters(name = "{0} repeated EntityManager requests")
  public static Iterable<Integer> repetitions() {
    return Arrays.asList(1, 5);
  }

  @Test
  public void testEntityManagerLifecyclePerTxnFor() {
    PersistenceUtils.withinUnitOfWork(injector, entityManager -> {
      //obtain em
      JpaDao dao = injector.getInstance(JpaDao.class);
      JpaTestEntity te = new JpaTestEntity();
      dao.persist(te);
      long initialSessionsOpen = sessionOpenCount(injector);

      //obtain same em again (bound to txn)
      for (int i = 0; i < repetitions; ++i) {
        injector.getInstance(EntityManager.class).isOpen();
      }

      assertEquals(
          "Entity managers closed inside same thread-scope",
          sessionOpenCount(injector),
          initialSessionsOpen);

      //try to start a new em in a new txn
      dao = injector.getInstance(JpaDao.class);

      assertTrue(
          "EntityManager was closed and reopened around txn"
              + " (persistent object does not persist)",
          dao.contains(te));
    });
  }

  public static class JpaDao {
    private EntityManager em;

    @Inject
    public JpaDao(EntityManager em) {
      this.em = em;
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
