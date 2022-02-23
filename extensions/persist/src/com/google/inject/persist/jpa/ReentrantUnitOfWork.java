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

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.persist.UnitOfWork;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.logging.Logger;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

@Singleton
class ReentrantUnitOfWork implements UnitOfWork, Provider<EntityManager> {

  private static final Logger logger = Logger.getLogger(ReentrantUnitOfWork.class.getName());

  private static final ThreadLocal<Integer> reentrancyDepth = ThreadLocal.withInitial(() -> 0);
  private static final ThreadLocal<EntityManager> boundEntityManager = new ThreadLocal<>();
  private static final ThreadLocal<Boolean> startedImplicitly =
      ThreadLocal.withInitial(() -> false);
  private final Provider<EntityManagerFactory> entityManagerFactory;
  private final boolean implicitUnitOfWork;

  @Inject
  ReentrantUnitOfWork(Provider<EntityManagerFactory> entityManagerFactory,
                      UnitOfWorkConfig config) {
    this.entityManagerFactory = entityManagerFactory;
    this.implicitUnitOfWork = config.implicit;
  }

  @Override
  public EntityManager get() {
    return (EntityManager) Proxy.newProxyInstance(
        getClass().getClassLoader(),
        new Class[] {EntityManager.class},
        (ignored, method, params) ->
            delegateToThreadBoundEntityManager(method,
                params)); // even when requested by a singleton
  }

  @Override
  public void begin() {
    reentrancyDepth.set(reentrancyDepth.get() + 1);
  }

  @Override
  public void end() {
    if (!isUnitOfWorkInProgress()) {
      logger.warning("Trying to close a unit of work which was never started, "
          + "make sure your code has matching begin() and end() calls");
    }
    reentrancyDepth.set(reentrancyDepth.get() - 1);
    // in case someone calls begin() and end() without ever having requested an EntityManager
    if (!isUnitOfWorkInProgress() && isEntityManagerBound()) {
      finishUnitOfWork();
    }
  }

  protected void finishUnitOfWork() {
    try {
      boundEntityManager.get().close();
    } finally {
      boundEntityManager.remove();
    }
  }

  protected EntityManager lazyGet() {
    if (!isEntityManagerBound()) {
      boundEntityManager.set(entityManagerFactory.get().createEntityManager());
    }
    return boundEntityManager.get();
  }

  private boolean isUnitOfWorkInProgress() {
    return reentrancyDepth.get() > 0;
  }

  private boolean isEntityManagerBound() {
    return boundEntityManager.get() != null;
  }

  private Object delegateToThreadBoundEntityManager(Method method, Object[] params)
      throws IllegalAccessException, InvocationTargetException {
    ensureUnitOfWork();
    if (isClose(method)) {
      return handleEntityManagerCloseMethod();
    } else {
      return method.invoke(lazyGet(), params);
    }
  }

  private Void handleEntityManagerCloseMethod() {
    if (implicitUnitOfWork && startedImplicitly.get()) {
      end();
      return null;
    } else {
      throw new IllegalStateException("This EntityManager is managed by the UnitOfWork.");
    }
  }

  private boolean isClose(Method method) {
    return method.getName().equals("close");
  }

  private void ensureUnitOfWork() {
    boolean unitOfWorkInProgress = isUnitOfWorkInProgress();
    Preconditions.checkState(
        implicitUnitOfWork || unitOfWorkInProgress,
        "Requested EntityManager outside work unit. "
            + "Try calling UnitOfWork.begin() first, or use a PersistFilter if you "
            + "are inside a servlet environment.");
    if (!unitOfWorkInProgress) {
      startedImplicitly.set(true);
      begin();
    }
  }

  static class UnitOfWorkConfig {
    private final boolean implicit;

    UnitOfWorkConfig(boolean implicit) {
      this.implicit = implicit;
    }
  }
}
