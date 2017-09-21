package com.google.inject.internal;

// TODO(sameb): Can this be more InternalFactory-like?
public interface ProvisionCallback<T> {
  public T call() throws ErrorsException;
}
