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

package com.google.inject.util;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.google.inject.spi.ModuleWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Static utility methods for creating and working with instances of {@link Module}.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class Modules {
  private Modules() {}

  public static final Module EMPTY_MODULE = new Module() {
    public void configure(Binder binder) {}
  };

  /**
   * Returns a builder that creates a module that overlays override modules over the given
   * modules. If a key is bound in both sets of modules, only the binding from the override modules
   * is kept. This can be used to replace the bindings of a production module with test bindings:
   * <pre>
   * Module functionalTestModule
   *     = Modules.override(new ProductionModule()).with(new TestModule());
   * </pre>
   *
   * <p>Prefer to write smaller modules that can be reused and tested without overrides.
   *
   * @param modules the modules whose bindings are open to be overridden
   */
  public static OverriddenModuleBuilder override(Module... modules) {
    return new RealOverriddenModuleBuilder(Arrays.asList(modules));
  }

  /**
   * Returns a builder that creates a module that overlays override modules over the given
   * modules. If a key is bound in both sets of modules, only the binding from the override modules
   * is kept. This can be used to replace the bindings of a production module with test bindings:
   * <pre>
   * Module functionalTestModule
   *     = Modules.override(getProductionModules()).with(getTestModules());
   * </pre>
   *
   * <p>Prefer to write smaller modules that can be reused and tested without overrides.
   *
   * @param modules the modules whose bindings are open to be overridden
   */
  public static OverriddenModuleBuilder override(Iterable<? extends Module> modules) {
    return new RealOverriddenModuleBuilder(modules);
  }

  /**
   * See the EDSL example at {@link Modules#override(Module[]) override()}.
   */
  public interface OverriddenModuleBuilder {

    /**
     * See the EDSL example at {@link Modules#override(Module[]) override()}.
     */
    Module with(Module... overrides);

    /**
     * See the EDSL example at {@link Modules#override(Module[]) override()}.
     */
    Module with(Iterable<? extends Module> overrides);
  }

  private static final class RealOverriddenModuleBuilder implements OverriddenModuleBuilder {
    private final ImmutableSet<Module> baseModules;

    private RealOverriddenModuleBuilder(Iterable<? extends Module> baseModules) {
      this.baseModules = ImmutableSet.copyOf(baseModules);
    }

    public Module with(Module... overrides) {
      return with(Arrays.asList(overrides));
    }

    public Module with(Iterable<? extends Module> overrides) {
      final List<Element> elements = Elements.getElements(baseModules);
      final List<Element> overrideElements = Elements.getElements(overrides);

      return new AbstractModule() {
        public void configure() {
          final Set<Key> overriddenKeys = Sets.newHashSet();

          // execute the overrides module, keeping track of which keys were bound
          new ModuleWriter() {
            @Override public <T> void writeBind(Binder binder, Binding<T> binding) {
              overriddenKeys.add(binding.getKey());
              super.writeBind(binder, binding);
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
          }.apply(binder(), elements);

          // TODO: bind the overridden keys using multibinder
        }
      };
    }
  }
}
