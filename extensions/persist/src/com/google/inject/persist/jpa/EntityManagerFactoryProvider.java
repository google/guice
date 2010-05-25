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
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.internal.Nullable;
import com.google.inject.internal.Preconditions;
import com.google.inject.persist.PersistModule;
import com.google.inject.persist.PersistenceService;
import java.util.Properties;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

/**
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */
@Singleton class EntityManagerFactoryProvider extends PersistenceService
    implements Provider<EntityManagerFactory> {
  private final String persistenceUnitName;
  private final Properties persistenceProperties;

  private volatile EntityManagerFactory emFactory;

  @Inject
  public EntityManagerFactoryProvider(@PersistModule.Persist String persistenceUnitName,
      @Nullable @PersistModule.Persist Properties persistenceProperties) {
    this.persistenceUnitName = persistenceUnitName;
    this.persistenceProperties = persistenceProperties;
  }

  public EntityManagerFactory get() {
    assert null != emFactory;
    return emFactory;
  }

  public synchronized void start() {
    Preconditions.checkState(null == emFactory, "Persistence service was already initialized.");

    if (null != persistenceProperties) {
      this.emFactory = Persistence
          .createEntityManagerFactory(persistenceUnitName, persistenceProperties);
    } else {
      this.emFactory = Persistence.createEntityManagerFactory(persistenceUnitName);
    }
  }

  public synchronized void shutdown() {
    Preconditions.checkState(emFactory.isOpen(), "Persistence service was already shut down.");
    emFactory.close();
  }
}
