package com.google.inject.persist.jpa;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.persist.PersistModule;
import com.google.inject.persist.PersistenceService;
import com.google.inject.persist.Transactional;
import com.google.inject.persist.UnitOfWork;
import com.google.inject.persist.WorkManager;
import com.google.inject.util.Providers;
import java.util.Properties;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import static com.google.inject.matcher.Matchers.annotatedWith;
import static com.google.inject.matcher.Matchers.any;

/**
 * @author dhanji@google.com (Dhanji R. Prasanna)
 * @internal
 */
public final class InternalJpaModule extends AbstractModule {
  private final UnitOfWork unitOfWork;
  private final String jpaUnit;
  private final Properties properties;

  public InternalJpaModule(UnitOfWork unitOfWork, String jpaUnit, Properties properties) {
    this.unitOfWork = unitOfWork;
    this.jpaUnit = jpaUnit;
    this.properties = properties;
  }

  @Override
  protected void configure() {
    bindConstant().annotatedWith(PersistModule.Persist.class).to(jpaUnit);
    bindConstant().annotatedWith(PersistModule.Persist.class).to(unitOfWork);

    if (null != properties) {
      bind(Properties.class).annotatedWith(PersistModule.Persist.class).toInstance(properties);
    } else {
      bind(Properties.class).annotatedWith(PersistModule.Persist.class)
          .toProvider(Providers.<Properties>of(null));      
    }

    bind(EntityManagerProvider.class).in(Singleton.class);
    bind(EntityManagerFactoryProvider.class).in(Singleton.class);

    bind(EntityManager.class).toProvider(EntityManagerProvider.class);
    bind(EntityManagerFactory.class).toProvider(EntityManagerFactoryProvider.class);

    bind(WorkManager.class).to(EntityManagerProvider.class);
    bind(PersistenceService.class).to(EntityManagerFactoryProvider.class);

    JpaLocalTxnInterceptor interceptor = new JpaLocalTxnInterceptor();
    requestInjection(interceptor);

    bindInterceptor(any(), annotatedWith(Transactional.class), interceptor);
  }
}
