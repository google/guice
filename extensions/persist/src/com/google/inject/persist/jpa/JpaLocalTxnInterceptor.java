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

import com.google.inject.Inject;
import com.google.inject.persist.PersistModule;
import com.google.inject.persist.Transactional;
import com.google.inject.persist.UnitOfWork;
import com.google.inject.persist.WorkManager;
import java.lang.reflect.Method;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */
class JpaLocalTxnInterceptor implements MethodInterceptor {
  @Inject @PersistModule.Persist
  private final UnitOfWork unitOfWork = null;

  @Inject // Dirty hack =(
  private final EntityManagerProvider emProvider = null;

  @Inject // Dirty hack =(
  private final WorkManager workManager = null;

  @Transactional
  private static class Internal {}


  public Object invoke(MethodInvocation methodInvocation) throws Throwable {

    // Should we start a unit of work?
    if (!emProvider.isWorking() && UnitOfWork.TRANSACTION.equals(unitOfWork)) {
      emProvider.begin();
    }

    Transactional transactional = readTransactionMetadata(methodInvocation);
    EntityManager em = this.emProvider.get();

    // Allow joining of transactions if there is an enclosing @Transactional method.
    if (em.getTransaction().isActive()) {
      return methodInvocation.proceed();
    }

    //start txn
    final EntityTransaction txn = em.getTransaction();
    txn.begin();

    Object result;
    try {
      result = methodInvocation.proceed();

    } catch (Exception e) {
      //commit transaction only if rollback didnt occur
      if (rollbackIfNecessary(transactional, e, txn)) {
        txn.commit();
      }

      //propagate whatever exception is thrown anyway
      throw e;
    } finally {
      // Close the em if necessary (guarded so this code doesn't run unless catch fired).
      if (isUnitOfWorkTransaction() && !txn.isActive()) {
        workManager.end();
      }
    }

    //everything was normal so commit the txn (do not move into try block above as it
    //  interferes with the advised method's throwing semantics)
    try {
      txn.commit();
    } finally {
      //close the em if necessary
      if (isUnitOfWorkTransaction()) {
        workManager.end();
      }
    }

    //or return result
    return result;
  }

  private Transactional readTransactionMetadata(MethodInvocation methodInvocation) {
    Transactional transactional;
    Method method = methodInvocation.getMethod();
    Class<?> targetClass = methodInvocation.getThis().getClass();

    if (method.isAnnotationPresent(Transactional.class)) {
      transactional = method.getAnnotation(Transactional.class);
    } else if (targetClass.isAnnotationPresent(Transactional.class)) {
      // If none on method, try the class.
      transactional = targetClass.getAnnotation(Transactional.class);
    } else {
      // If there is no transactional annotation present, use the default
      transactional = Internal.class.getAnnotation(Transactional.class);
    }

    return transactional;
  }

  /**
   * @param transactional The metadata annotaiton of the method
   * @param e The exception to test for rollback
   * @param txn A JPA Transaction to issue rollbacks on
   * @return returns True if rollback DID NOT HAPPEN (i.e. if commit should continue).
   */
  private boolean rollbackIfNecessary(Transactional transactional, Exception e,
      EntityTransaction txn) {
    boolean commit = true;

    //check rollback clauses
    for (Class<? extends Exception> rollBackOn : transactional.rollbackOn()) {

      //if one matched, try to perform a rollback
      if (rollBackOn.isInstance(e)) {
        commit = false;

        //check exceptOn clauses (supercedes rollback clause)
        for (Class<? extends Exception> exceptOn : transactional.exceptOn()) {
          //An exception to the rollback clause was found, DON'T rollback
          // (i.e. commit and throw anyway)
          if (exceptOn.isInstance(e)) {
            commit = true;
            break;
          }
        }

        //rollback only if nothing matched the exceptOn check
        if (!commit) {
          txn.rollback();
        }
        //otherwise continue to commit

        break;
      }
    }

    return commit;
  }

  private boolean isUnitOfWorkTransaction() {
    return this.unitOfWork == UnitOfWork.TRANSACTION;
  }
}
