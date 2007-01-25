// Copyright 2006 Google Inc. All Rights Reserved.

package com.google.inject;

/**
 * Singleton scope. Returns one instance per {@link Container}.
 *
 * @author crazybob@google.com (Bob Lee)
 */
class SingletonScope implements Scope {

  static final Scope INSTANCE = new SingletonScope();

  private SingletonScope() {}

  public <T> Factory<T> scope(Key<T> key, final Factory<T> creator) {
    return new Factory<T>() {

      T instance;

      public T get() {
        // Use a pretty coarse lock. We don't want to run into deadlocks when
        // two threads try to load circularly-dependent singletons.
        // Maybe one of these days we will identify independent graphs of
        // singletons and offer to load them in parallel.
        synchronized (Container.class) {
          if (instance == null) {
            instance = creator.get();
          }
          return instance;
        }
      }

      public String toString() {
        return creator.toString();
      }
    };
  }
}
