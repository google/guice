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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.Transactional;
import com.google.inject.persist.UnitOfWork;
import com.google.inject.persist.finder.Finder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A test around providing sessions (starting, closing etc.)
 *
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */

public class DynamicFinderTest {
  private Injector injector;

  @BeforeEach
  public void setUp() {
    injector = Guice.createInjector(new JpaPersistModule("testUnit").addFinder(JpaFinder.class));

    //startup persistence
    injector.getInstance(PersistService.class).start();
  }

  @AfterEach
  public final void tearDown() {
    injector.getInstance(PersistService.class).stop();
  }

  @Test
  public void testDynamicFinderListAll() {
    //obtain em
    JpaDao dao = injector.getInstance(JpaDao.class);

    //obtain same em again (bound to txn)
    JpaTestEntity te = new JpaTestEntity();
    te.setText("HIAjsOKAOSD" + new Date() + UUID.randomUUID());

    dao.persist(te);

    injector.getInstance(UnitOfWork.class).begin();
    List<JpaTestEntity> list = injector.getInstance(JpaFinder.class).listAll();
    assertNotNull(list);
    assertFalse(list.isEmpty());
    assertEquals(1, list.size());
    assertEquals(te, list.get(0));
  }

  public static interface JpaFinder {
    @Finder(query = "from JpaTestEntity", returnAs = ArrayList.class)
    public List<JpaTestEntity> listAll();
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
