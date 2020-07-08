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
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.inject.daggeradapter.Annotations.getAnnotatedAnnotation;
import static com.google.inject.daggeradapter.Keys.parameterKey;
import static com.google.inject.daggeradapter.SupportedAnnotations.supportedBindingAnnotations;

import com.google.common.base.MoreObjects;
import com.google.common.base.Predicate;
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
import dagger.multibindings.IntoMap;
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

  static DaggerMethodScanner create(Predicate<Method> predicate) {
    return new DaggerMethodScanner(predicate);
  }

  private final Predicate<Method> predicate;

  @Override
  public ImmutableSet<Class<? extends Annotation>> annotationClasses() {
    return supportedBindingAnnotations();
  }

  @Override
  public <T> Key<T> prepareMethod(
      Binder binder, Annotation annotation, Key<T> key, InjectionPoint injectionPoint) {
    Method method = (Method) injectionPoint.getMember();
    if (!predicate.apply(method)) {
      return null;
    }
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
    if (method.isAnnotationPresent(IntoSet.class)) {
      return processSetBinding(binder, key);
    } else if (method.isAnnotationPresent(IntoMap.class)) {
      return processMapBinding(binder, key, method);
    }
    return key;
  }

  private static <T> Key<T> processSetBinding(Binder binder, Key<T> key) {
    Multibinder<T> setBinder = newSetBinder(binder, key.getTypeLiteral(), key.getAnnotation());

    Key<T> contributionKey = key.withAnnotation(UniqueAnnotations.create());
    setBinder.addBinding().to(contributionKey);
    return contributionKey;
  }

  private static <K, V> Key<V> processMapBinding(Binder binder, Key<V> key, Method method) {
    MapKeyData<K> mapKeyData = mapKeyData(method);
    MapBinder<K, V> mapBinder =
        newMapBinder(binder, mapKeyData.typeLiteral, key.getTypeLiteral(), key.getAnnotation());

    Key<V> contributionKey = key.withAnnotation(UniqueAnnotations.create());
    mapBinder.addBinding(mapKeyData.key).to(contributionKey);
    return contributionKey;
  }

  private static <K> MapKeyData<K> mapKeyData(Method method) {
    Annotation mapKey = getAnnotatedAnnotation(method, dagger.MapKey.class).get();
    dagger.MapKey mapKeyDefinition = mapKey.annotationType().getAnnotation(dagger.MapKey.class);
    if (!mapKeyDefinition.unwrapValue()) {
      return MapKeyData.create(TypeLiteral.get(mapKey.annotationType()), mapKey);
    }

    Method mapKeyValueMethod =
        getOnlyElement(Arrays.asList(mapKey.annotationType().getDeclaredMethods()));
    Object mapKeyValue;
    try {
      mapKeyValue = mapKeyValueMethod.invoke(mapKey);
    } catch (ReflectiveOperationException e) {
      throw new UnsupportedOperationException("Cannot extract map key value", e);
    }
    return MapKeyData.create(
        TypeLiteral.get(mapKeyValueMethod.getGenericReturnType()), mapKeyValue);
  }

  private static class MapKeyData<K> {
    final TypeLiteral<K> typeLiteral;
    final K key;

    MapKeyData(TypeLiteral<K> typeLiteral, K key) {
      this.typeLiteral = typeLiteral;
      this.key = key;
    }

    // We can't verify the compatibility of the type arguments here, but by definition they must be
    // aligned
    @SuppressWarnings({"unchecked", "rawtypes"})
    static <K> MapKeyData<K> create(TypeLiteral<?> typeLiteral, Object key) {
      return new MapKeyData(typeLiteral, key);
    }
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

  @Override
  public boolean equals(Object object) {
    if (object instanceof DaggerMethodScanner) {
      DaggerMethodScanner that = (DaggerMethodScanner) object;
      return this.predicate.equals(that.predicate);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return predicate.hashCode();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("predicate", predicate).toString();
  }

  private DaggerMethodScanner(Predicate<Method> predicate) {
    this.predicate = predicate;
  }
}
