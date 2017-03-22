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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.UnitOfWork;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

/** @author Dhanji R. Prasanna (dhanji@gmail.com) */
@Singleton
class JpaPersistService implements Provider<EntityManager>, UnitOfWork, PersistService {
  private final ThreadLocal<EntityManager> entityManager = new ThreadLocal<>();

  private final String persistenceUnitName;
  private final Map<?, ?> persistenceProperties;

  @Inject
  public JpaPersistService(
      @Jpa String persistenceUnitName, @Nullable @Jpa Map<?, ?> persistenceProperties) {
    this.persistenceUnitName = persistenceUnitName;
    this.persistenceProperties = persistenceProperties;
  }

  @Override
  public EntityManager get() {
    if (!isWorking()) {
      begin();
    }

    EntityManager em = entityManager.get();
    Preconditions.checkState(
        null != em,
        "Requested EntityManager outside work unit. "
            + "Try calling UnitOfWork.begin() first, or use a PersistFilter if you "
            + "are inside a servlet environment.");

    return em;
  }

  public boolean isWorking() {
    return entityManager.get() != null;
  }

  @Override
  public void begin() {
    Preconditions.checkState(
        null == entityManager.get(),
        "Work already begun on this thread. Looks like you have called UnitOfWork.begin() twice"
            + " without a balancing call to end() in between.");

    entityManager.set(emFactory.createEntityManager());
  }

  @Override
  public void end() {
    EntityManager em = entityManager.get();

    // Let's not penalize users for calling end() multiple times.
    if (null == em) {
      return;
    }

    try {
      em.close();
    } finally {
      entityManager.remove();
    }
  }

  private volatile EntityManagerFactory emFactory;

  @VisibleForTesting
  synchronized void start(EntityManagerFactory emFactory) {
    this.emFactory = emFactory;
  }

  @Override
  public synchronized void start() {
    Preconditions.checkState(null == emFactory, "Persistence service was already initialized.");

    if (null != persistenceProperties) {
      this.emFactory =
          Persistence.createEntityManagerFactory(persistenceUnitName, persistenceProperties);
    } else {
      this.emFactory = Persistence.createEntityManagerFactory(persistenceUnitName);
    }
  }

  @Override
  public synchronized void stop() {
    Preconditions.checkState(emFactory.isOpen(), "Persistence service was already shut down.");
    emFactory.close();
  }

  @Singleton
  public static class EntityManagerFactoryProvider implements Provider<EntityManagerFactory> {
    private final JpaPersistService emProvider;

    @Inject
    public EntityManagerFactoryProvider(JpaPersistService emProvider) {
      this.emProvider = emProvider;
    }

    @Override
    public EntityManagerFactory get() {
      assert null != emProvider.emFactory;
      return emProvider.emFactory;
    }
  }

  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.PARAMETER)
  private @interface Nullable {}
}
