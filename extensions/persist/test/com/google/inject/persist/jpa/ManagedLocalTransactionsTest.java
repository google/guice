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

import static org.junit.Assert.assertThrows;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.Transactional;
import com.google.inject.persist.UnitOfWork;
import java.io.IOException;
import java.util.Date;
import jakarta.inject.Provider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.NoResultException;
import junit.framework.TestCase;

/**
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */

public class ManagedLocalTransactionsTest extends TestCase {
  private Injector injector;
  private static final String UNIQUE_TEXT = "some unique text" + new Date();
  private static final String UNIQUE_TEXT_MERGE = "meRG_Esome unique text" + new Date();
  private static final String TRANSIENT_UNIQUE_TEXT = "some other unique text" + new Date();

  @Override
  public void setUp() {
    injector = Guice.createInjector(new JpaPersistModule("testUnit"));

    // startup persistence
    injector.getInstance(PersistService.class).start();
  }

  @Override
  public final void tearDown() {
    injector.getInstance(UnitOfWork.class).end();
    injector.getInstance(EntityManagerFactory.class).close();
  }

  public void testSimpleTransaction() {
    injector.getInstance(TransactionalObject.class).runOperationInTxn();

    injector.getInstance(UnitOfWork.class).begin();
    EntityManager em = injector.getInstance(EntityManager.class);
    assertFalse("txn was not closed by transactional service", em.getTransaction().isActive());

    // test that the data has been stored
    Object result =
        em.createQuery("from JpaTestEntity where text = :text")
            .setParameter("text", UNIQUE_TEXT)
            .getSingleResult();
    injector.getInstance(UnitOfWork.class).end();

    assertTrue("odd result returned fatal", result instanceof JpaTestEntity);

    assertEquals(
        "queried entity did not match--did automatic txn fail?",
        UNIQUE_TEXT,
        ((JpaTestEntity) result).getText());
  }

  public void testSimpleTransactionWithMerge() {
    JpaTestEntity entity =
        injector.getInstance(TransactionalObject.class).runOperationInTxnWithMerge();

    injector.getInstance(UnitOfWork.class).begin();
    EntityManager em = injector.getInstance(EntityManager.class);
    assertFalse("txn was not closed by transactional service", em.getTransaction().isActive());

    // test that the data has been stored
    assertTrue("Em was closed after txn!", em.isOpen());

    Object result =
        em.createQuery("from JpaTestEntity where text = :text")
            .setParameter("text", UNIQUE_TEXT_MERGE)
            .getSingleResult();
    injector.getInstance(UnitOfWork.class).end();

    assertTrue(result instanceof JpaTestEntity);

    assertEquals(
        "queried entity did not match--did automatic txn fail?",
        UNIQUE_TEXT_MERGE,
        ((JpaTestEntity) result).getText());
  }

  public void testSimpleTransactionRollbackOnChecked() {
    try {
      injector.getInstance(TransactionalObject.class).runOperationInTxnThrowingChecked();
    } catch (IOException e) {
      // ignore
      injector.getInstance(UnitOfWork.class).end();
    }

    injector.getInstance(UnitOfWork.class).begin();
    EntityManager em = injector.getInstance(EntityManager.class);

    assertFalse(
        "Previous EM was not closed by transactional service (rollback didnt happen?)",
        em.getTransaction().isActive());

    // test that the data has been stored
    assertThrows(
        NoResultException.class,
        () ->
            em.createQuery("from JpaTestEntity where text = :text")
                .setParameter("text", TRANSIENT_UNIQUE_TEXT)
                .getSingleResult());
    injector.getInstance(UnitOfWork.class).end();
  }

  public void testSimpleTransactionRollbackOnUnchecked() {
    try {
      injector.getInstance(TransactionalObject.class).runOperationInTxnThrowingUnchecked();
    } catch (RuntimeException re) {
      // ignore
      injector.getInstance(UnitOfWork.class).end();
    }

    injector.getInstance(UnitOfWork.class).begin();
    EntityManager em = injector.getInstance(EntityManager.class);
    assertFalse(
        "Session was not closed by transactional service (rollback didnt happen?)",
        em.getTransaction().isActive());

    assertThrows(
        NoResultException.class,
        () ->
            em.createQuery("from JpaTestEntity where text = :text")
                .setParameter("text", TRANSIENT_UNIQUE_TEXT)
                .getSingleResult());
    injector.getInstance(UnitOfWork.class).end();
  }

  public void testSimpleTransactionRollbackPerformedManuallyWithoutException() {
    injector.getInstance(TransactionalObject.class).runOperationInTxnWithManualRollback();

    injector.getInstance(UnitOfWork.class).begin();
    EntityManager em = injector.getInstance(EntityManager.class);
    assertFalse(
        "Session was not closed by transactional service (rollback didnt happen?)",
        em.getTransaction().isActive());

    assertThrows(
        NoResultException.class,
        () ->
            em.createQuery("from JpaTestEntity where text = :text")
                .setParameter("text", TRANSIENT_UNIQUE_TEXT)
                .getSingleResult());
    injector.getInstance(UnitOfWork.class).end();
  }

  public void testSimpleTransactionRollbackOnlySetWithoutException() {
    injector.getInstance(TransactionalObject.class).runOperationInTxnWithRollbackOnlySet();

    injector.getInstance(UnitOfWork.class).begin();
    EntityManager em = injector.getInstance(EntityManager.class);
    assertFalse(
        "Session was not closed by transactional service (rollback didnt happen?)",
        em.getTransaction().isActive());

    assertThrows(
        NoResultException.class,
        () ->
            em.createQuery("from JpaTestEntity where text = :text")
                .setParameter("text", TRANSIENT_UNIQUE_TEXT)
                .getSingleResult());
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

    @Transactional
    public void runOperationInTxnWithManualRollback() {
      JpaTestEntity entity = new JpaTestEntity();
      entity.setText(TRANSIENT_UNIQUE_TEXT);
      emProvider.get().persist(entity);

      emProvider.get().getTransaction().rollback();
    }

    @Transactional
    public void runOperationInTxnWithRollbackOnlySet() {
      JpaTestEntity entity = new JpaTestEntity();
      entity.setText(TRANSIENT_UNIQUE_TEXT);
      emProvider.get().persist(entity);

      emProvider.get().getTransaction().setRollbackOnly();
    }
  }
}
