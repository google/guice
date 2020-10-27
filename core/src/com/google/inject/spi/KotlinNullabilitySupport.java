package com.google.inject.spi;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The class allows Guice to optionally have a dependency on Kotlin code. When
 * com.google.inject.kotlin.KotlinParameterNullabilityFunctions is available in the class loader,
 * Guice will be able to understand that String? is a nullable type.
 */
final class KotlinNullabilitySupport {

  private final Function<Constructor<?>, Predicate<Integer>> constructorParameterNullability;
  private final Function<Method, Predicate<Integer>> methodParameterNullability;

  private KotlinNullabilitySupport(
      Function<Constructor<?>, Predicate<Integer>> constructorParameterNullability,
      Function<Method, Predicate<Integer>> methodParameterNullability) {
    this.constructorParameterNullability = constructorParameterNullability;
    this.methodParameterNullability = methodParameterNullability;
  }

  static KotlinNullabilitySupport getInstance() {
    return KotlinNullabilitySupportHolder.INSTANCE;
  }

  /**
   * Returns a {@link Predicate} that says whether the constructor's i-th parameter is
   * Kotlin-nullable.
   */
  Predicate<Integer> getParameterPredicate(Constructor<?> constructor) {
    return constructorParameterNullability.apply(constructor);
  }

  /**
   * Returns a {@link Predicate} that says whether the method's i-th parameter is Kotlin-nullable.
   */
  Predicate<Integer> getParameterPredicate(Method method) {
    return methodParameterNullability.apply(method);
  }

  // lazy holder pattern
  private static class KotlinNullabilitySupportHolder {
    static final KotlinNullabilitySupport INSTANCE = loadKotlinNullabilitySupport();
  }

  private static KotlinNullabilitySupport loadKotlinNullabilitySupport() {
    Function<Constructor<?>, Predicate<Integer>> kotlinConstructorParameterNullabilityFunction;
    Function<Method, Predicate<Integer>> kotlinMethodParameterNullabilityFunction;
    try {
      Class<?> clazz =
          Class.forName("com.google.inject.kotlin.KotlinParameterNullabilityFunctions");

      @SuppressWarnings("unchecked") // we know the types of KotlinParameterNullabilityFunctions
      Function<Constructor<?>, Predicate<Integer>> localConstructorFn =
          (Function<Constructor<?>, Predicate<Integer>>)
              getStaticField(clazz, "KOTLIN_CONSTRUCTOR_PARAMETER_NULLABILITY_FUNCTION");
      kotlinConstructorParameterNullabilityFunction = localConstructorFn;
      @SuppressWarnings("unchecked") // we know the types of KotlinParameterNullabilityFunctions
      Function<Method, Predicate<Integer>> localMethodFn =
          (Function<Method, Predicate<Integer>>)
              getStaticField(clazz, "KOTLIN_METHOD_PARAMETER_NULLABILITY_FUNCTION");
      kotlinMethodParameterNullabilityFunction = localMethodFn;
    } catch (NoSuchFieldException | IllegalAccessException | ClassNotFoundException e) {
      kotlinConstructorParameterNullabilityFunction = constructor -> index -> false;
      kotlinMethodParameterNullabilityFunction = method -> index -> false;
    }
    return new KotlinNullabilitySupport(
        kotlinConstructorParameterNullabilityFunction, kotlinMethodParameterNullabilityFunction);
  }

  private static Object getStaticField(Class<?> clazz, String methodName)
      throws NoSuchFieldException, IllegalAccessException {
    Field m = clazz.getDeclaredField(methodName);
    m.setAccessible(true);
    return m.get(null);
  }
}
