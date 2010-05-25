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
import com.google.inject.internal.Preconditions;
import com.google.inject.persist.WorkManager;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

/**
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */
@Singleton
class EntityManagerProvider implements Provider<EntityManager>, WorkManager {
  private final Provider<EntityManagerFactory> emFactoryProvider;
  private final ThreadLocal<EntityManager> entityManager = new ThreadLocal<EntityManager>();

  @Inject
  public EntityManagerProvider(Provider<EntityManagerFactory> emFactoryProvider) {
    this.emFactoryProvider = emFactoryProvider;
  }

  public EntityManager get() {
    if (!isWorking()) {
      begin();
    }

    EntityManager em = entityManager.get();
    Preconditions.checkState(null != em, "Requested EntityManager outside work unit. "
        + "Try calling WorkManager.begin() first, or use a PersistenceFilter if you "
        + "are inside a servlet environment.");

    return em;
  }

  public boolean isWorking() {
    return entityManager.get() != null;
  }

  public void begin() {
    Preconditions.checkState(null == entityManager.get(),
        "Work already begun on this thread. Looks like you have called WorkManager.begin() twice"
         + " without a balancing call to end() in between.");

    entityManager.set(emFactoryProvider.get().createEntityManager());
  }

  public void end() {
    EntityManager em = entityManager.get();

    // Let's not penalize users for calling end multiple times.
    if (null == em) {
      return;
    }

    em.close();
    entityManager.remove();
  }
}
