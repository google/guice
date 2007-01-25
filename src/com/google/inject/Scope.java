// Copyright 2006 Google Inc. All Rights Reserved.

package com.google.inject;

/**
 * A scope which bound objects can reside in. Add a new scope using {@link
 * com.google.inject.ContainerBuilder#put(String, Scope)} and reference it from
 * bindings using its name.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public interface Scope {

  /**
   * Scopes a factory. The returned factory returns objects from this scope.
   * If an object does not exist in this scope, the factory can use the given
   * creator to create one.
   *
   * @param key binding key
   * @param creator creates new instances as needed
   * @return a new factory which only delegates to the given factory when an
   *  instance of the requested object doesn't already exist in the scope
   */
  public <T> Factory<T> scope(Key<T> key, Factory<T> creator);
}
