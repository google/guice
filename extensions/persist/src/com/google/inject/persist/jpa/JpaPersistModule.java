package com.google.inject.persist.jpa;

import com.google.inject.Singleton;
import com.google.inject.internal.util.Preconditions;
import com.google.inject.persist.PersistModule;
import com.google.inject.persist.WorkManager;
import com.google.inject.util.Providers;
import java.lang.annotation.Annotation;
import java.util.Properties;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * JPA provider for guice persist.
 */
public final class JpaPersistModule extends PersistModule {
  private final String jpaUnit;

  public JpaPersistModule(String jpaUnit) {
    Preconditions.checkArgument(null != jpaUnit && !jpaUnit.isEmpty(),
        "Specified JPA unit name must be a non-empty string.");
    this.jpaUnit = jpaUnit;
  }

  public JpaPersistModule(String jpaUnit, Class<? extends Annotation> unitOfWork) {
    this(jpaUnit);
    setUnitOfWork(unitOfWork);
  }

  public JpaPersistModule(String jpaUnit, Class<? extends Annotation> unitOfWork,
      Class<? extends Annotation> transactional) {
    this(jpaUnit);
    setUnitOfWork(unitOfWork);
    setTransactional(transactional);
  }

  private Properties properties;
  private final MethodInterceptor transactionInterceptor = new JpaLocalTxnInterceptor();

  @Override protected void configurePersistence() {
    bindConstant().annotatedWith(PersistModule.Persist.class).to(jpaUnit);

    if (null != properties) {
      bind(Properties.class).annotatedWith(PersistModule.Persist.class).toInstance(properties);
    } else {
      bind(Properties.class).annotatedWith(PersistModule.Persist.class)
          .toProvider(Providers.<Properties>of(null));
    }

    bind(EntityManagerProvider.class).in(Singleton.class);
    bind(EntityManagerProvider.EntityManagerFactoryProvider.class).in(Singleton.class);

    bind(EntityManager.class).toProvider(EntityManagerProvider.class);
    bind(EntityManagerFactory.class)
        .toProvider(EntityManagerProvider.EntityManagerFactoryProvider.class);

    requestInjection(transactionInterceptor);
  }

  @Override protected Class<? extends WorkManager> getWorkManager() {
    return EntityManagerProvider.class;
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
