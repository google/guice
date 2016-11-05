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

package com.google.inject.multibindings;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.ModuleAnnotatedMethodScanner;
import com.google.inject.util.Modules;
import java.lang.annotation.Annotation;
import java.util.Set;

/**
 * Scans a module for annotations that signal multibindings, mapbindings, and optional bindings.
 *
 * @since 4.0
 * @deprecated This functionality is installed by default. All references to this can be safely
 *     removed. This class will be removed in Guice 4.4
 */
@Deprecated
public class MultibindingsScanner {
  private MultibindingsScanner() {}

  /**
   * Returns a module that, when installed, will scan all modules for methods with the annotations
   * {@literal @}{@link ProvidesIntoMap}, {@literal @}{@link ProvidesIntoSet}, and
   * {@literal @}{@link ProvidesIntoOptional}.
   *
   * <p>This is a convenience method, equivalent to doing {@code
   * binder().scanModulesForAnnotatedMethods(MultibindingsScanner.scanner())}.
   *
   * @deprecated This functionality is now installed by default. All references/installations can be
   *     eliminated.
   */
  @Deprecated
  public static Module asModule() {
    return Modules.EMPTY_MODULE;
  }

  /**
   * @deprecated This method returns an empty scanner since the preexisting functionality is
   *     installed by default.
   */
  @Deprecated
  public static ModuleAnnotatedMethodScanner scanner() {
    return new ModuleAnnotatedMethodScanner() {
      @Override
      public Set<? extends Class<? extends Annotation>> annotationClasses() {
        return ImmutableSet.of();
      }

      @Override
      public <T> Key<T> prepareMethod(
          Binder binder, Annotation annotation, Key<T> key, InjectionPoint injectionPoint) {
        throw new IllegalStateException("Unexpected annotation: " + annotation);
      }
    };
  }
}
