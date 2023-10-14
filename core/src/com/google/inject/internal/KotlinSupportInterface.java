package com.google.inject.internal;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Predicate;

/** Interface for accessing information about Kotlin code. */
public interface KotlinSupportInterface {
  /** Returns an array of {@link Annotation}s on the field's Kotlin property (if applicable). */
  Annotation[] getAnnotations(Field field);

  /** Returns true if the field is declared as kotlin nullable type. */
  boolean isNullable(Field field);

  /**
   * Returns a {@link Predicate} that says whether the constructor's i-th parameter is
   * Kotlin-nullable.
   */
  Predicate<Integer> getIsParameterKotlinNullablePredicate(Constructor<?> constructor);

  /**
   * Returns a {@link Predicate} that says whether the method's i-th parameter is Kotlin-nullable.
   */
  Predicate<Integer> getIsParameterKotlinNullablePredicate(Method method);

  /** Checks for any errors on the constructor's parameters' annotations. */
  void checkConstructorParameterAnnotations(Constructor<?> constructor, Errors errors);

  /** Returns whether the {@code clazz} is a local Kotlin class. */
  boolean isLocalClass(Class<?> clazz);
}
