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

import static com.google.inject.persist.utils.PersistenceUtils.query;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.google.inject.persist.UnitOfWork;
import com.google.inject.persist.utils.PersistenceInjectorResource;
import com.google.inject.persist.utils.PersistenceUnitResource;
import java.util.Collections;
import java.util.Date;
import javax.persistence.EntityManager;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Created with IntelliJ IDEA. On: 2/06/2007
 *
 * <p>For instance, a session-per-request strategy will control the opening and closing of the EM at
 * its own (manual) discretion. As opposed to a transactional unit of work.
 *
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 * @since 1.0
 */

public class ManualLocalTransactionsWithCustomMatcherTest {

  private static final String UNIQUE_TEXT = "some unique text" + new Date();
  private static final String UNIQUE_TEXT_2 = "some other unique text" + new Date();

  @Rule
  @ClassRule
  public static PersistenceInjectorResource injector = new PersistenceInjectorResource("testUnit");

  @Rule
  @ClassRule
  public static PersistenceUnitResource persistenceUnit = new PersistenceUnitResource("testUnit");

  @Before
  public void setUp() {
    injector.getInstance(UnitOfWork.class).begin();
  }

  @After
  public void tearDown() {
    injector.getInstance(UnitOfWork.class).end();
  }

  @Test
  public void testSimpleCrossTxnWork() {
    //pretend that the request was started here
    EntityManager em = injector.getInstance(EntityManager.class);
    em.isOpen(); // needed because the EntityManager proxy is lazy

    JpaTestEntity entity =
        injector
            .getInstance(TransactionalObject.class)
            .runOperationInTxn();
    injector
        .getInstance(TransactionalObject.class)
        .runOperationInTxn2();

    //persisted entity should remain in the same em (which should still be open)
    assertTrue(
        "EntityManager  appears to have been closed across txns!",
        injector.getInstance(EntityManager.class).contains(entity));
    assertTrue("EntityManager  appears to have been closed across txns!", em.contains(entity));
    assertTrue("EntityManager appears to have been closed across txns!", em.isOpen());

    injector.getInstance(UnitOfWork.class).end();

    //try to query them back out
    assertFalse(query(persistenceUnit)
            .forList(
                JpaTestEntity.class,
                "SELECT e FROM JpaTestEntity e WHERE e.text = :text",
                Collections.singletonMap("text", UNIQUE_TEXT))
        .isEmpty());
    assertFalse(query(persistenceUnit)
        .forList(
            JpaTestEntity.class,
            "SELECT e FROM JpaTestEntity e WHERE e.text = :text",
            Collections.singletonMap("text", UNIQUE_TEXT_2))
        .isEmpty());
  }

  public static class TransactionalObject {
    @Inject EntityManager em;

    @Transactional
    public JpaTestEntity runOperationInTxn() {
      JpaTestEntity entity = new JpaTestEntity();
      entity.setText(UNIQUE_TEXT);
      em.persist(entity);

      return entity;
    }

    @Transactional
    public void runOperationInTxn2() {
      JpaTestEntity entity = new JpaTestEntity();
      entity.setText(UNIQUE_TEXT_2);
      em.persist(entity);
    }
  }
}
