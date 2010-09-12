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
import com.google.inject.persist.PersistService;
import com.google.inject.persist.Transactional;
import com.google.inject.persist.UnitOfWork;
import java.io.IOException;
import java.util.Date;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.NoResultException;
import junit.framework.TestCase;

/**
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */
public class JoiningLocalTransactionsTest extends TestCase {
  private Injector injector;
  private static final String UNIQUE_TEXT = JoiningLocalTransactionsTest.class + "some unique text"
      + new Date();
  private static final String TRANSIENT_UNIQUE_TEXT = JoiningLocalTransactionsTest.class
      + "some other unique text" + new Date();

  @Override
  public void setUp() {
    injector = Guice.createInjector(new JpaPersistModule("testUnit"));

    //startup persistence
    injector.getInstance(PersistService.class).start();
  }

  //cleanup entitymanager in case some of the rollback tests left it in an open state
  @Override
  public final void tearDown() {
    injector.getInstance(UnitOfWork.class).end();
    injector.getInstance(EntityManagerFactory.class).close();
  }

  public void testSimpleTransaction() {
    injector.getInstance(JoiningLocalTransactionsTest.TransactionalObject.class)
        .runOperationInTxn();

    EntityManager em = injector.getInstance(EntityManager.class);
    assertFalse("txn was not closed by transactional service",
        em.getTransaction().isActive());

    //test that the data has been stored
    Object result = em.createQuery("from JpaTestEntity where text = :text")
        .setParameter("text", UNIQUE_TEXT).getSingleResult();
    injector.getInstance(UnitOfWork.class).end();

    assertTrue("odd result returned fatal", result instanceof JpaTestEntity);

    assertEquals("queried entity did not match--did automatic txn fail?", UNIQUE_TEXT,
        ((JpaTestEntity) result).getText());
  }

  public void testSimpleTransactionRollbackOnChecked() {
    try {
      injector.getInstance(JoiningLocalTransactionsTest.TransactionalObject.class)
          .runOperationInTxnThrowingChecked();
    } catch (IOException e) {
      //ignore
      injector.getInstance(UnitOfWork.class).end();
    }

    EntityManager em = injector.getInstance(EntityManager.class);

    assertFalse("EM was not closed by transactional service (rollback didnt happen?)",
        em.getTransaction().isActive());

    //test that the data has been stored
    try {
      Object result = em.createQuery("from JpaTestEntity where text = :text")
          .setParameter("text", TRANSIENT_UNIQUE_TEXT).getSingleResult();
      injector.getInstance(UnitOfWork.class).end();
      fail("a result was returned! rollback sure didnt happen!!!");
    } catch (NoResultException e) { }
  }

  public void testSimpleTransactionRollbackOnUnchecked() {
    try {
      injector.getInstance(JoiningLocalTransactionsTest.TransactionalObject.class)
          .runOperationInTxnThrowingUnchecked();
    } catch (RuntimeException re) {
      //ignore
      injector.getInstance(UnitOfWork.class).end();
    }

    EntityManager em = injector.getInstance(EntityManager.class);
    assertFalse("Session was not closed by transactional service (rollback didnt happen?)",
        em.getTransaction().isActive());

    try {
      Object result = em.createQuery("from JpaTestEntity where text = :text")
          .setParameter("text", TRANSIENT_UNIQUE_TEXT).getSingleResult();
      injector.getInstance(UnitOfWork.class).end();
      fail("a result was returned! rollback sure didnt happen!!!");
    } catch (NoResultException e) {}
  }

  public static class TransactionalObject {
    private final EntityManager em;

    @Inject
    public TransactionalObject(EntityManager em) {
      this.em = em;
    }

    @Transactional
    public void runOperationInTxn() {
      runOperationInTxnInternal();
    }

    @Transactional(rollbackOn = IOException.class)
    public void runOperationInTxnInternal() {
      JpaTestEntity entity = new JpaTestEntity();
      entity.setText(UNIQUE_TEXT);
      em.persist(entity);
    }

    @Transactional(rollbackOn = IOException.class)
    public void runOperationInTxnThrowingChecked() throws IOException {
      runOperationInTxnThrowingCheckedInternal();
    }

    @Transactional
    private void runOperationInTxnThrowingCheckedInternal() throws IOException {
      JpaTestEntity entity = new JpaTestEntity();
      entity.setText(TRANSIENT_UNIQUE_TEXT);
      em.persist(entity);

      throw new IOException();
    }

    @Transactional
    public void runOperationInTxnThrowingUnchecked() {
      runOperationInTxnThrowingUncheckedInternal();
    }

    @Transactional(rollbackOn = IOException.class)
    public void runOperationInTxnThrowingUncheckedInternal() {
      JpaTestEntity entity = new JpaTestEntity();
      entity.setText(TRANSIENT_UNIQUE_TEXT);
      em.persist(entity);

      throw new IllegalStateException();
    }
  }
}
