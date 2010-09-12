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

import com.google.inject.Singleton;
import com.google.inject.internal.util.Preconditions;
import com.google.inject.persist.PersistModule;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.UnitOfWork;
import com.google.inject.util.Providers;
import java.util.Properties;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * JPA provider for guice persist.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public final class JpaPersistModule extends PersistModule {
  private final String jpaUnit;

  public JpaPersistModule(String jpaUnit) {
    Preconditions.checkArgument(null != jpaUnit && !jpaUnit.isEmpty(),
        "JPA unit name must be a non-empty string.");
    this.jpaUnit = jpaUnit;
  }

  private Properties properties;
  private MethodInterceptor transactionInterceptor;

  @Override protected void configurePersistence() {
    bindConstant().annotatedWith(Jpa.class).to(jpaUnit);

    if (null != properties) {
      bind(Properties.class).annotatedWith(Jpa.class).toInstance(properties);
    } else {
      bind(Properties.class).annotatedWith(Jpa.class)
          .toProvider(Providers.<Properties>of(null));
    }

    bind(JpaPersistService.class).in(Singleton.class);

    bind(PersistService.class).to(JpaPersistService.class);
    bind(UnitOfWork.class).to(JpaPersistService.class);
    bind(EntityManager.class).toProvider(JpaPersistService.class);
    bind(EntityManagerFactory.class)
        .toProvider(JpaPersistService.EntityManagerFactoryProvider.class);

    transactionInterceptor = new JpaLocalTxnInterceptor();
    requestInjection(transactionInterceptor);
  }

  @Override protected MethodInterceptor getTransactionInterceptor() {
    return transactionInterceptor;
  }

  /**
   * Configures the JPA persistence provider with a set of properties.
   * 
   * @param properties A set of name value pairs that configure a JPA persistence
   * provider as per the specification.
   */
  public JpaPersistModule properties(Properties properties) {
    this.properties = properties;
    return this;
  }
}
