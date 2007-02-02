// Copyright 2006 Google Inc. All Rights Reserved.

package com.google.inject.spi;

import java.lang.reflect.Constructor;

/**
 * Creates {@link ConstructionProxy} instances.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public interface ConstructionProxyFactory {

  /**
   * Gets a construction proxy for the given constructor.
   */
  <T> ConstructionProxy<T> get(Constructor<T> constructor);
}
