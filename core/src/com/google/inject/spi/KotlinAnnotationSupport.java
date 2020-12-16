package com.google.inject.spi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.function.Function;

/**
 * This class allows Guice to optionally have a dependency on Kotlin code. When
 * com.google.inject.kotlin.KotlinAnnotationFunctions is available in the class loader, Guice will
 * be able to read {@link Annotation}s on Kotlin properties.
 */
final class KotlinAnnotationSupport {

  private final Function<Field, Annotation[]> annotationsFn;

  private KotlinAnnotationSupport(Function<Field, Annotation[]> annotationsFn) {
    this.annotationsFn = annotationsFn;
  }

  static KotlinAnnotationSupport getInstance() {
    return KotlinAnnotationSupportHolder.INSTANCE;
  }

  /** Returns an array of {@link Annotation}s on the field's Kotlin property (if applicable). */
  Annotation[] getAnnotations(Field field) {
    return annotationsFn.apply(field);
  }

  // lazy holder pattern
  private static class KotlinAnnotationSupportHolder {
    static final KotlinAnnotationSupport INSTANCE = loadKotlinAnnotationSupport();
  }

  private static KotlinAnnotationSupport loadKotlinAnnotationSupport() {
    Function<Field, Annotation[]> annotationsFn;
    try {
      Class<?> clazz = Class.forName("com.google.inject.kotlin.KotlinAnnotationFunctions");

      @SuppressWarnings("unchecked") // we know the types of KotlinAnnotationSupport
      Function<Field, Annotation[]> localAnnotationsFn =
          (Function<Field, Annotation[]>) getDeclaredField(clazz, "KOTLIN_ANNOTATIONS_FUNCTION");
      annotationsFn = localAnnotationsFn;
    } catch (ReflectiveOperationException e) {
      Annotation[] empty = new Annotation[] {};
      annotationsFn = field -> empty;
    }
    return new KotlinAnnotationSupport(annotationsFn);
  }

  private static Object getDeclaredField(Class<?> clazz, String methodName)
      throws NoSuchFieldException, IllegalAccessException {
    Field m = clazz.getDeclaredField(methodName);
    m.setAccessible(true);
    return m.get(null);
  }
}
