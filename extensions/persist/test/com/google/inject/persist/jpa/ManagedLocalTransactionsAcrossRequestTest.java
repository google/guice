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
import com.google.inject.name.Named;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.Transactional;
import com.google.inject.persist.UnitOfWork;
import com.google.inject.persist.finder.Finder;
import java.io.IOException;
import java.util.Date;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.NoResultException;
import junit.framework.TestCase;

/**
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */
public class ManagedLocalTransactionsAcrossRequestTest extends TestCase {
  private Injector injector;
  private static final String UNIQUE_TEXT = "some unique text" + new Date();
  private static final String UNIQUE_TEXT_MERGE = "meRG_Esome unique text" + new Date();
  private static final String UNIQUE_TEXT_MERGE_FORDF = "aSdoaksdoaksdmeRG_Esome unique text"
      + new Date();
  private static final String TRANSIENT_UNIQUE_TEXT = "some other unique text" + new Date();

  @Override
  public void setUp() {
    injector = Guice.createInjector(new JpaPersistModule("testUnit"));

    //startup persistence
    injector.getInstance(PersistService.class).start();
  }

  @Override
  public final void tearDown() {
    injector.getInstance(EntityManagerFactory.class).close();
  }

  public void testSimpleTransaction() {
    injector.getInstance(TransactionalObject.class).runOperationInTxn();

    EntityManager em = injector.getInstance(EntityManager.class);
    assertFalse(em.getTransaction().isActive());

    //test that the data has been stored
    Object result = em.createQuery("from JpaTestEntity where text = :text")
        .setParameter("text", UNIQUE_TEXT).getSingleResult();
    injector.getInstance(UnitOfWork.class).end();

    assertTrue("odd result returned fatal", result instanceof JpaTestEntity);

    assertEquals("queried entity did not match--did automatic txn fail?",
        UNIQUE_TEXT, ((JpaTestEntity) result).getText());
    injector.getInstance(UnitOfWork.class).end();

  }

  public void testSimpleTransactionWithMerge() {
    EntityManager emOrig = injector.getInstance(EntityManager.class);
    JpaTestEntity entity = injector.getInstance(TransactionalObject.class)
        .runOperationInTxnWithMerge();

    assertNotNull("Entity was not given an id (was not persisted correctly?)", entity.getId());

    EntityManager em = injector.getInstance(EntityManager.class);
    assertFalse(em.getTransaction().isActive());

    //test that the data has been stored
    assertTrue("Em was closed after txn!", em.isOpen());
    assertEquals("Em was not kept open across txns", emOrig, em);
    assertTrue("Merge did not store state or did not return persistent copy", em.contains(entity));

    Object result = em.createQuery("from JpaTestEntity where text = :text")
        .setParameter("text", UNIQUE_TEXT_MERGE).getSingleResult();
    injector.getInstance(UnitOfWork.class).end();

    assertTrue(result instanceof JpaTestEntity);

    assertEquals("queried entity did not match--did automatic txn fail?",
        UNIQUE_TEXT_MERGE, ((JpaTestEntity) result).getText());
    injector.getInstance(UnitOfWork.class).end();

  }

  public void disabled_testSimpleTransactionWithMergeAndDF() {
    EntityManager emOrig = injector.getInstance(EntityManager.class);
    JpaTestEntity entity = injector.getInstance(TransactionalObject.class)
        .runOperationInTxnWithMergeForDf();

    EntityManager em = injector.getInstance(EntityManager.class);
    assertFalse("txn was not closed by transactional service", em.getTransaction().isActive());

    //test that the data has been stored
    assertTrue("Em was closed after txn!", em.isOpen());
    assertEquals("Em was not kept open across txns", emOrig, em);
    assertTrue("Merge did not store state or did not return persistent copy", em.contains(entity));

    Object result = injector.getInstance(TransactionalObject.class).find(UNIQUE_TEXT_MERGE_FORDF);
    injector.getInstance(UnitOfWork.class).end();

    assertNotNull(result);
    assertTrue(result instanceof JpaTestEntity);

    assertEquals("queried entity did not match--did automatic txn fail?",
        UNIQUE_TEXT_MERGE_FORDF, ((JpaTestEntity) result).getText());
    injector.getInstance(UnitOfWork.class).end();

  }

  public void testSimpleTransactionRollbackOnChecked() {
    try {
      injector.getInstance(TransactionalObject.class).runOperationInTxnThrowingChecked();
    } catch (IOException e) {
      //ignore
      injector.getInstance(UnitOfWork.class).end();
    }

    EntityManager em = injector.getInstance(EntityManager.class);

    assertFalse("Previous EM was not closed by transactional service (rollback didnt happen?)",
        em.getTransaction().isActive());

    //test that the data has been stored
    try {
      Object result = em.createQuery("from JpaTestEntity where text = :text")
          .setParameter("text", TRANSIENT_UNIQUE_TEXT).getSingleResult();
      injector.getInstance(UnitOfWork.class).end();
      fail();
    } catch (NoResultException e) {}

    injector.getInstance(UnitOfWork.class).end();
  }

  public void testSimpleTransactionRollbackOnUnchecked() {
    try {
      injector.getInstance(TransactionalObject.class).runOperationInTxnThrowingUnchecked();
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
      fail();
    } catch (NoResultException e) {}
    
    injector.getInstance(UnitOfWork.class).end();
  }

  public static class TransactionalObject {
    private final EntityManager em;

    @Inject
    public TransactionalObject(EntityManager em) {
      this.em = em;
    }

    @Transactional
    public void runOperationInTxn() {
      JpaTestEntity entity = new JpaTestEntity();
      entity.setText(UNIQUE_TEXT);
      em.persist(entity);
    }

    @Transactional
    public JpaTestEntity runOperationInTxnWithMerge() {
      JpaTestEntity entity = new JpaTestEntity();
      entity.setText(UNIQUE_TEXT_MERGE);
      return em.merge(entity);
    }

    @Transactional
    public JpaTestEntity runOperationInTxnWithMergeForDf() {
      JpaTestEntity entity = new JpaTestEntity();
      entity.setText(UNIQUE_TEXT_MERGE_FORDF);
      return em.merge(entity);
    }

    @Transactional(rollbackOn = IOException.class)
    public void runOperationInTxnThrowingChecked() throws IOException {
      JpaTestEntity entity = new JpaTestEntity();
      entity.setText(TRANSIENT_UNIQUE_TEXT);
      em.persist(entity);

      throw new IOException();
    }

    @Transactional
    public void runOperationInTxnThrowingUnchecked() {
      JpaTestEntity entity = new JpaTestEntity();
      entity.setText(TRANSIENT_UNIQUE_TEXT);
      em.persist(entity);

      throw new IllegalStateException();
    }

    @Finder(query = "from JpaTestEntity where text = :text")
    public JpaTestEntity find(@Named("text") String text) {
      return null;
    }
  }
}