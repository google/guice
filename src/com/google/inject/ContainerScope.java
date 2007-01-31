// Copyright 2006 Google Inc. All Rights Reserved.

package com.google.inject;

/**
 * Container scope. Returns one instance per {@link Container}.
 *
 * @author crazybob@google.com (Bob Lee)
 */
class ContainerScope implements Scope {

  static final Scope INSTANCE = new ContainerScope();

  private ContainerScope() {}

  public <T> Factory<T> scope(Key<T> key, final Factory<T> creator) {
    return new Factory<T>() {

      private volatile T instance;

      public T get() {
        // Double checked locking improves performance and is safe as of Java 5.
        if (instance == null) {
          // Use a pretty coarse lock. We don't want to run into deadlocks when
          // two threads try to load circularly-dependent objects.
          // Maybe one of these days we will identify independent graphs of
          // objects and offer to load them in parallel.
          synchronized (Container.class) {
            if (instance == null) {
              instance = creator.get();
            }
          }
        }
        return instance;
      }

      public String toString() {
        return creator.toString();
      }
    };
  }
}
