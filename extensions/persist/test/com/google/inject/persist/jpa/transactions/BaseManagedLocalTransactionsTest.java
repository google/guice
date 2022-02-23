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

package com.google.inject.persist.jpa.transactions;

import static com.google.inject.persist.utils.PersistenceUtils.query;
import static com.google.inject.persist.utils.PersistenceUtils.successfulTransactionCount;
import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.inject.persist.jpa.entities.JpaTestEntity;
import com.google.inject.persist.utils.PersistenceInjectorResource;
import com.google.inject.persist.utils.PersistenceUnitResource;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * This test asserts class level @Transactional annotation behavior.
 *
 * <p>Class-level @Transactional is a shortcut if all non-private methods in the class are meant to
 * be transactional.
 *
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */

public abstract class BaseManagedLocalTransactionsTest<T extends BaseManagedLocalTransactionsTest.TransactionalObject> {
  private static final String UNIQUE_TEXT = "JPAsome unique text88888" + new Date();
  private static final String UNIQUE_TEXT_2 = "JPAsome asda unique teasdalsdplasdxt" + new Date();
  private static final String TRANSIENT_UNIQUE_TEXT =
      "JPAsome other unique texaksoksojadasdt" + new Date();

  @Rule
  @ClassRule
  public static PersistenceInjectorResource injector = new PersistenceInjectorResource("testUnit");

  @Rule
  @ClassRule
  public static PersistenceUnitResource persistenceUnit = new PersistenceUnitResource("testUnit");

  @Test
  public void testSimpleTransaction() {
    long initialTransactions = successfulTransactionCount(injector);
    T transactionalObject = getTransactionalObject();
    transactionalObject.runOperationInTxn();


    assertEquals("EntityManager was not closed by transactional service",
        successfulTransactionCount(injector), initialTransactions + 1);
    //test that the data has been stored
    List<JpaTestEntity> result =
        query(persistenceUnit).forList(JpaTestEntity.class,
            "SELECT e from JpaTestEntity e where e.text = :text",
            singletonMap("text", UNIQUE_TEXT));

    assertFalse(transactionalObject.getLastTransaction().isActive());

    assertEquals(
        "queried entity did not match--did automatic txn fail?",
        UNIQUE_TEXT,
        (result.get(0).getText()));
  }

  @Test
  public void testSimpleTransactionRollbackOnChecked() {
    long initialTransactions = successfulTransactionCount(injector);
    T transactionalObject = getTransactionalObject();
    try {
      transactionalObject.runOperationInTxnThrowingChecked();
    } catch (IOException e) {
      //ignore
    }

    assertEquals(
        "EntityManager was not closed by transactional service (rollback didnt happen?)",
        successfulTransactionCount(injector), initialTransactions);

    assertFalse(transactionalObject.getLastTransaction().isActive());

    //test that the data has been stored
    List<?> result =
        query(persistenceUnit).forList(JpaTestEntity.class,
            "SELECT e from JpaTestEntity e where e.text = :text",
            singletonMap("text", TRANSIENT_UNIQUE_TEXT));

    assertTrue("a result was returned! rollback sure didnt happen!!!", result.isEmpty());
  }

  @Test
  public void testSimpleTransactionRollbackWithIgnoredException() {
    long initialTransactions = successfulTransactionCount(injector);
    T transactionalObject = getTransactionalObject();
    try {
      transactionalObject.runOperationInTxnThrowingCheckedException();
      fail("Exception was not thrown by test txn-al method!");
    } catch (IOException e) {
      //ignored
    }

    assertEquals(
        "Txn was not closed by transactional service (commit didnt happen?)",
        successfulTransactionCount(injector), initialTransactions + 1);

    assertFalse(transactionalObject.getLastTransaction().isActive());

    //test that the data has been stored
    List<?> result =
        query(persistenceUnit).forList(JpaTestEntity.class,
            "SELECT e from JpaTestEntity e where e.text = :text",
            singletonMap("text", UNIQUE_TEXT_2));

    assertFalse("a result was not returned! rollback happened anyway (ignore failed)!!!",
        result.isEmpty());
  }

  @Test
  public void testSimpleTransactionRollbackOnUnchecked() {
    long initialTransactions = successfulTransactionCount(injector);
    T transactionalObject = getTransactionalObject();
    try {
      transactionalObject.runOperationInTxnThrowingUnchecked();
    } catch (RuntimeException re) {
      //ignore
    }

    assertEquals(
        "EntityManager was not closed by transactional service (rollback didnt happen?)",
        successfulTransactionCount(injector), initialTransactions);

    assertFalse(transactionalObject.getLastTransaction().isActive());

    //test that the data has been stored
    List<?> result =
        query(persistenceUnit).forList(JpaTestEntity.class,
            "SELECT e from JpaTestEntity e where e.text = :text",
            singletonMap("text", TRANSIENT_UNIQUE_TEXT));

    assertTrue("a result was returned! rollback sure didnt happen!!!", result.isEmpty());
  }

  T getTransactionalObject() {
    return injector.getInstance(getTransactionalObjectType());
  }

  protected abstract Class<T> getTransactionalObjectType();

  interface TransactionalObject {
    EntityTransaction getLastTransaction();

    void runOperationInTxn();

    void runOperationInTxnThrowingUnchecked();

    void runOperationInTxnThrowingCheckedException() throws IOException;

    void runOperationInTxnThrowingChecked() throws IOException;
  }

  public abstract static class TransactionalObjectImpl implements TransactionalObject {

    private EntityTransaction lastTransaction;

    protected abstract EntityManager getSession();

    public void runOperationInTxn() {
      verifyTransaction();
      JpaTestEntity entity = new JpaTestEntity();
      entity.setText(UNIQUE_TEXT);
      getSession().persist(entity);
    }

    public void runOperationInTxnThrowingUnchecked() {
      verifyTransaction();
      JpaTestEntity entity = new JpaTestEntity();
      entity.setText(TRANSIENT_UNIQUE_TEXT);
      getSession().persist(entity);

      throw new IllegalStateException();
    }

    public void runOperationInTxnThrowingCheckedException() throws IOException {
      verifyTransaction();
      JpaTestEntity entity = new JpaTestEntity();
      entity.setText(UNIQUE_TEXT_2);
      getSession().persist(entity);

      throw new FileNotFoundException();
    }

    public void runOperationInTxnThrowingChecked() throws IOException {
      verifyTransaction();
      JpaTestEntity entity = new JpaTestEntity();
      entity.setText(TRANSIENT_UNIQUE_TEXT);
      getSession().persist(entity);

      throw new IOException();
    }

    private void verifyTransaction() {
      assertTrue(getSession().getTransaction().isActive());
      lastTransaction = getSession().getTransaction();
    }

    @Override
    public EntityTransaction getLastTransaction() {
      return lastTransaction;
    }
  }
}
