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

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;

/**
 * Provides JPA RESOURCE_LOCAL transaction management
 *
 * @author Krzysztof Sierszeń (krzysztof.sierszen@digitalnewagency.com)
 */
@Singleton
class LocalTransaction extends ReentrantUnitOfWork {

  /**
   * An independent counter for transaction reentrancy depth.
   *
   * <p>This allows for transactions to be opened and closed while an overreaching unit of work remains open
   * (i.e. an equivalent of {@code @PersistenceContext(scope = EXTENDED)}). </p>
   */
  private static final ThreadLocal<Integer> transactionReentrancyDepth =
      ThreadLocal.withInitial(() -> 0);

  /**
   * Whether the first-level call was done with a transaction already started from outside.
   *
   * <p>In such a scenario, we will just flip this switch on and off instead of actually starting/committing the transaction.</p>
   */
  private static final ThreadLocal<Boolean> externalTransaction =
      ThreadLocal.withInitial(() -> false);

  @Inject
  LocalTransaction(Provider<EntityManagerFactory> entityManagerFactory, UnitOfWorkConfig config) {
    super(entityManagerFactory, config);
  }

  @Override
  public void begin() {
    super.begin();
    if (!isReentrantCall()) {
      EntityTransaction currentTransaction = getEntityTransaction();
      if (!currentTransaction.isActive()) {
        currentTransaction.begin();
      } else { // transaction started externally, presumably by the user calling em.getTransaction().begin() directly
        externalTransaction.set(true);
      }
    }
    incrementTransactionDepth();
  }

  @Override
  public void end() {
    try {
      decrementTransactionDepth();
      if (isReentrantCall()) {
        return;
      }
      if (externalTransaction.get()) {
        externalTransaction.set(false);
      } else {
        finalizeTransaction();
      }
    } finally {
      super.end();
    }
  }

  private void finalizeTransaction() {
    EntityTransaction currentTransaction = getEntityTransaction();
    if (currentTransaction.getRollbackOnly()) {
      currentTransaction.rollback();
    } else if (currentTransaction.isActive()) {
      currentTransaction.commit();
    }
  }

  public void markRollbackOnly() {
    getEntityTransaction().setRollbackOnly();
  }

  private EntityTransaction getEntityTransaction() {
    EntityManager entityManager = lazyGet();
    return entityManager.getTransaction();
  }

  private boolean isReentrantCall() {
    return transactionReentrancyDepth.get() != 0;
  }

  private void incrementTransactionDepth() {
    transactionReentrancyDepth.set(transactionReentrancyDepth.get() + 1);
  }

  private void decrementTransactionDepth() {
    transactionReentrancyDepth.set(transactionReentrancyDepth.get() - 1);
  }
}
