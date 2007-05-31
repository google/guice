package com.google.inject.introspect;

import com.google.inject.Key;

/**
 * Javadoc.
 *
 * @author Kevin Bourrillion (kevinb9n@gmail.com)
 */
public interface Dependency<T> {

  Implementation<?> getDependent();

  Key<T> getKey();

  boolean usesProviderInjection();
}
