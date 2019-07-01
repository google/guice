/*
 * Copyright (C) 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.inject.daggeradapter;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.inject.daggeradapter.Annotations.getAnnotatedAnnotation;
import static com.google.inject.daggeradapter.Keys.parameterKey;
import static com.google.inject.daggeradapter.SupportedAnnotations.supportedBindingAnnotations;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.ScopedBindingBuilder;
import com.google.inject.internal.UniqueAnnotations;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.ModuleAnnotatedMethodScanner;
import dagger.Binds;
import dagger.BindsOptionalOf;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import dagger.multibindings.Multibinds;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import javax.inject.Scope;

/**
 * A scanner to process provider methods on Dagger modules.
 *
 * @author cgruber@google.com (Christian Gruber)
 */
final class DaggerMethodScanner extends ModuleAnnotatedMethodScanner {
  /**
   * A single instance is not necessary for the correctness or performance of the scanner, but it
   * does suffice an invariant of {@link com.google.inject.internal.ProviderMethodsModule}, which
   * uses scanner equality in its own equality semantics. If multiple modules use
   * DaggerAdapter.from(FooModule.class) separately, and thus are not deduplicated by DaggerAdapter
   * on their own, Guice will do so as long as this scanner is always equal.
   *
   * <p>If we do away with this singleton instance, we need to be sure that we do so in a way that
   * maintains equality in these cases.
   */
  static final DaggerMethodScanner INSTANCE = new DaggerMethodScanner();

  @Override
  public ImmutableSet<Class<? extends Annotation>> annotationClasses() {
    return supportedBindingAnnotations();
  }

  @Override
  public <T> Key<T> prepareMethod(
      Binder binder, Annotation annotation, Key<T> key, InjectionPoint injectionPoint) {
    Method method = (Method) injectionPoint.getMember();
    Class<? extends Annotation> annotationType = annotation.annotationType();
    if (annotationType.equals(Provides.class)) {
      return prepareProvidesKey(binder, method, key);
    } else if (annotationType.equals(Binds.class)) {
      configureBindsKey(binder, method, key);
      return null;
    } else if (annotationType.equals(Multibinds.class)) {
      configureMultibindsKey(binder, method, key);
      return null;
    } else if (annotationType.equals(BindsOptionalOf.class)) {
      OptionalBinder.newOptionalBinder(binder, key);
      return null;
    }

    throw new UnsupportedOperationException(annotation.toString());
  }

  private <T> Key<T> prepareProvidesKey(Binder binder, Method method, Key<T> key) {
    key = processMultibindingAnnotations(binder, method, key);

    return key;
  }

  private <T> void configureBindsKey(Binder binder, Method method, Key<T> key) {
    // the Dagger processor already validates the assignability of these two keys. parameterKey()
    // has no way to infer the correct type parameter, so we use rawtypes instead.
    @SuppressWarnings({"unchecked", "rawtypes"})
    ScopedBindingBuilder scopedBindingBuilder =
        binder
            .bind((Key) processMultibindingAnnotations(binder, method, key))
            .to(parameterKey(method.getParameters()[0]));

    getAnnotatedAnnotation(method, Scope.class)
        .ifPresent(scope -> scopedBindingBuilder.in(scope.annotationType()));
  }

  private static <T> Key<T> processMultibindingAnnotations(
      Binder binder, Method method, Key<T> key) {
    return method.isAnnotationPresent(IntoSet.class) ? processSetBinding(binder, key) : key;
  }

  private static <T> Key<T> processSetBinding(Binder binder, Key<T> key) {
    Multibinder<T> setBinder = newSetBinder(binder, key.getTypeLiteral(), key.getAnnotation());
    Key<T> newKey = Key.get(key.getTypeLiteral(), UniqueAnnotations.create());
    setBinder.addBinding().to(newKey);
    return newKey;
  }

  private static <T> Multibinder<T> newSetBinder(
      Binder binder, TypeLiteral<T> typeLiteral, Annotation possibleAnnotation) {
    return possibleAnnotation == null
        ? Multibinder.newSetBinder(binder, typeLiteral)
        : Multibinder.newSetBinder(binder, typeLiteral, possibleAnnotation);
  }

  private static <K, V> MapBinder<K, V> newMapBinder(
      Binder binder,
      TypeLiteral<K> keyType,
      TypeLiteral<V> valueType,
      Annotation possibleAnnotation) {
    return possibleAnnotation == null
        ? MapBinder.newMapBinder(binder, keyType, valueType)
        : MapBinder.newMapBinder(binder, keyType, valueType, possibleAnnotation);
  }

  private <T> void configureMultibindsKey(Binder binder, Method method, Key<T> key) {
    Class<?> rawReturnType = method.getReturnType();
    ImmutableList<? extends TypeLiteral<?>> typeParameters =
        Arrays.stream(((ParameterizedType) method.getGenericReturnType()).getActualTypeArguments())
            .map(TypeLiteral::get)
            .collect(toImmutableList());

    if (rawReturnType.equals(Set.class)) {
      newSetBinder(binder, typeParameters.get(0), key.getAnnotation());
    } else if (rawReturnType.equals(Map.class)) {
      newMapBinder(binder, typeParameters.get(0), typeParameters.get(1), key.getAnnotation());
    } else {
      throw new AssertionError(
          "@dagger.Multibinds can only be used with Sets or Map, found: "
              + method.getGenericReturnType());
    }
  }

  private DaggerMethodScanner() {}
}
