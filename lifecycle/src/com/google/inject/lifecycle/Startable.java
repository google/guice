package com.google.inject.lifecycle;

/**
 * A convenience lifecycle interface. Any class the exposes
 * this interface will be started if the lifecycle module is
 * installed. The lifecycle module guarantees that the order
 * in which Startables are called will match the order in which
 * modules are installed.
 *
 * All instances that wish to use startable *must* be bound as
 * singletons.
 *
 * @author dhanji@google.com (Dhanji R. Prasanna)
 */
public interface Startable {
  /**
   * Called once the injector has been created completely.
   * In PRODUCTION mode, this means when all singletons
   * have been instantiated.
   */
  void start();
}
