package com.google.inject.binder;

/**
 * Javadoc.
 */
public interface ConstantBindingBuilder {

  /**
   * Binds constant to the given value.
   */
  void to(String value);

  /**
   * Binds constant to the given value.
   */
  void to(int value);

  /**
   * Binds constant to the given value.
   */
  void to(long value);

  /**
   * Binds constant to the given value.
   */
  void to(boolean value);

  /**
   * Binds constant to the given value.
   */
  void to(double value);

  /**
   * Binds constant to the given value.
   */
  void to(float value);

  /**
   * Binds constant to the given value.
   */
  void to(short value);

  /**
   * Binds constant to the given value.
   */
  void to(char value);

  /**
   * Binds constant to the given value.
   */
  void to(Class<?> value);

  /**
   * Binds constant to the given value.
   */
  <E extends Enum<E>> void to(E value);
}
