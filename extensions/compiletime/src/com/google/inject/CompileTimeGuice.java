/**
 * Copyright (C) 2008 Google Inc.
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

import static com.google.inject.internal.Objects.nonNull;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * <strong>Unsupported.</strong> Currently compile-time Guice code still
 * requires runtime access to the reflection APIs and is not suited for use.
 *
 * <p>Performs "compile-time" code generation in order to avoid runtime
 * reflection. This is motivated to support Guice on environments where Java
 * reflection is expensive or unavailable.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
/* public */ class CompileTimeGuice {

  private final String name;
  private final Set<? extends Module> modules;

  /**
   * Builds a new compile-time guice instance. The instance must be provided
   * the exact same {@code name} and {@code modules} at both compile-time and
   * at runtime, otherwise behaviour is undefined.
   *
   * @param name a unique, valid Java identifier such as "FooApplication".
   * @param modules the modules used to build the injector.
   */
  public CompileTimeGuice(String name, Set<? extends Module> modules) {
    this.name = nonNull(name, "name");
    this.modules = nonNull(modules, "modules");
  }

  /**
   * "Compile-time" step that generates <code>.java</code> source directories
   * to support runtime reflection. This step must be executed whenever the
   * modules or their dependendent classes have changed.
   */
  void generateCode(File generatedSourceDirectory) throws IOException {
    CodeGenReflectionFactory reflectionFactory = new CodeGenReflectionFactory(name);

    new InjectorBuilder()
        .stage(Stage.TOOL)
        .addModules(modules)
        .usingReflectionFactory(reflectionFactory)
        .build();

    reflectionFactory.writeToFile(generatedSourceDirectory);
  }

  /**
   * Runtime step that uses the generated <code>.java</code> to create an
   * injector.
   */
  Injector createInjector() {
    Reflection.Factory reflectionFactory
        = new CodeGenReflectionFactory(name).getRuntimeReflectionFactory();

    return new InjectorBuilder()
        .stage(Stage.TOOL)
        .addModules(modules)
        .usingReflectionFactory(reflectionFactory)
        .build();
  }
}