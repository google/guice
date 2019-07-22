package com.google.inject.daggeradapter;

import com.google.common.collect.ImmutableSet;
import dagger.Binds;
import dagger.BindsOptionalOf;
import dagger.MapKey;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;
import dagger.multibindings.Multibinds;
import java.lang.annotation.Annotation;

/** Collections of annotations that are supported by {@link DaggerAdapter}. */
final class SupportedAnnotations {
  private static final ImmutableSet<Class<? extends Annotation>> BINDING_ANNOTATIONS =
      ImmutableSet.of(Provides.class, Binds.class, Multibinds.class, BindsOptionalOf.class);

  private static final ImmutableSet<Class<? extends Annotation>> ALL_SUPPORTED_ANNOTATIONS =
      ImmutableSet.<Class<? extends Annotation>>builder()
          .addAll(BINDING_ANNOTATIONS)
          .add(IntoSet.class)
          .add(IntoMap.class)
          // TODO(ronshapiro): should we support (and automatically bind?) dagger.Reusable?
          .build();

  /** Returns all binding annotations supported by {@link DaggerAdapter}. */
  static ImmutableSet<Class<? extends Annotation>> supportedBindingAnnotations() {
    return BINDING_ANNOTATIONS;
  }

  /**
   * Returns true if {@code annotation} is in a dagger package and is supported by {@link
   * DaggerAdapter}.
   */
  static boolean isAnnotationSupported(Class<? extends Annotation> annotation) {
    if (ALL_SUPPORTED_ANNOTATIONS.contains(annotation)) {
      return true;
    }
    return annotation.isAnnotationPresent(MapKey.class);
  }
}
