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
import static org.junit.jupiter.api.Assertions.fail;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Named;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.Transactional;
import com.google.inject.persist.UnitOfWork;
import com.google.inject.persist.finder.Finder;
import java.io.IOException;
import java.util.Date;
import javax.inject.Provider;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.NoResultException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** @author Dhanji R. Prasanna (dhanji@gmail.com) */

public class ManagedLocalTransactionsAcrossRequestTest {
  private Injector injector;
  private static final String UNIQUE_TEXT = "some unique text" + new Date();
  private static final String UNIQUE_TEXT_MERGE = "meRG_Esome unique text" + new Date();
  private static final String UNIQUE_TEXT_MERGE_FORDF =
      "aSdoaksdoaksdmeRG_Esome unique text" + new Date();
  private static final String TRANSIENT_UNIQUE_TEXT = "some other unique text" + new Date();

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
  public void testSimpleTransaction() {
    injector.getInstance(TransactionalObject.class).runOperationInTxn();

    injector.getInstance(UnitOfWork.class).begin();
    EntityManager em = injector.getInstance(EntityManager.class);
    assertFalse(em.getTransaction().isActive());

    //test that the data has been stored
    Object result =
        em.createQuery("from JpaTestEntity where text = :text")
            .setParameter("text", UNIQUE_TEXT)
            .getSingleResult();
    injector.getInstance(UnitOfWork.class).end();

    assertTrue(result instanceof JpaTestEntity, "odd result returned fatal");

    assertEquals(
        UNIQUE_TEXT,
        ((JpaTestEntity) result).getText(),
        "queried entity did not match--did automatic txn fail?");
    injector.getInstance(UnitOfWork.class).end();
  }

  @Test
  public void testSimpleTransactionWithMerge() {
    injector.getInstance(UnitOfWork.class).begin();
    EntityManager emOrig = injector.getInstance(EntityManager.class);
    JpaTestEntity entity =
        injector.getInstance(TransactionalObject.class).runOperationInTxnWithMerge();

    assertNotNull(entity.getId(), "Entity was not given an id (was not persisted correctly?)");

    EntityManager em = injector.getInstance(EntityManager.class);
    assertFalse(em.getTransaction().isActive());

    //test that the data has been stored
    assertTrue(em.isOpen(), "Em was closed after txn!");
    assertEquals(emOrig, em, "Em was not kept open across txns");
    assertTrue(em.contains(entity), "Merge did not store state or did not return persistent copy");

    Object result =
        em.createQuery("from JpaTestEntity where text = :text")
            .setParameter("text", UNIQUE_TEXT_MERGE)
            .getSingleResult();
    injector.getInstance(UnitOfWork.class).end();

    assertTrue(result instanceof JpaTestEntity);

    assertEquals(
        UNIQUE_TEXT_MERGE,
        ((JpaTestEntity) result).getText(),
        "queried entity did not match--did automatic txn fail?");
    injector.getInstance(UnitOfWork.class).end();
  }

  @Disabled
  @Test
  public void testSimpleTransactionWithMergeAndDF() {
    injector.getInstance(UnitOfWork.class).begin();
    EntityManager emOrig = injector.getInstance(EntityManager.class);
    JpaTestEntity entity =
        injector.getInstance(TransactionalObject.class).runOperationInTxnWithMergeForDf();

    EntityManager em = injector.getInstance(EntityManager.class);
    assertFalse(em.getTransaction().isActive(), "txn was not closed by transactional service");

    //test that the data has been stored
    assertTrue(em.isOpen(), "Em was closed after txn!");
    assertEquals(emOrig, em, "Em was not kept open across txns");
    assertTrue(em.contains(entity), "Merge did not store state or did not return persistent copy");

    Object result = injector.getInstance(TransactionalObject.class).find(UNIQUE_TEXT_MERGE_FORDF);
    injector.getInstance(UnitOfWork.class).end();

    assertNotNull(result);
    assertTrue(result instanceof JpaTestEntity);

    assertEquals(
        "queried entity did not match--did automatic txn fail?",
        UNIQUE_TEXT_MERGE_FORDF,
        ((JpaTestEntity) result).getText());
    injector.getInstance(UnitOfWork.class).end();
  }

  @Test
  public void testSimpleTransactionRollbackOnChecked() {
    try {
      injector.getInstance(TransactionalObject.class).runOperationInTxnThrowingChecked();
    } catch (IOException e) {
      //ignore
      injector.getInstance(UnitOfWork.class).end();
    }

    injector.getInstance(UnitOfWork.class).begin();
    EntityManager em = injector.getInstance(EntityManager.class);

    assertFalse(
        em.getTransaction().isActive(),
        "Previous EM was not closed by transactional service (rollback didnt happen?)");

    //test that the data has been stored
    try {
      Object result =
          em.createQuery("from JpaTestEntity where text = :text")
              .setParameter("text", TRANSIENT_UNIQUE_TEXT)
              .getSingleResult();
      injector.getInstance(UnitOfWork.class).end();
      fail();
    } catch (NoResultException e) {
    }

    injector.getInstance(UnitOfWork.class).end();
  }

  @Test
  public void testSimpleTransactionRollbackOnUnchecked() {
    try {
      injector.getInstance(TransactionalObject.class).runOperationInTxnThrowingUnchecked();
    } catch (RuntimeException re) {
      //ignore
      injector.getInstance(UnitOfWork.class).end();
    }

    injector.getInstance(UnitOfWork.class).begin();
    EntityManager em = injector.getInstance(EntityManager.class);
    assertFalse(
        em.getTransaction().isActive(),
        "Session was not closed by transactional service (rollback didnt happen?)");

    try {
      Object result =
          em.createQuery("from JpaTestEntity where text = :text")
              .setParameter("text", TRANSIENT_UNIQUE_TEXT)
              .getSingleResult();
      injector.getInstance(UnitOfWork.class).end();
      fail();
    } catch (NoResultException e) {
    }

    injector.getInstance(UnitOfWork.class).end();
  }

  public static class TransactionalObject {
    private final Provider<EntityManager> emProvider;

    @Inject
    public TransactionalObject(Provider<EntityManager> emProvider) {
      this.emProvider = emProvider;
    }

    @Transactional
    public void runOperationInTxn() {
      JpaTestEntity entity = new JpaTestEntity();
      entity.setText(UNIQUE_TEXT);
      emProvider.get().persist(entity);
    }

    @Transactional
    public JpaTestEntity runOperationInTxnWithMerge() {
      JpaTestEntity entity = new JpaTestEntity();
      entity.setText(UNIQUE_TEXT_MERGE);
      return emProvider.get().merge(entity);
    }

    @Transactional
    public JpaTestEntity runOperationInTxnWithMergeForDf() {
      JpaTestEntity entity = new JpaTestEntity();
      entity.setText(UNIQUE_TEXT_MERGE_FORDF);
      return emProvider.get().merge(entity);
    }

    @Transactional(rollbackOn = IOException.class)
    public void runOperationInTxnThrowingChecked() throws IOException {
      JpaTestEntity entity = new JpaTestEntity();
      entity.setText(TRANSIENT_UNIQUE_TEXT);
      emProvider.get().persist(entity);

      throw new IOException();
    }

    @Transactional
    public void runOperationInTxnThrowingUnchecked() {
      JpaTestEntity entity = new JpaTestEntity();
      entity.setText(TRANSIENT_UNIQUE_TEXT);
      emProvider.get().persist(entity);

      throw new IllegalStateException();
    }

    @Finder(query = "from JpaTestEntity where text = :text")
    public JpaTestEntity find(@Named("text") String text) {
      return null;
    }
  }
}
