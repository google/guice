package com.google.inject.spi;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;
import com.google.inject.spi.Dependency;

public interface CustomJitProvider {
  <T> boolean supports(Key<T> key, Injector injector);

  <T> T provide(Key<T> key, Injector internalFactory, Errors errors, Dependency<?> dependency) throws ErrorsException;
}
