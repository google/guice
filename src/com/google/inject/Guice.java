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

import com.google.common.collect.Sets;
import com.google.inject.spi.BindConstant;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.google.inject.spi.ModuleWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * The entry point to the Guice framework. Creates {@link Injector}s from
 * {@link Module}s.
 *
 * <p>Guice supports a model of development that draws clear boundaries between
 * APIs, Implementations of these APIs, Modules which configure these
 * implementations, and finally Applications which consist of a collection of
 * Modules. It is the Application, which typically defines your {@code main()}
 * method, that bootstraps the Guice Injector using the {@code Guice} class, as
 * in this example:
 * <pre>
 *     public class FooApplication {
 *       public static void main(String[] args) {
 *         Injector injector = Guice.createInjector(
 *             new ModuleA(),
 *             new ModuleB(),
 *             . . .
 *             new FooApplicationFlagsModule(args)
 *         );
 *
 *         // Now just bootstrap the application and you're done
 *         MyStartClass starter = injector.getInstance(MyStartClass.class);
 *         starter.runApplication();
 *       }
 *     }
 * </pre>
 */
public final class Guice {

  private Guice() {}

  /**
   * Creates an injector for the given set of modules.
   *
   * @throws CreationException if one or more errors occur during Injector
   *     construction
   */
  public static Injector createInjector(Module... modules) {
    return createInjector(Arrays.asList(modules));
  }

  /**
   * Creates an injector for the given set of modules.
   *
   * @throws CreationException if one or more errors occur during Injector
   *     construction
   */
  public static Injector createInjector(Iterable<? extends Module> modules) {
    return createInjector(Stage.DEVELOPMENT, modules);
  }

  /**
   * Creates an injector for the given set of modules, in a given development
   * stage.
   *
   * @throws CreationException if one or more errors occur during Injector
   *     construction
   */
  public static Injector createInjector(Stage stage, Module... modules) {
    return createInjector(stage, Arrays.asList(modules));
  }

  /**
   * Creates an injector for the given set of modules, in a given development
   * stage.
   *
   * @throws CreationException if one or more errors occur during Injector
   *     construction
   */
  public static Injector createInjector(Stage stage,
      Iterable<? extends Module> modules) {
    return createInjector(null, stage, modules);
  }


  /**
   * Creates an injector for the given set of modules, with the given parent
   * injector.
   *
   * @throws CreationException if one or more errors occur during Injector
   *     construction
   */
  public static Injector createInjector(Injector parent,
      Iterable<? extends Module> modules) {
    return createInjector(parent, Stage.DEVELOPMENT, modules);
  }


  /**
   * Creates an injector for the given set of modules, with the given parent
   * injector.
   *
   * @throws CreationException if one or more errors occur during Injector
   *     construction
   */
  public static Injector createInjector(Injector parent,
      Module... modules) {
    return createInjector(parent, Stage.DEVELOPMENT, Arrays.asList(modules));
  }

  /**
   * Creates an injector for the given set of modules, in a given development
   * stage, with the given parent injector.
   *
   * @throws CreationException if one or more errors occur during Injector
   *     construction
   */
  public static Injector createInjector(
      Injector parent, Stage stage,
      Iterable<? extends Module> modules) {
    return new InjectorBuilder()
        .stage(stage)
        .parentInjector(parent)
        .addModules(modules)
        .build();
  }

  /**
   * Returns a new {@link Module} that overlays {@code overridesModule} over
   * {@code module}. If a key is bound by both modules, only the binding in
   * overrides is kept. This can be used to replace bindings in a production
   * module with test bindings:
   * <pre>
   * Module functionalTestModule
   *     = Guice.overrideModule(new ProductionModule(), new TestModule());
   * </pre>
   */
  public static Module overrideModule(Module module, Module overridesModule) {
    final List<Element> elements = Elements.getElements(module);
    final List<Element> overrideElements = Elements.getElements(overridesModule);

    return new AbstractModule() {
      public void configure() {
        final Set<Key> overriddenKeys = Sets.newHashSet();

        // execute the overrides module, keeping track of which keys were bound
        new ModuleWriter() {
          @Override public <T> void writeBind(Binder binder, Binding<T> binding) {
            overriddenKeys.add(binding.getKey());
            super.writeBind(binder, binding);
          }
          @Override public void writeBindConstant(Binder binder, BindConstant command) {
            overriddenKeys.add(command.getKey());
            super.writeBindConstant(binder, command);
          }
        }.apply(binder(), overrideElements);

        // bind the regular module, skipping overridden keys. We only skip each
        // overridden key once, so things still blow up if the module binds the
        // same key multiple times
        new ModuleWriter() {
          @Override public <T> void writeBind(Binder binder, Binding<T> binding) {
            if (!overriddenKeys.remove(binding.getKey())) {
              super.writeBind(binder, binding);
            }
          }
          @Override public void writeBindConstant(Binder binder, BindConstant command) {
            if (!overriddenKeys.remove(command.getKey())) {
              super.writeBindConstant(binder, command);
            }
          }
        }.apply(binder(), elements);

        // TODO: bind the overridden keys using multibinder
      }
    };
  }
}
