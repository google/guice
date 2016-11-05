/*
 * Copyright (C) 2016 Google Inc.
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

package com.google.inject.internal;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapKey;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.google.inject.multibindings.ProvidesIntoOptional;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.ModuleAnnotatedMethodScanner;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * A {@link ModuleAnnotatedMethodScanner} that handles the {@Provides}, {@link ProvidesIntoSet},
 * {@link ProvidesIntoMap} and {@link ProvidesIntoOptional} annotations.
 *
 * <p>This is the default scanner used by ProviderMethodsModule and handles all the built in
 * annotations.
 */
final class ProvidesMethodScanner extends ModuleAnnotatedMethodScanner {
  static final ProvidesMethodScanner INSTANCE = new ProvidesMethodScanner();
  private static final ImmutableSet<Class<? extends Annotation>> ANNOTATIONS =
      ImmutableSet.of(
          Provides.class, ProvidesIntoSet.class, ProvidesIntoMap.class, ProvidesIntoOptional.class);

  private ProvidesMethodScanner() {}

  @Override
  public Set<? extends Class<? extends Annotation>> annotationClasses() {
    return ANNOTATIONS;
  }

  @SuppressWarnings({"unchecked", "rawtypes"}) // mapKey doesn't know its key type
  @Override
  public <T> Key<T> prepareMethod(
      Binder binder, Annotation annotation, Key<T> key, InjectionPoint injectionPoint) {
    Method method = (Method) injectionPoint.getMember();
    AnnotationOrError mapKey = findMapKeyAnnotation(binder, method);
    if (annotation instanceof Provides) {
      if (mapKey.annotation != null) {
        binder.addError("Found a MapKey annotation on non map binding at %s.", method);
      }
      // no key rewriting for plain old @Provides
      return key;
    }
    if (annotation instanceof ProvidesIntoSet) {
      if (mapKey.annotation != null) {
        binder.addError("Found a MapKey annotation on non map binding at %s.", method);
      }
      return RealMultibinder.newRealSetBinder(binder, key).getKeyForNewItem();
    } else if (annotation instanceof ProvidesIntoMap) {
      if (mapKey.error) {
        // Already failed on the MapKey, don't bother doing more work.
        return key;
      }
      if (mapKey.annotation == null) {
        // If no MapKey, make an error and abort.
        binder.addError("No MapKey found for map binding at %s.", method);
        return key;
      }
      TypeAndValue typeAndValue = typeAndValueOfMapKey(mapKey.annotation);
      return RealMapBinder.newRealMapBinder(binder, typeAndValue.type, key)
          .getKeyForNewValue(typeAndValue.value);
    } else if (annotation instanceof ProvidesIntoOptional) {
      if (mapKey.annotation != null) {
        binder.addError("Found a MapKey annotation on non map binding at %s.", method);
      }
      switch (((ProvidesIntoOptional) annotation).value()) {
        case DEFAULT:
          return RealOptionalBinder.newRealOptionalBinder(binder, key).getKeyForDefaultBinding();
        case ACTUAL:
          return RealOptionalBinder.newRealOptionalBinder(binder, key).getKeyForActualBinding();
      }
    }
    throw new IllegalStateException("Invalid annotation: " + annotation);
  }

  private static class AnnotationOrError {
    final Annotation annotation;
    final boolean error;

    AnnotationOrError(Annotation annotation, boolean error) {
      this.annotation = annotation;
      this.error = error;
    }

    static AnnotationOrError forPossiblyNullAnnotation(Annotation annotation) {
      return new AnnotationOrError(annotation, false);
    }

    static AnnotationOrError forError() {
      return new AnnotationOrError(null, true);
    }
  }

  private static AnnotationOrError findMapKeyAnnotation(Binder binder, Method method) {
    Annotation foundAnnotation = null;
    for (Annotation annotation : method.getAnnotations()) {
      MapKey mapKey = annotation.annotationType().getAnnotation(MapKey.class);
      if (mapKey != null) {
        if (foundAnnotation != null) {
          binder.addError("Found more than one MapKey annotations on %s.", method);
          return AnnotationOrError.forError();
        }
        if (mapKey.unwrapValue()) {
          try {
            // validate there's a declared method called "value"
            Method valueMethod = annotation.annotationType().getDeclaredMethod("value");
            if (valueMethod.getReturnType().isArray()) {
              binder.addError(
                  "Array types are not allowed in a MapKey with unwrapValue=true: %s",
                  annotation.annotationType());
              return AnnotationOrError.forError();
            }
          } catch (NoSuchMethodException invalid) {
            binder.addError(
                "No 'value' method in MapKey with unwrapValue=true: %s",
                annotation.annotationType());
            return AnnotationOrError.forError();
          }
        }
        foundAnnotation = annotation;
      }
    }
    return AnnotationOrError.forPossiblyNullAnnotation(foundAnnotation);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  static TypeAndValue<?> typeAndValueOfMapKey(Annotation mapKeyAnnotation) {
    if (!mapKeyAnnotation.annotationType().getAnnotation(MapKey.class).unwrapValue()) {
      return new TypeAndValue(TypeLiteral.get(mapKeyAnnotation.annotationType()), mapKeyAnnotation);
    } else {
      try {
        Method valueMethod = mapKeyAnnotation.annotationType().getDeclaredMethod("value");
        valueMethod.setAccessible(true);
        TypeLiteral<?> returnType =
            TypeLiteral.get(mapKeyAnnotation.annotationType()).getReturnType(valueMethod);
        return new TypeAndValue(returnType, valueMethod.invoke(mapKeyAnnotation));
      } catch (NoSuchMethodException e) {
        throw new IllegalStateException(e);
      } catch (SecurityException e) {
        throw new IllegalStateException(e);
      } catch (IllegalAccessException e) {
        throw new IllegalStateException(e);
      } catch (InvocationTargetException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  private static class TypeAndValue<T> {
    final TypeLiteral<T> type;
    final T value;

    TypeAndValue(TypeLiteral<T> type, T value) {
      this.type = type;
      this.value = value;
    }
  }
}
