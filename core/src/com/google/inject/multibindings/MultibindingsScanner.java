/**
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

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.internal.MultibindingsMethodScanner;
import com.google.inject.spi.ModuleAnnotatedMethodScanner;

/**
 * Scans a module for annotations that signal multibindings, mapbindings, and optional bindings.
 *
 * @since 4.0
 */
public class MultibindingsScanner {
  // TODO(lukes): eliminate this class and install by default in createInjector

  private MultibindingsScanner() {}

  /**
   * Returns a module that, when installed, will scan all modules for methods with the annotations
   * {@literal @}{@link ProvidesIntoMap}, {@literal @}{@link ProvidesIntoSet}, and
   * {@literal @}{@link ProvidesIntoOptional}.
   * 
   * <p>This is a convenience method, equivalent to doing
   * {@code binder().scanModulesForAnnotatedMethods(MultibindingsScanner.scanner())}.
   */
  public static Module asModule() {
    return new AbstractModule() {
      @Override protected void configure() {
        binder().scanModulesForAnnotatedMethods(MultibindingsMethodScanner.INSTANCE);
      }
    };
  }
  
  /**
   * Returns a {@link ModuleAnnotatedMethodScanner} that, when bound, will scan all modules for
   * methods with the annotations {@literal @}{@link ProvidesIntoMap},
   * {@literal @}{@link ProvidesIntoSet}, and {@literal @}{@link ProvidesIntoOptional}.
   */
  public static ModuleAnnotatedMethodScanner scanner() {
    return MultibindingsMethodScanner.INSTANCE;
  }
}
