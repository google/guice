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

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/** @author Dhanji R. Prasanna (dhanji@gmail.com) */
class JpaTransactionInterceptor implements MethodInterceptor {

  private Provider<LocalTransaction> transactionProvider;

  public JpaTransactionInterceptor(Provider<LocalTransaction> transactionProvider) {
    this.transactionProvider = transactionProvider;
  }

  @Override
  public Object invoke(MethodInvocation methodInvocation) throws Throwable {
    LocalTransaction transaction = transactionProvider.get();
    transaction.begin();
    try {
      return methodInvocation.proceed();
    } catch (Exception exception) {
      TransactionConfig config = TransactionConfig.from(methodInvocation);
      if (!config.shouldContinueOn(exception)) {
        transaction.markRollbackOnly();
      }
      throw exception;
    } finally {
      transaction.end();
    }
  }

  private static class TransactionConfig {
    private final Class<? extends Exception>[] rollbackOn;
    private final Class<? extends Exception>[] ignore;

    @Transactional
    private static class DefaultTransactionConfigHolder {}

    private TransactionConfig(Class<? extends Exception>[] rollbackOn, Class<? extends Exception>[] ignore) {
      this.rollbackOn = rollbackOn;
      this.ignore = ignore;
    }

    static TransactionConfig from(MethodInvocation methodInvocation) {
      Transactional annotation = readTransactionMetadata(methodInvocation);
      return new TransactionConfig(annotation.rollbackOn(), annotation.ignore());
    }

    boolean shouldContinueOn(Exception e) {
      return noMatchInOnRollback(e) || matchInIgnore(e);
    }

    private boolean matchInIgnore(Exception e) {
      return Arrays.stream(ignore)
          .anyMatch(ignored -> ignored.isAssignableFrom(e.getClass()));
    }

    private boolean noMatchInOnRollback(Exception e) {
      return Arrays.stream(rollbackOn)
          .noneMatch(rollbackable -> rollbackable.isAssignableFrom(e.getClass()));
    }

    // TODO(user): Cache this method's results.
    private static Transactional readTransactionMetadata(MethodInvocation methodInvocation) {
      Transactional transactional;
      Method method = methodInvocation.getMethod();
      Class<?> targetClass = methodInvocation.getThis().getClass();

      transactional = method.getAnnotation(Transactional.class);
      if (null == transactional) {
        // If none on method, try the class.
        transactional = targetClass.getAnnotation(Transactional.class);
      }
      if (null == transactional) {
        // If there is no transactional annotation present, use the default
        transactional = DefaultTransactionConfigHolder.class.getAnnotation(Transactional.class);
      }

      return transactional;
    }
  }
}
