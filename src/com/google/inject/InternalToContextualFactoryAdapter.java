// Copyright 2006 Google Inc. All Rights Reserved.

package com.google.inject;

/**
 * @author crazybob@google.com (Bob Lee)
*/
class InternalToContextualFactoryAdapter<T> 
    implements InternalFactory<T> {

  private final ContextualFactory<? extends T> factory;

  public InternalToContextualFactoryAdapter(
      ContextualFactory<? extends T> factory) {
    this.factory = factory;
  }

  public T get(InternalContext context) {
    return factory.get(context.getExternalContext());
  }

  public String toString() {
    return factory.toString();
  }
}
