package com.google.inject.spi;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;

/**
 * This interface allows to hijack Just-In-Time bindings logic.
 *
 * All the implementations of this interface which are present in current injector
 * are going to be queried (in unpredictable order) for an instance of any JIT binding
 * before any other lookups.
 *
 * @author pshirshov@gmail.com (Pavel Shirshov)
 * @since 4.1.1
 */
public interface CustomJitProvider {
  <T> boolean supports(Key<T> key, Injector injector);

  <T> T provide(Key<T> key, Injector injector, Errors errors, Dependency<?> dependency) throws ErrorsException;
}
