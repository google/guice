package com.google.inject.introspect;

import com.google.inject.Key;
import java.util.Set;

/**
 * The resolver is used by the Injector to, given an injection request (key),
 * find the appropriate instructions for obtaining instances to fulfill that
 * request.  It is exposed for use by tools.
 *
 * @author Kevin Bourrillion (kevinb9n@gmail.com)
 */
public interface Resolver {

  /**
   * Returns the Implementation that the given key resolves to
   * @throws IllegalArgumentException if the key cannot be resolved
   */
  <T> Implementation<T> resolve(Key<? super T> key);

  /**
   * Returns all the keys that can be resolved by this injector.
   */
  Set<Key<?>> allKeys();

  /**
   * Returns all the implementations known to this injector.
   */
  Set<Implementation<?>> resolveAll();
}
