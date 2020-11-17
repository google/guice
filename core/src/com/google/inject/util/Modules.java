/*
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.PrivateBinder;
import com.google.inject.PrivateModule;
import com.google.inject.Scope;
import com.google.inject.internal.Errors;
import com.google.inject.spi.DefaultBindingScopingVisitor;
import com.google.inject.spi.DefaultElementVisitor;
import com.google.inject.spi.Element;
import com.google.inject.spi.ElementVisitor;
import com.google.inject.spi.Elements;
import com.google.inject.spi.ModuleAnnotatedMethodScannerBinding;
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

  public static final Module EMPTY_MODULE = new EmptyModule();

  private static class EmptyModule implements Module {
    @Override
    public void configure(Binder binder) {}
  }

  /**
   * Returns a builder that creates a module that overlays override modules over the given modules.
   * If a key is bound in both sets of modules, only the binding from the override modules is kept.
   * If a single {@link PrivateModule} is supplied or all elements are from a single {@link
   * PrivateBinder}, then this will overwrite the private bindings. Otherwise, private bindings will
   * not be overwritten unless they are exposed. This can be used to replace the bindings of a
   * production module with test bindings:
   *
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
    return override(Arrays.asList(modules));
  }

  /** @deprecated there's no reason to use {@code Modules.override()} without any arguments. */
  @Deprecated
  public static OverriddenModuleBuilder override() {
    return override(Arrays.asList());
  }

  /**
   * Returns a builder that creates a module that overlays override modules over the given modules.
   * If a key is bound in both sets of modules, only the binding from the override modules is kept.
   * If a single {@link PrivateModule} is supplied or all elements are from a single {@link
   * PrivateBinder}, then this will overwrite the private bindings. Otherwise, private bindings will
   * not be overwritten unless they are exposed. This can be used to replace the bindings of a
   * production module with test bindings:
   *
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
   *
   * <p>Although sometimes helpful, this method is rarely necessary. Most Guice APIs accept multiple
   * arguments or (like {@code install()}) can be called repeatedly. Where possible, external APIs
   * that require a single module should similarly be adapted to permit multiple modules.
   */
  public static Module combine(Module... modules) {
    return combine(ImmutableSet.copyOf(modules));
  }

  /** @deprecated there's no need to "combine" one module; just install it directly. */
  @Deprecated
  public static Module combine(Module module) {
    return module;
  }

  /** @deprecated this method call is effectively a no-op, just remove it. */
  @Deprecated
  public static Module combine() {
    return EMPTY_MODULE;
  }

  /**
   * Returns a new module that installs all of {@code modules}.
   *
   * <p>Although sometimes helpful, this method is rarely necessary. Most Guice APIs accept multiple
   * arguments or (like {@code install()}) can be called repeatedly. Where possible, external APIs
   * that require a single module should similarly be adapted to permit multiple modules.
   */
  public static Module combine(Iterable<? extends Module> modules) {
    return new CombinedModule(modules);
  }

  private static class CombinedModule implements Module {
    final Set<Module> modulesSet;

    CombinedModule(Iterable<? extends Module> modules) {
      this.modulesSet = ImmutableSet.copyOf(modules);
    }

    @Override
    public void configure(Binder binder) {
      binder = binder.skipSources(getClass());
      for (Module module : modulesSet) {
        binder.install(module);
      }
    }
  }

  /** See the EDSL example at {@link Modules#override(Module[]) override()}. */
  public interface OverriddenModuleBuilder {

    /** See the EDSL example at {@link Modules#override(Module[]) override()}. */
    Module with(Module... overrides);

    /** @deprecated there's no reason to use {@code .with()} without any arguments. */
    @Deprecated
    public Module with();

    /** See the EDSL example at {@link Modules#override(Module[]) override()}. */
    Module with(Iterable<? extends Module> overrides);
  }

  private static final class RealOverriddenModuleBuilder implements OverriddenModuleBuilder {
    private final ImmutableSet<Module> baseModules;

    // TODO(diamondm) checkArgument(!baseModules.isEmpty())?
    private RealOverriddenModuleBuilder(Iterable<? extends Module> baseModules) {
      this.baseModules = ImmutableSet.copyOf(baseModules);
    }

    @Override
    public Module with(Module... overrides) {
      return with(Arrays.asList(overrides));
    }

    @Override
    public Module with() {
      return with(Arrays.asList());
    }

    @Override
    public Module with(Iterable<? extends Module> overrides) {
      return new OverrideModule(overrides, baseModules);
    }
  }

  static class OverrideModule extends AbstractModule {
    private final ImmutableSet<Module> overrides;
    private final ImmutableSet<Module> baseModules;

    // TODO(diamondm) checkArgument(!overrides.isEmpty())?
    OverrideModule(Iterable<? extends Module> overrides, ImmutableSet<Module> baseModules) {
      this.overrides = ImmutableSet.copyOf(overrides);
      this.baseModules = baseModules;
    }

    @Override
    public void configure() {
      Binder baseBinder = binder();
      List<Element> baseElements = Elements.getElements(currentStage(), baseModules);

      // If the sole element was a PrivateElements, we want to override
      // the private elements within that -- so refocus our elements
      // and binder.
      if (baseElements.size() == 1) {
        Element element = Iterables.getOnlyElement(baseElements);
        if (element instanceof PrivateElements) {
          PrivateElements privateElements = (PrivateElements) element;
          PrivateBinder privateBinder =
              baseBinder.newPrivateBinder().withSource(privateElements.getSource());
          for (Key<?> exposed : privateElements.getExposedKeys()) {
            privateBinder.withSource(privateElements.getExposedSource(exposed)).expose(exposed);
          }
          baseBinder = privateBinder;
          baseElements = privateElements.getElements();
        }
      }

      final Binder binder = baseBinder.skipSources(this.getClass());
      final LinkedHashSet<Element> elements = new LinkedHashSet<>(baseElements);
      final Module scannersModule = extractScanners(elements);
      final List<Element> overrideElements =
          Elements.getElements(
              currentStage(),
              ImmutableList.<Module>builder().addAll(overrides).add(scannersModule).build());

      final Set<Key<?>> overriddenKeys = Sets.newHashSet();
      final Map<Class<? extends Annotation>, ScopeBinding> overridesScopeAnnotations =
          Maps.newHashMap();

      // execute the overrides module, keeping track of which keys and scopes are bound
      new ModuleWriter(binder) {
        @Override
        public <T> Void visit(Binding<T> binding) {
          overriddenKeys.add(binding.getKey());
          return super.visit(binding);
        }

        @Override
        public Void visit(ScopeBinding scopeBinding) {
          overridesScopeAnnotations.put(scopeBinding.getAnnotationType(), scopeBinding);
          return super.visit(scopeBinding);
        }

        @Override
        public Void visit(PrivateElements privateElements) {
          overriddenKeys.addAll(privateElements.getExposedKeys());
          return super.visit(privateElements);
        }
      }.writeAll(overrideElements);

      // execute the original module, skipping all scopes and overridden keys. We only skip each
      // overridden binding once so things still blow up if the module binds the same thing
      // multiple times.
      final Map<Scope, List<Object>> scopeInstancesInUse = Maps.newHashMap();
      final List<ScopeBinding> scopeBindings = Lists.newArrayList();
      new ModuleWriter(binder) {
        @Override
        public <T> Void visit(Binding<T> binding) {
          if (!overriddenKeys.remove(binding.getKey())) {
            super.visit(binding);

            // Record when a scope instance is used in a binding
            Scope scope = getScopeInstanceOrNull(binding);
            if (scope != null) {
              scopeInstancesInUse
                  .computeIfAbsent(scope, k -> Lists.newArrayList())
                  .add(binding.getSource());
            }
          }

          return null;
        }

        void rewrite(Binder binder, PrivateElements privateElements, Set<Key<?>> keysToSkip) {
          PrivateBinder privateBinder =
              binder.withSource(privateElements.getSource()).newPrivateBinder();

          Set<Key<?>> skippedExposes = Sets.newHashSet();

          for (Key<?> key : privateElements.getExposedKeys()) {
            if (keysToSkip.remove(key)) {
              skippedExposes.add(key);
            } else {
              privateBinder.withSource(privateElements.getExposedSource(key)).expose(key);
            }
          }

          for (Element element : privateElements.getElements()) {
            if (element instanceof Binding && skippedExposes.remove(((Binding) element).getKey())) {
              continue;
            }
            if (element instanceof PrivateElements) {
              rewrite(privateBinder, (PrivateElements) element, skippedExposes);
              continue;
            }
            element.applyTo(privateBinder);
          }
        }

        @Override
        public Void visit(PrivateElements privateElements) {
          rewrite(binder, privateElements, overriddenKeys);
          return null;
        }

        @Override
        public Void visit(ScopeBinding scopeBinding) {
          scopeBindings.add(scopeBinding);
          return null;
        }
      }.writeAll(elements);

      // execute the scope bindings, skipping scopes that have been overridden. Any scope that
      // is overridden and in active use will prompt an error
      new ModuleWriter(binder) {
        @Override
        public Void visit(ScopeBinding scopeBinding) {
          ScopeBinding overideBinding =
              overridesScopeAnnotations.remove(scopeBinding.getAnnotationType());
          if (overideBinding == null) {
            super.visit(scopeBinding);
          } else {
            List<Object> usedSources = scopeInstancesInUse.get(scopeBinding.getScope());
            if (usedSources != null) {
              StringBuilder sb =
                  new StringBuilder(
                      "The scope for @%s is bound directly and cannot be overridden.");
              sb.append("%n     original binding at " + Errors.convert(scopeBinding.getSource()));
              for (Object usedSource : usedSources) {
                sb.append("%n     bound directly at " + Errors.convert(usedSource) + "");
              }
              binder
                  .withSource(overideBinding.getSource())
                  .addError(sb.toString(), scopeBinding.getAnnotationType().getSimpleName());
            }
          }
          return null;
        }
      }.writeAll(scopeBindings);
    }

    private Scope getScopeInstanceOrNull(Binding<?> binding) {
      return binding.acceptScopingVisitor(
          new DefaultBindingScopingVisitor<Scope>() {
            @Override
            public Scope visitScope(Scope scope) {
              return scope;
            }
          });
    }
  }

  private static class ModuleWriter extends DefaultElementVisitor<Void> {
    protected final Binder binder;

    ModuleWriter(Binder binder) {
      this.binder = binder.skipSources(this.getClass());
    }

    @Override
    protected Void visitOther(Element element) {
      element.applyTo(binder);
      return null;
    }

    void writeAll(Iterable<? extends Element> elements) {
      for (Element element : elements) {
        element.acceptVisitor(this);
      }
    }
  }

  private static Module extractScanners(Iterable<Element> elements) {
    final List<ModuleAnnotatedMethodScannerBinding> scanners = Lists.newArrayList();
    ElementVisitor<Void> visitor =
        new DefaultElementVisitor<Void>() {
          @Override
          public Void visit(ModuleAnnotatedMethodScannerBinding binding) {
            scanners.add(binding);
            return null;
          }
        };
    for (Element element : elements) {
      element.acceptVisitor(visitor);
    }
    return new AbstractModule() {
      @Override
      protected void configure() {
        for (ModuleAnnotatedMethodScannerBinding scanner : scanners) {
          scanner.applyTo(binder());
        }
      }
    };
  }

  /**
   * Returns a module that will configure the injector to require explicit bindings.
   *
   * @since 4.2.3
   */
  public static Module requireExplicitBindingsModule() {
    return new RequireExplicitBindingsModule();
  }

  private static final class RequireExplicitBindingsModule implements Module {
    @Override
    public void configure(Binder binder) {
      binder.requireExplicitBindings();
    }
  }

  /**
   * Returns a module that will configure the injector to require {@literal @}{@link Inject} on
   * constructors.
   *
   * @since 4.2.3
   * @see Binder#requireAtInjectOnConstructors
   */
  public static Module requireAtInjectOnConstructorsModule() {
    return new RequireAtInjectOnConstructorsModule();
  }

  private static final class RequireAtInjectOnConstructorsModule implements Module {
    @Override
    public void configure(Binder binder) {
      binder.requireAtInjectOnConstructors();
    }
  }

  /**
   * Returns a module that will configure the injector to require an exactly matching binding
   * annotation.
   *
   * @since 4.2.3
   * @see Binder#requireExactBindingAnnotations
   */
  public static Module requireExactBindingAnnotationsModule() {
    return new RequireExactBindingAnnotationsModule();
  }

  private static final class RequireExactBindingAnnotationsModule implements Module {
    @Override
    public void configure(Binder binder) {
      binder.requireExactBindingAnnotations();
    }
  }

  /**
   * Returns a module that will configure the injector to disable circular proxies.
   *
   * @since 4.2.3
   */
  public static Module disableCircularProxiesModule() {
    return new DisableCircularProxiesModule();
  }

  private static final class DisableCircularProxiesModule implements Module {
    @Override
    public void configure(Binder binder) {
      binder.disableCircularProxies();
    }
  }
}
