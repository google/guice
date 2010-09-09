package com.google.inject.persist;

import com.google.inject.AbstractModule;
import com.google.inject.BindingAnnotation;
import com.google.inject.internal.util.Preconditions;
import static com.google.inject.matcher.Matchers.annotatedWith;
import static com.google.inject.matcher.Matchers.any;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * Install this module to add guice-persist library support for JPA persistence
 * providers.
 *
 * @author dhanji@google.com (Dhanji R. Prasanna)
 */
public abstract class PersistModule extends AbstractModule {
  private Class<? extends Annotation> unitOfWork;
  private Class<? extends Annotation> transactional;

  @Override
  protected final void configure() {
    configurePersistence();

    if (null == unitOfWork) {
      // We bind this as the "Default" work manager if no custom unit of work is used.
      bind(WorkManager.class).to(getWorkManager());
      unitOfWork = UnitOfWork.class;
    }
    if (null == transactional) {
      transactional = Transactional.class;
    }

    // NOTE(dhanji): Bind work-specific work manager + transaction interceptors.
    // We permit the default work manager to be bound to both the default work
    // annotation @UnitOfWork, and without any annotation. Default is defined as
    // any persistence module without a custom unit of work annotation. In a single
    // module system, the single module would be the default (most typical apps).
    bind(WorkManager.class).annotatedWith(unitOfWork).to(getWorkManager());
    bindInterceptor(any(), annotatedWith(transactional), getTransactionInterceptor());
  }

  protected abstract void configurePersistence();

  protected abstract Class<? extends WorkManager> getWorkManager();

  protected abstract MethodInterceptor getTransactionInterceptor();

  protected final void setUnitOfWork(Class<? extends Annotation> unitOfWork) {
    Preconditions.checkArgument(null != unitOfWork,
        "Must specify a non-null unit of work.");
    this.unitOfWork = unitOfWork;
  }

  protected final void setTransactional(Class<? extends Annotation> transactional) {
    Preconditions.checkArgument(null != unitOfWork,
        "Must specify a non-null transactional annotation.");
    this.transactional = transactional;
  }

  /**
   * @author dhanji@google.com (Dhanji R. Prasanna)
   */
  @Retention(RetentionPolicy.RUNTIME)
  @BindingAnnotation
  public static @interface Persist {
  }
}
