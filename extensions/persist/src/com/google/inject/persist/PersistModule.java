package com.google.inject.persist;

import com.google.inject.AbstractModule;
import com.google.inject.BindingAnnotation;
import com.google.inject.internal.Preconditions;
import com.google.inject.persist.jpa.InternalJpaModule;
import com.google.inject.servlet.ServletModule;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Properties;

/**
 * Install this module to add guice-persist library support for JPA persistence providers. This
 * module *MUST* be installed before any filters are registered
 *
 * @author dhanji@google.com (Dhanji R. Prasanna)
 */
public class PersistModule extends AbstractModule implements PersistenceProviderBinder {
  private UnitOfWork unitOfWork;
  private String jpaUnit;
  private Properties properties;

  @Override
  protected final void configure() {
    configurePersistence();

    // Really post conditions, but whatevah.
    Preconditions.checkArgument(null != unitOfWork,
        "Must specify a unit of work in the PersistModule.");
    Preconditions.checkArgument(null != jpaUnit,
        "Must specify the name of a JPA unit in the PersistModule.");

    install(new InternalJpaModule(unitOfWork, jpaUnit, properties));

    // Servlet functionality requested.
    if (UnitOfWork.REQUEST.equals(unitOfWork)) {
      install(new ServletModule() {
        @Override
        protected void configureServlets() {
          filter("/*").through(PersistenceFilter.class);
        }
      });
    }
  }

  protected void configurePersistence() {
    // For users to override
  }

  protected final PersistenceProviderBinder workAcross(UnitOfWork unitOfWork) {
    this.unitOfWork = unitOfWork;
    return this;
  }

  public void usingJpa(String unitName) {
    this.jpaUnit = unitName;
  }

  public void usingJpa(String unitName, Properties properties) {
    this.jpaUnit = unitName;
    this.properties = properties;
  }

  /**
   * @author dhanji@google.com (Dhanji R. Prasanna)
   */
  @Retention(RetentionPolicy.RUNTIME)
  @BindingAnnotation
  public static @interface Persist {
  }
}
