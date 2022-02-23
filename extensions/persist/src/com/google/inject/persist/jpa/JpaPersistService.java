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
import com.google.inject.persist.PersistService;
import java.util.Map;
import javax.annotation.Nullable;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

/**
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */
@Singleton
class JpaPersistService implements PersistService, Provider<EntityManagerFactory> {

  private final String persistenceUnitName;
  private final Map<?, ?> persistenceProperties;

  @Inject
  public JpaPersistService(
      @Jpa String persistenceUnitName, @Nullable @Jpa Map<?, ?> persistenceProperties) {
    this.persistenceUnitName = persistenceUnitName;
    this.persistenceProperties = persistenceProperties;
  }

  private EntityManagerFactory emFactory;

  @Override
  public synchronized void start() {
    Preconditions.checkState(null == emFactory, "Persistence service was already initialized.");
    this.emFactory =
        Persistence.createEntityManagerFactory(persistenceUnitName, persistenceProperties);
  }

  @Override
  public synchronized void stop() {
    try {
      Preconditions.checkState(emFactory != null, "Persistence service never started.");
      Preconditions.checkState(emFactory.isOpen(), "Persistence service was already shut down.");
      this.emFactory.close();
    } finally {
      this.emFactory = null;
    }
  }

  @Override
  public synchronized EntityManagerFactory get() {
    Preconditions.checkState(null != emFactory, "Persistence service not initialized.");
    return this.emFactory;
  }
}
