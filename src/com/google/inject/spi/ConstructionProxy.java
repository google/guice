// Copyright 2006 Google Inc. All Rights Reserved.

package com.google.inject.spi;

import java.lang.reflect.InvocationTargetException;

/**
 * Proxies calls to a {@link java.lang.reflect.Constructor} for a class
 * {@code T}.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public interface ConstructionProxy<T> {

  /**
   * Constructs an instance of {@code T} for the given arguments.
   */
  T newInstance(Object... arguments) throws InvocationTargetException;
}
