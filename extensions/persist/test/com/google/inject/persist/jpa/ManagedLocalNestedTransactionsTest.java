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

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Provider;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.Transactional;
import com.google.inject.persist.UnitOfWork;

import junit.framework.TestCase;

/**
 * Test of methods with nested Transactional annotations.
 */
public class ManagedLocalNestedTransactionsTest extends TestCase {
  private Injector injector;
  private static final String NESTED_TEXT = "nested-" + new Date().getTime();
  private static final String TEXT_BEFORE_NEST = "before-nest-" + new Date().getTime();
  private static final String TEXT_AFTER_NEST = "after-nest-" + new Date().getTime();

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

  public void testCommittedTransactionWithNoUnitOfWorkStarted() {
    newTransactionalObject().runNestedOperationInTxn();

    assertUnitOfWorkClosed();
    assertStoredTextsAre(TEXT_BEFORE_NEST, NESTED_TEXT, TEXT_AFTER_NEST);
  }

  public void testCommittedTransactionWithUnitOfWorkStarted() {
    UnitOfWork uow = injector.getInstance(UnitOfWork.class);
    uow.begin();

    newTransactionalObject().runNestedOperationInTxn();

    assertUnitOfWorkNotClosed();
    assertTransactionClosed();
    uow.end();
    assertStoredTextsAre(TEXT_BEFORE_NEST, NESTED_TEXT, TEXT_AFTER_NEST);
  }

  public void testCommittedTransactionWithUnitOfWorkAndTransactionStarted() {
    JpaPersistService persistService = injector.getInstance(JpaPersistService.class);

    UnitOfWork unitOfWork = injector.getInstance(UnitOfWork.class);
    unitOfWork.begin();

    EntityTransaction transaction = persistService.get().getTransaction();
    transaction.begin();

    newTransactionalObject().runNestedOperationInTxn();

    assertUnitOfWorkNotClosed();
    assertTrue(transaction.isActive());
    transaction.commit();
    unitOfWork.end();
    assertStoredTextsAre(TEXT_BEFORE_NEST, NESTED_TEXT, TEXT_AFTER_NEST);
  }

  public void testInnerRolledbackTransactionWithNoUnitOfWorkStarted() {
    try {
      newTransactionalObject().runNestedOperationInTxnThrowingInnerUnchecked();
    } catch (RuntimeException e) {
    }

    assertUnitOfWorkClosed();
    assertNoTextsStored();
  }

  public void testInnerRolledbackTransactionWithUnitOfWorkStarted() {
    UnitOfWork uow = injector.getInstance(UnitOfWork.class);
    uow.begin();

    try {
      newTransactionalObject().runNestedOperationInTxnThrowingInnerUnchecked();
    } catch (RuntimeException e) {
    }

    assertUnitOfWorkNotClosed();
    assertTransactionClosed();
    uow.end();
    assertNoTextsStored();
  }

  public void testOuterRolledbackTransactionWithNoUnitOfWorkStarted() {
    try {
      newTransactionalObject().runNestedOperationInTxnThrowingOuterUnchecked();
    } catch (RuntimeException e) {
    }

    assertUnitOfWorkClosed();
    assertNoTextsStored();
  }

  public void testOuterRolledbackTransactionWithUnitOfWorkStarted() {
    UnitOfWork uow = injector.getInstance(UnitOfWork.class);
    uow.begin();

    try {
      newTransactionalObject().runNestedOperationInTxnThrowingOuterUnchecked();
    } catch (RuntimeException e) {
    }

    assertUnitOfWorkNotClosed();
    assertTransactionClosed();
    uow.end();
    assertNoTextsStored();
  }

  private TransactionalObject newTransactionalObject() {
    return injector.getInstance(TransactionalObject.class);
  }

  private void assertStoredTextsAre(String... texts) {
    UnitOfWork uow = injector.getInstance(UnitOfWork.class);
    uow.begin();
    try {
      EntityManager em = injector.getInstance(EntityManager.class);
      List<String> result =
          em.createQuery("select text from JpaTestEntity", String.class).getResultList();

      Set<String> currentTextsSet = new HashSet<String>(result);
      Set<String> expectedTextsSet = new HashSet<String>(Arrays.asList(texts));
      assertEquals(expectedTextsSet, currentTextsSet);

    } finally {
      uow.end();
    }
  }

  private void assertNoTextsStored() {
    JpaPersistService persistService = injector.getInstance(JpaPersistService.class);
    persistService.begin();
    try {
      EntityManager em = injector.getInstance(EntityManager.class);
      long textCount =
          em.createQuery("select count(*) from JpaTestEntity", Long.class).getSingleResult();

      assertEquals("No text should have been saved", 0, textCount);

    } finally {
      persistService.end();
    }
  }

  private EntityManager assertTransactionClosed() {
    EntityManager em = injector.getInstance(EntityManager.class);
    assertFalse("txn was not closed by transactional service", em.getTransaction().isActive());
    return em;
  }

  private void assertUnitOfWorkClosed() {
    JpaPersistService persistService = injector.getInstance(JpaPersistService.class);
    assertFalse("Unit of work is still open but it should be closed", persistService.isWorking());
  }

  private void assertUnitOfWorkNotClosed() {
    JpaPersistService persistService = injector.getInstance(JpaPersistService.class);
    assertTrue("Unit of work is close but it should be still opened", persistService.isWorking());
  }


  public static class TransactionalObject {
    private final Provider<EntityManager> emProvider;

    @Inject
    public TransactionalObject(Provider<EntityManager> emProvider) {
      this.emProvider = emProvider;
    }

    @Transactional
    public void runNestedOperationInTxn() {
      saveText(TEXT_BEFORE_NEST);
      runOperationInTxn();
      saveText(TEXT_AFTER_NEST);
    }

    @Transactional
    public void runOperationInTxn() {
      saveText(NESTED_TEXT);
    }

    @Transactional
    public void runNestedOperationInTxnThrowingInnerUnchecked() {
      saveText(TEXT_BEFORE_NEST);
      runOperationInTxnThrowingUnchecked();
      saveText(TEXT_AFTER_NEST);
    }

    @Transactional
    public void runOperationInTxnThrowingUnchecked() {
      saveText(NESTED_TEXT);
      throw new IllegalStateException();
    }

    @Transactional
    public void runNestedOperationInTxnThrowingOuterUnchecked() {
      saveText(TEXT_BEFORE_NEST);
      runOperationInTxn();
      saveText(TEXT_AFTER_NEST);
      throw new IllegalStateException();
    }

    private void saveText(String text) {
      JpaTestEntity entity = new JpaTestEntity();
      entity.setText(text);
      em().persist(entity);
    }

    private EntityManager em() {
      return emProvider.get();
    }
  }
}
