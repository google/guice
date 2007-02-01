// Copyright 2006 Google Inc. All Rights Reserved.

package com.google.inject;

/**
 * @author crazybob@google.com (Bob Lee)
*/
class FactoryToInternalFactoryAdapter<T> implements Factory<T> {

  private final ContainerImpl container;

  private final InternalFactory<? extends T> internalFactory;

  public FactoryToInternalFactoryAdapter(ContainerImpl container,
      InternalFactory<? extends T> internalFactory) {
    this.container = container;
    this.internalFactory = internalFactory;
  }

  public T get() {
    return container.callInContext(
        new ContainerImpl.ContextualCallable<T>() {
      public T call(InternalContext context) {
        return internalFactory.get(context);
      }
    });
  }

  public String toString() {
    return internalFactory.toString();
  }
}
