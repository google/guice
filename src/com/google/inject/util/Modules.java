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

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Scope;
import com.google.inject.internal.ImmutableSet;
import com.google.inject.internal.Lists;
import com.google.inject.internal.Maps;
import com.google.inject.internal.Sets;
import com.google.inject.spi.DefaultBindingScopingVisitor;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.google.inject.spi.ModuleWriter;
import com.google.inject.spi.ScopeBinding;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Static utility methods for creating and working with instances of {@link Module}.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 * @since 2.0
 */
public final class Modules {
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
   * Returns a new module that installs all of {@code modules}.
   */
  public static Module combine(Module... modules) {
    return combine(ImmutableSet.of(modules));
  }

  /**
   * Returns a new module that installs all of {@code modules}.
   */
  public static Module combine(Iterable<? extends Module> modules) {
    final Set<Module> modulesSet = ImmutableSet.copyOf(modules);
    return new Module() {
      public void configure(Binder binder) {
        binder = binder.skipSources(getClass());
        for (Module module : modulesSet) {
          binder.install(module);
        }
      }
    };
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

    public Module with(final Iterable<? extends Module> overrides) {
      return new AbstractModule() {
        @Override
        public void configure() {
          final List<Element> elements = Elements.getElements(baseModules);
          final List<Element> overrideElements = Elements.getElements(overrides);

          final Set<Key> overriddenKeys = Sets.newHashSet();
          final Set<Class<? extends Annotation>> overridesScopeAnnotations = Sets.newHashSet();

          // execute the overrides module, keeping track of which keys and scopes are bound
          new ModuleWriter() {
            @Override public <T> void writeBind(Binder binder, Binding<T> binding) {
              overriddenKeys.add(binding.getKey());
              super.writeBind(binder, binding);
            }

            @Override public void writeBindScope(Binder binder, ScopeBinding element) {
              overridesScopeAnnotations.add(element.getAnnotationType());
              super.writeBindScope(binder, element);
            }
          }.apply(binder(), overrideElements);

          // execute the original module, skipping all scopes and overridden keys. We only skip each
          // overridden binding once so things still blow up if the module binds the same thing
          // multiple times.
          final Map<Scope, Object> scopeInstancesInUse = Maps.newHashMap();
          final List<ScopeBinding> scopeBindings = Lists.newArrayList();
          new ModuleWriter() {
            @Override public <T> void writeBind(Binder binder, final Binding<T> binding) {
              if (!overriddenKeys.remove(binding.getKey())) {
                super.writeBind(binder, binding);

                // Record when a scope instance is used in a binding
                Scope scope = getScopeInstanceOrNull(binding);
                if (scope != null) {
                  scopeInstancesInUse.put(scope, binding.getSource());
                }
              }
            }

            @Override public void writeBindScope(Binder binder, ScopeBinding element) {
              scopeBindings.add(element);
            }
          }.apply(binder(), elements);

          // execute the scope bindings, skipping scopes that have been overridden. Any scope that
          // is overridden and in active use will prompt an error
          new ModuleWriter() {
            @Override public void writeBindScope(Binder binder, ScopeBinding element) {
              if (!overridesScopeAnnotations.remove(element.getAnnotationType())) {
                super.writeBindScope(binder, element);
              } else {
                Object source = scopeInstancesInUse.get(element.getScope());
                if (source != null) {
                  binder().withSource(source).addError(
                      "The scope for @%s is bound directly and cannot be overridden.",
                      element.getAnnotationType().getSimpleName());
                }
              }
            }
          }.apply(binder(), scopeBindings);

          // TODO: bind the overridden keys using multibinder
        }

        private Scope getScopeInstanceOrNull(Binding<?> binding) {
          return binding.acceptScopingVisitor(new DefaultBindingScopingVisitor<Scope>() {
            public Scope visitScope(Scope scope) {
              return scope;
            }
          });
        }
      };
    }
  }
}
