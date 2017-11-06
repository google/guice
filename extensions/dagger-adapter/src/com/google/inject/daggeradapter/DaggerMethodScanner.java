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

import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.internal.UniqueAnnotations;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.ModuleAnnotatedMethodScanner;
import dagger.Provides;
import dagger.Provides.Type;
import dagger.multibindings.ElementsIntoSet;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * A scanner to process provider methods on Dagger modules.
 *
 * @author cgruber@google.com (Christian Gruber)
 */
final class DaggerMethodScanner extends ModuleAnnotatedMethodScanner {
  static final DaggerMethodScanner INSTANCE = new DaggerMethodScanner();
  private static final ImmutableSet<Class<Provides>> ANNOTATIONS =
      ImmutableSet.of(dagger.Provides.class);

  @Override
  public Set<? extends Class<? extends Annotation>> annotationClasses() {
    return ANNOTATIONS;
  }

  @Override
  public <T> Key<T> prepareMethod(
      Binder binder, Annotation rawAnnotation, Key<T> key, InjectionPoint injectionPoint) {
    Method providesMethod = (Method) injectionPoint.getMember();
    Provides annotation = (Provides) rawAnnotation;
    if (providesMethod.isAnnotationPresent(IntoSet.class)) {
      return processSetBinding(binder, key);
    } else if (providesMethod.isAnnotationPresent(ElementsIntoSet.class)) {
      binder.addError("@ElementsIntoSet contributions are not suppored by Guice.", providesMethod);
      return key;
    } else if (providesMethod.isAnnotationPresent(IntoMap.class)) {
      /* TODO(cgruber) implement map bindings */
      binder.addError("Map bindings are not yet supported.");
      return key;
    }

    switch (annotation.type()) {
      case UNIQUE:
        return key;
      case SET:
        return processSetBinding(binder, key);
      case SET_VALUES:
        binder.addError(
            Type.SET_VALUES.name() + " contributions are not supported by Guice.", providesMethod);
        return key;
      default:
        binder.addError("Unknown @Provides type " + annotation.type() + ".", providesMethod);
        return key;
    }
  }

  private static <T> Key<T> processSetBinding(Binder binder, Key<T> key) {
    Annotation annotation = key.getAnnotation();
    Multibinder<T> setBinder =
        (annotation != null)
            ? Multibinder.newSetBinder(binder, key.getTypeLiteral(), annotation)
            : Multibinder.newSetBinder(binder, key.getTypeLiteral());
    Key<T> newKey = Key.get(key.getTypeLiteral(), UniqueAnnotations.create());
    setBinder.addBinding().to(newKey);
    return newKey;
  }

  private DaggerMethodScanner() {}
}
