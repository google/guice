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
import com.google.inject.PrivateBinder;
import com.google.inject.PrivateModule;
import com.google.inject.Scope;
import com.google.inject.internal.util.ImmutableSet;
import com.google.inject.internal.util.Iterables;
import com.google.inject.internal.util.Lists;
import com.google.inject.internal.util.Maps;
import com.google.inject.internal.util.Sets;
import com.google.inject.spi.DefaultBindingScopingVisitor;
import com.google.inject.spi.DefaultElementVisitor;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.google.inject.spi.PrivateElements;
import com.google.inject.spi.ScopeBinding;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.LinkedHashSet;
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
   * is kept. If a single {@link PrivateModule} is supplied or all elements are from
   * a single {@link PrivateBinder}, then this will overwrite the private bindings.
   * Otherwise, private bindings will not be overwritten unless they are exposed. 
   * This can be used to replace the bindings of a production module with test bindings:
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
   * is kept. If a single {@link PrivateModule} is supplied or all elements are from
   * a single {@link PrivateBinder}, then this will overwrite the private bindings.
   * Otherwise, private bindings will not be overwritten unless they are exposed. 
   * This can be used to replace the bindings of a production module with test bindings:
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
          Binder baseBinder = binder();
          List<Element> baseElements = Elements.getElements(baseModules);

          // If the sole element was a PrivateElements, we want to override
          // the private elements within that -- so refocus our elements
          // and binder.
          if(baseElements.size() == 1) {
            Element element = Iterables.getOnlyElement(baseElements);
            if(element instanceof PrivateElements) {
              PrivateElements privateElements = (PrivateElements)element;
              PrivateBinder privateBinder = baseBinder.newPrivateBinder().withSource(privateElements.getSource());
              for(Key exposed : privateElements.getExposedKeys()) {
                privateBinder.withSource(privateElements.getExposedSource(exposed)).expose(exposed);
              }
              baseBinder = privateBinder;
              baseElements = privateElements.getElements();
            }
          }
          
          final Binder binder = baseBinder;
          final LinkedHashSet<Element> elements = new LinkedHashSet<Element>(baseElements);
          final List<Element> overrideElements = Elements.getElements(overrides);

          final Set<Key<?>> overriddenKeys = Sets.newHashSet();
          final Set<Class<? extends Annotation>> overridesScopeAnnotations = Sets.newHashSet();

          // execute the overrides module, keeping track of which keys and scopes are bound
          new ModuleWriter(binder) {
            @Override public <T> Void visit(Binding<T> binding) {
              overriddenKeys.add(binding.getKey());
              return super.visit(binding);
            }

            @Override public Void visit(ScopeBinding scopeBinding) {
              overridesScopeAnnotations.add(scopeBinding.getAnnotationType());
              return super.visit(scopeBinding);
            }

            @Override public Void visit(PrivateElements privateElements) {
              overriddenKeys.addAll(privateElements.getExposedKeys());
              return super.visit(privateElements);
            }
          }.writeAll(overrideElements);

          // execute the original module, skipping all scopes and overridden keys. We only skip each
          // overridden binding once so things still blow up if the module binds the same thing
          // multiple times.
          final Map<Scope, Object> scopeInstancesInUse = Maps.newHashMap();
          final List<ScopeBinding> scopeBindings = Lists.newArrayList();
          new ModuleWriter(binder) {
            @Override public <T> Void visit(Binding<T> binding) {
              if (!overriddenKeys.remove(binding.getKey())) {
                super.visit(binding);

                // Record when a scope instance is used in a binding
                Scope scope = getScopeInstanceOrNull(binding);
                if (scope != null) {
                  scopeInstancesInUse.put(scope, binding.getSource());
                }
              }

              return null;
            }

            void rewrite(Binder binder, PrivateElements privateElements, Set<Key<?>> keysToSkip) {
              PrivateBinder privateBinder = binder.withSource(privateElements.getSource())
                  .newPrivateBinder();

              Set<Key<?>> skippedExposes = Sets.newHashSet();

              for (Key<?> key : privateElements.getExposedKeys()) {
                if (keysToSkip.remove(key)) {
                  skippedExposes.add(key);
                } else {
                  privateBinder.withSource(privateElements.getExposedSource(key)).expose(key);
                }
              }

              for (Element element : privateElements.getElements()) {
                if (element instanceof Binding
                    && skippedExposes.remove(((Binding) element).getKey())) {
                  continue;
                }
                if (element instanceof PrivateElements) {
                  rewrite(privateBinder, (PrivateElements) element, skippedExposes);
                  continue;
                }
                element.applyTo(privateBinder);
              }
            }

            @Override public Void visit(PrivateElements privateElements) {
              rewrite(binder, privateElements, overriddenKeys);
              return null;
            }

            @Override public Void visit(ScopeBinding scopeBinding) {
              scopeBindings.add(scopeBinding);
              return null;
            }
          }.writeAll(elements);

          // execute the scope bindings, skipping scopes that have been overridden. Any scope that
          // is overridden and in active use will prompt an error
          new ModuleWriter(binder) {
            @Override public Void visit(ScopeBinding scopeBinding) {
              if (!overridesScopeAnnotations.remove(scopeBinding.getAnnotationType())) {
                super.visit(scopeBinding);
              } else {
                Object source = scopeInstancesInUse.get(scopeBinding.getScope());
                if (source != null) {
                  binder.withSource(source).addError(
                      "The scope for @%s is bound directly and cannot be overridden.",
                      scopeBinding.getAnnotationType().getSimpleName());
                }
              }
              return null;
            }
          }.writeAll(scopeBindings);

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

  private static class ModuleWriter extends DefaultElementVisitor<Void> {
    protected final Binder binder;

    ModuleWriter(Binder binder) {
      this.binder = binder;
    }

    @Override protected Void visitOther(Element element) {
      element.applyTo(binder);
      return null;
    }

    void writeAll(Iterable<? extends Element> elements) {
      for (Element element : elements) {
        element.acceptVisitor(this);
      }
    }
  }
}
