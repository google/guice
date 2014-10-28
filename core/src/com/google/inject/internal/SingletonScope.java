package com.google.inject.internal;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Scope;
import com.google.inject.Scopes;
import com.google.inject.Singleton;

/**
 * One instance per {@link Injector}. Also see {@code @}{@link Singleton}.
 */
public class SingletonScope implements Scope {

  /** A sentinel value representing null. */
  private static final Object NULL = new Object();

  /**
   * Lock to use for new instances creation. This allows a per-root-Injector singleton lock,
   * instead of a global lock across the JVM. Is set only during call to {@link #scope}.
   *
   * This is necessary because users have coded to a single {@link Scopes#SINGLETON} instance, 
   * and we cannot change that.  Additionally, we can't reference the injector from a Key or
   * Provider (the only variables available to the {@link #scope} method).  Therefore, we rely
   * on the injector implementation to explicitly set/unset the lock surrounding
   * creation of the Provider the scope creates.
   *
   * @see {@link Scoping#scope(Key, InjectorImpl, InternalFactory, Object, Scoping)} for details.
   */
  static final ThreadLocal<Object> singletonCreationPerRootInjectorLock =
      new ThreadLocal<Object>();

  public <T> Provider<T> scope(final Key<T> key, final Provider<T> creator) {
    // lock is referenced from anonymous class instance
    final Object rootInjectorLock = singletonCreationPerRootInjectorLock.get();
    if (rootInjectorLock == null) {
      throw new OutOfScopeException("Singleton scope should only be used from Injector");
    }
    return new Provider<T>() {
      /*
       * The lazily initialized singleton instance. Once set, this will either have type T or will
       * be equal to NULL.
       */
      private volatile Object instance;

      // DCL on a volatile is safe as of Java 5, which we obviously require.
      @SuppressWarnings("DoubleCheckedLocking")
      public T get() {
        if (instance == null) {
          /*
           * Use a pretty coarse lock. We don't want to run into deadlocks
           * when two threads try to load circularly-dependent objects.
           * Maybe one of these days we will identify independent graphs of
           * objects and offer to load them in parallel.
           *
           * This block is re-entrant for circular dependencies.
           */
          synchronized (rootInjectorLock) {
            if (instance == null) {
              T provided = creator.get();

              // don't remember proxies; these exist only to serve circular dependencies
              if (Scopes.isCircularProxy(provided)) {
                return provided;
              }

              Object providedOrSentinel = (provided == null) ? NULL : provided;
              if (instance != null && instance != providedOrSentinel) {
                throw new ProvisionException(
                    "Provider was reentrant while creating a singleton");
              }

              instance = providedOrSentinel;
            }
          }
        }

        Object localInstance = instance;
        // This is safe because instance has type T or is equal to NULL
        @SuppressWarnings("unchecked")
        T returnedInstance = (localInstance != NULL) ? (T) localInstance : null;
        return returnedInstance;
      }

      @Override
      public String toString() {
        return String.format("%s[%s]", creator, Scopes.SINGLETON);
      }
    };
  }

  @Override public String toString() {
    return "Scopes.SINGLETON";
  }
}
