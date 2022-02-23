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

import static com.google.inject.persist.utils.PersistenceUtils.successfulTransactionCount;
import static org.junit.Assert.assertEquals;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.google.inject.persist.UnitOfWork;
import java.io.FileNotFoundException;
import java.io.IOException;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import org.junit.Test;

/**
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */

public class JoiningManagedLocalTransactionsTest
    extends
    BaseManagedLocalTransactionsTest<JoiningManagedLocalTransactionsTest.DelegatingTransactionalObjectImpl> {

  @Override
  protected Class<DelegatingTransactionalObjectImpl> getTransactionalObjectType() {
    return DelegatingTransactionalObjectImpl.class;
  }

  @Test
  public void testTransactionSpanningMultipleServiceCalls() {
    long initialTransactions = successfulTransactionCount(injector);
    getTransactionalObject().runOperationTwiceInTxn();

    assertEquals("Transaction not reused for multiple service calls",
        successfulTransactionCount(injector), initialTransactions + 1);
  }

  @Test
  public void testMixingManualAndDeclarativeTransactions() {
    long initialTransactions = successfulTransactionCount(injector);
    getTransactionalObject().runOperationTwiceInManualTxn();

    assertEquals("Transaction not reused for multiple service calls",
        successfulTransactionCount(injector), initialTransactions + 1);
  }

  public static class DelegatingTransactionalObjectImpl implements TransactionalObject {
    private final MethodLevelTransactionalObjectImpl delegate;
    private final EntityManager entityManager;
    private final UnitOfWork unitOfWork;

    @Inject
    public DelegatingTransactionalObjectImpl(MethodLevelTransactionalObjectImpl delegate,
                                             EntityManager entityManager, UnitOfWork unitOfWork) {
      this.delegate = delegate;
      this.entityManager = entityManager;
      this.unitOfWork = unitOfWork;
    }

    @Override
    public EntityTransaction getLastTransaction() {
      return delegate.getLastTransaction();
    }

    @Transactional
    @Override
    public void runOperationInTxn() {
      delegate.runOperationInTxn();
    }

    @Transactional
    public void runOperationTwiceInTxn() {
      delegate.runOperationInTxn();
      delegate.runOperationInTxn();
    }

    public void runOperationTwiceInManualTxn() {
      unitOfWork.begin();
      entityManager.getTransaction().begin();
      delegate.runOperationInTxn();
      delegate.runOperationInTxn();
      entityManager.getTransaction().commit();
      unitOfWork.end();
    }

    @Transactional
    @Override
    public void runOperationInTxnThrowingUnchecked() {
      delegate.runOperationInTxnThrowingUnchecked();
    }

    @Transactional(rollbackOn = IOException.class, ignore = FileNotFoundException.class)
    @Override
    public void runOperationInTxnThrowingCheckedException() throws IOException {
      delegate.runOperationInTxnThrowingCheckedException();
    }

    @Transactional(rollbackOn = IOException.class)
    @Override
    public void runOperationInTxnThrowingChecked() throws IOException {
      delegate.runOperationInTxnThrowingChecked();
    }
  }

  public static class MethodLevelTransactionalObjectImpl extends TransactionalObjectImpl {
    private final EntityManager session;

    @Inject
    public MethodLevelTransactionalObjectImpl(EntityManager em) {
      this.session = em;
    }

    @Override
    protected EntityManager getSession() {
      return session;
    }

    @Transactional
    @Override
    public void runOperationInTxn() {
      super.runOperationInTxn();
    }

    @Transactional(rollbackOn = IOException.class)
    @Override
    public void runOperationInTxnThrowingChecked() throws IOException {
      super.runOperationInTxnThrowingChecked();
    }

    @Transactional
    @Override
    public void runOperationInTxnThrowingUnchecked() {
      super.runOperationInTxnThrowingUnchecked();
    }

    @Transactional(rollbackOn = IOException.class, ignore = FileNotFoundException.class)
    @Override
    public void runOperationInTxnThrowingCheckedException() throws IOException {
      super.runOperationInTxnThrowingCheckedException();
    }
  }
}
