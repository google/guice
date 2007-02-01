// Copyright 2006 Google Inc. All Rights Reserved.

package com.google.inject;

/**
 * @author crazybob@google.com (Bob Lee)
*/
class InternalFactoryToFactoryAdapter<T> implements InternalFactory<T> {

  private final Factory<? extends T> factory;

  public InternalFactoryToFactoryAdapter(Factory<? extends T> factory) {
    this.factory = factory;
  }
  
  public T get(InternalContext context) {
    return factory.get();
  }

  public String toString() {
    return factory.toString();
  }
}
