/*
 * Copyright (C) 2007 Google Inc.
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

package com.google.inject;

import java.util.Arrays;

/**
 * The entry point to the Guice framework. Creates {@link Injector}s from
 * {@link Module}s.
 */
public final class Guice {

  private Guice() {}

  /**
   * Creates an injector with no explicit bindings.
   */
  static Injector createEmptyInjector() {
    return createInjector();
  }

  /**
   * Creates an injector for the given set of modules.
   *
   * @throws CreationException from which you can retrieve the individual error
   *  messages
   */
  public static Injector createInjector(Module... modules) {
    return createInjector(Arrays.asList(modules));
  }

  /**
   * Creates an injector for the given set of modules.
   *
   * @throws CreationException from which you can retrieve the individual error
   *  messages
   */
  public static Injector createInjector(Iterable<Module> modules) {
    return createInjector(Stage.DEVELOPMENT, modules);
  }

  /**
   * Creates an injector for the given set of modules, in a given development
   * stage.
   *
   * @throws CreationException from which you can retrieve the individual error
   *  messages.
   */
  public static Injector createInjector(Stage stage, Module... modules) {
    return createInjector(stage, Arrays.asList(modules));
  }

  /**
   * Creates an injector for the given set of modules, in a given development
   * stage.
   *
   * @throws CreationException from which you can retrieve the individual error
   *  messages.
   */
  public static Injector createInjector(Stage stage, Iterable<Module> modules) {
    BinderImpl binder = new BinderImpl(stage);
    for (Module module : modules) {
      binder.install(module);
    }
    return binder.createInjector();
  }
}
