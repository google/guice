package com.google.inject.daggeradapter;

import com.google.common.collect.ImmutableSet;
import dagger.Binds;
import dagger.BindsOptionalOf;
import dagger.Provides;
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
          .build();

  /** Returns all binding annotations supported by {@link DaggerAdapter}. */
  static ImmutableSet<Class<? extends Annotation>> supportedBindingAnnotations() {
    return BINDING_ANNOTATIONS;
  }

  /** Returns all annotations from dagger packages that are supported by {@link DaggerAdapter}. */
  static ImmutableSet<Class<? extends Annotation>> allSupportedAnnotations() {
    return ALL_SUPPORTED_ANNOTATIONS;
  }
}
