package com.google.inject.persist;

import java.util.Properties;

/**
 * @author dhanji@google.com (Dhanji R. Prasanna)
 */
public interface PersistenceProviderBinder {
  /**
   * @param unitName The name of the JPA unit you specified in persistence.xml
   */
  void usingJpa(String unitName);

  /**
   * @param unitName The name of the JPA unit you specified in persistence.xml
   * @param properties A set of vendor configuration properties as key/value pairs.
   */
  void usingJpa(String unitName, Properties properties);
}
