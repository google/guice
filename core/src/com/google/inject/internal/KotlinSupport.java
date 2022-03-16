package com.google.inject.internal;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Predicate;

/**
 * Class to conditionally load support for Kotlin features. These features are enabled based on
 * whether {@code com.google.inject.KotlinSupportImpl} is in the class path.
 */
public final class KotlinSupport {

  private KotlinSupport() {} // no instantiation

  public static KotlinSupportInterface getInstance() {
    return KotlinSupportHolder.INSTANCE;
  }

  private static class KotlinSupportHolder {
    static final KotlinSupportInterface INSTANCE = loadKotlinSupport();
  }

  private static KotlinSupportInterface loadKotlinSupport() {
    try {
      @SuppressWarnings("unchecked")
      Class<? extends KotlinSupportInterface> kotlinSupportClass =
          (Class<? extends KotlinSupportInterface>)
              Class.forName("com.google.inject.KotlinSupportImpl");
      Field instance = kotlinSupportClass.getField("INSTANCE");
      instance.setAccessible(true);
      return (KotlinSupportInterface) instance.get(null);
    } catch (ReflectiveOperationException e) {
      return new KotlinUnsupported();
    }
  }

  private static class KotlinUnsupported implements KotlinSupportInterface {
    static final Annotation[] NO_ANNOTATIONS = new Annotation[0];
    static final Predicate<Integer> FALSE_PREDICATE = integer -> false;

    @Override
    public Annotation[] getAnnotations(Field field) {
      return NO_ANNOTATIONS;
    }

    @Override
    public boolean isNullable(Field field) {
      return false;
    }

    @Override
    public Predicate<Integer> getIsParameterKotlinNullablePredicate(Constructor<?> constructor) {
      return FALSE_PREDICATE;
    }

    @Override
    public Predicate<Integer> getIsParameterKotlinNullablePredicate(Method method) {
      return FALSE_PREDICATE;
    }

    @Override
    public void checkConstructorParameterAnnotations(Constructor<?> constructor, Errors errors) {
      // do nothing
    }

    @Override
    public boolean isLocalClass(Class<?> clazz) {
      return false;
    }
  }
}
