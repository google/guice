package com.google.inject.binder;

import com.google.inject.Key;
import com.google.inject.TypeLiteral;

/**
 * Javadoc.
 *
 */
public interface LinkedBindingBuilder<T> {

  /**
   * Links to another binding with the given key.
   */
  void to(Key<? extends T> destination);

  /**
   * Links to another binding with the given type.
   */
  void to(Class<? extends T> destination);

  /**
   * Links to another binding with the given type.
   */
  void to(TypeLiteral<? extends T> destination);
}
