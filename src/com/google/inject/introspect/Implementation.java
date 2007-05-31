package com.google.inject.introspect;

import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Scope;
import java.lang.reflect.Constructor;
import java.util.Set;

/**
 * Javadoc.
 *
 * @author Kevin Bourrillion (kevinb9n@gmail.com)
 */
public interface Implementation<T> {

  enum ProvisionStrategy {

    /**
     * An instance was provided to Guice by the Module; this instance will be
     * used to fulfill every request.
     */
    INSTANCE,

    /**
     * Guice obtains instances for this implementation by invoking an injectable
     * constructor (either the lone constructor marked with {@code @Inject} or
     * a parameterless constructor).
     */
    CONSTRUCTOR,

    /**
     * Guice invokes a user-supplied Provider to obtain instances for this
     * implementation.
     */
    PROVIDER,

    /**
     * This is a reserved key that Guice provides natively, like Injector or
     * Stage.
     */
    RESERVED,
  }

  /**
   * Returns the strategy Guice will use to provide instances for this
   * implementation.
   */
  ProvisionStrategy getProvisionStrategy();

  /**
   * Returns the constructor Guice will use to obtain instances for this
   * implementation.
   *
   * @throws IllegalStateException if {@link #getProvisionStrategy()} is not
   *     {@link ProvisionStrategy#CONSTRUCTOR}.
   */
  Constructor<? extends T> getInjectableConstructor();

  /**
   * Returns the provider Guice will use to obtain instances for this
   * implementation.  TODO: what about @Provides methods?
   *
   * @throws IllegalStateException if {@link #getProvisionStrategy()} is not
   *     {@link ProvisionStrategy#PROVIDER}.
   */
  Implementation<? extends Provider<? extends T>> getCustomProvider();

  /**
   * Returns the scope applied to this implementation, or null if there is none.
   */
  Scope getScope();

  /**
   * Returns all keys which resolve to this implementation.
   */
  Set<Key<? super T>> getKeys();

  /**
   * Returns a Dependency instance for each dependency this implementation has;
   * that is, "everyone we depend on."
   */
  Set<Dependency<?>> getDependencies();

  /**
   * Returns a Dependency instance for each dependent this implementation has;
   * that is, "everyone who depends on us."
   */
  Set<Dependency<?>> getDependents();
}
