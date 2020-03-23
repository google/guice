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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Iterables.getFirst;
import static com.google.inject.daggeradapter.SupportedAnnotations.isAnnotationSupported;

import com.google.common.base.MoreObjects;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimaps;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.internal.ProviderMethodsModule;
import com.google.inject.spi.ModuleAnnotatedMethodScanner;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Adapts classes annotated with {@link @dagger.Module} such that their {@link @dagger.Provides}
 * methods can be properly invoked by Guice to perform their provision operations.
 *
 * <p>Simple example:
 *
 * <pre>{@code
 * Guice.createInjector(
 *   DaggerAdapter.from(SomeDaggerModule.class, new AnotherModuleWithConstructor());
 * }</pre>
 *
 * <p>For modules with no instance binding methods, prefer using a class literal. If there are
 * instance binding methods, an instance of the module must be passed.
 *
 * <p>Any class literals specified by {@code dagger.Module(includes = ...)} transitively will be
 * included. Modules are de-duplicated, though multiple module instances of the same type is an
 * error. Specifying a module instance and a class literal is also an error.
 *
 * <p>Some notes on usage and compatibility.
 *
 * <ul>
 *   <li>Dagger provider methods have a "SET_VALUES" provision mode not supported by Guice.
 *   <li>MapBindings are not yet implemented (pending).
 *   <li>Be careful about stateful modules. In contrast to Dagger (where components are expected to
 *       be recreated on-demand with new Module instances), Guice typically has a single injector
 *       with a long lifetime, so your module instance will be used throughout the lifetime of the
 *       entire app.
 *   <li>Dagger 1.x uses {@link @Singleton} for all scopes, including shorter-lived scopes like
 *       per-request or per-activity. Using modules written with Dagger 1.x usage in mind may result
 *       in mis-scoped objects.
 *   <li>Dagger 2.x supports custom scope annotations, but for use in Guice, a custom scope
 *       implementation must be registered in order to support the custom lifetime of that
 *       annotation.
 * </ul>
 *
 * <p>If methods need to be ignored based on a condtion, a {@code Predicate<Method>} can be used
 * passed to {@link DaggerAdapter.Builder#filter}, as in {@code
 * DaggerAdapter.builder().addModules(...).filter(predicate).build()}. Only the methods which
 * satisfy the predicate will be processed.
 */
public final class DaggerAdapter {
  /** Creates a new {@link DaggerAdapter} from {@code daggerModuleObjects}. */
  public static Module from(Object... daggerModuleObjects) {
    return builder().addModules(ImmutableList.copyOf(daggerModuleObjects)).build();
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for setting configuration options on DaggerAdapter. */
  public static class Builder {
    private final ImmutableList.Builder<Object> modules = ImmutableList.builder();
    private Predicate<Method> predicate = Predicates.alwaysTrue();

    /** Returns a module that will configure bindings based on the modules & scanners. */
    public Module build() {
      return new DaggerCompatibilityModule(this);
    }

    /**
     * Adds modules (which can be classes annotated with {@link dagger.Module}, or instances of
     * those classes) which will be scanned for bindings.
     */
    public Builder addModules(Iterable<Object> daggerModuleObjects) {
      this.modules.addAll(daggerModuleObjects);
      return this;
    }

    /**
     * Limit the adapter to a subset of {@code methods} from {@link @dagger.Module} annotated
     * classes which satisfy the {@code predicate}. Defaults to allowing all.
     */
    public Builder filter(Predicate<Method> predicate) {
      this.predicate = checkNotNull(predicate, "predicate");
      return this;
    }
  }

  /**
   * A Module that adapts Dagger {@code @Module}-annotated types to contribute configuration to an
   * {@link com.google.inject.Injector} using a dagger-specific {@link
   * ModuleAnnotatedMethodScanner}.
   */
  private static final class DaggerCompatibilityModule implements Module {
    private final ImmutableList<Object> declaredModules;
    private final Predicate<Method> predicate;

    private DaggerCompatibilityModule(Builder builder) {
      this.declaredModules = builder.modules.build();
      this.predicate = builder.predicate;
    }

    @Override
    public void configure(Binder binder) {
      binder = binder.skipSources(getClass());
      ModuleAnnotatedMethodScanner scanner = DaggerMethodScanner.create(predicate);
      for (Object module : deduplicateModules(binder, transitiveModules())) {
        checkIsDaggerModule(module, binder);
        validateNoSubcomponents(binder, module);
        checkUnsupportedDaggerAnnotations(module, binder);

        binder.install(ProviderMethodsModule.forModule(module, scanner));
      }
    }

    private void checkIsDaggerModule(Object module, Binder binder) {
      Class<?> moduleClass = module instanceof Class ? (Class<?>) module : module.getClass();
      if (!moduleClass.isAnnotationPresent(dagger.Module.class)) {
        binder
            .skipSources(getClass())
            .addError("%s must be annotated with @dagger.Module", moduleClass.getCanonicalName());
      }
    }

    private static void checkUnsupportedDaggerAnnotations(Object module, Binder binder) {
      for (Method method : allDeclaredMethods(moduleClass(module))) {
        for (Annotation annotation : method.getAnnotations()) {
          Class<? extends Annotation> annotationClass = annotation.annotationType();
          if (annotationClass.getName().startsWith("dagger.")
              && !isAnnotationSupported(annotationClass)) {
            binder.addError(
                "%s is annotated with @%s which is not supported by DaggerAdapter",
                method, annotationClass.getCanonicalName());
          }
        }
      }
    }

    private static ImmutableList<Method> allDeclaredMethods(Class<?> clazz) {
      ImmutableList.Builder<Method> methods = ImmutableList.builder();
      for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
        methods.add(current.getDeclaredMethods());
      }
      return methods.build();
    }

    private void validateNoSubcomponents(Binder binder, Object module) {
      Class<?> moduleClass = module instanceof Class ? (Class<?>) module : module.getClass();
      dagger.Module moduleAnnotation = moduleClass.getAnnotation(dagger.Module.class);
      if (moduleAnnotation.subcomponents().length > 0) {
        binder.addError(
            "Subcomponents cannot be configured for modules used with DaggerAdapter. %s specifies:"
                + " %s",
            moduleClass, Arrays.toString(moduleAnnotation.subcomponents()));
      }
    }

    private ImmutableList<Object> transitiveModules() {
      ModuleTraversingQueue queue = new ModuleTraversingQueue();
      declaredModules.forEach(queue::add);

      while (!queue.isEmpty()) {
        dagger.Module module = queue.pop().getAnnotation(dagger.Module.class);
        if (module != null) {
          // invalid inputs are checked separately in checkIsDaggerModule()
          Arrays.asList(module.includes()).forEach(queue::add);
        }
      }

      return queue.transitiveModules();
    }

    private static ImmutableList<Object> deduplicateModules(
        Binder binder, ImmutableList<Object> transitiveModules) {
      ImmutableList.Builder<Object> deduplicatedModules = ImmutableList.builder();
      // Group modules by their module class to detect duplicates
      Multimaps.index(transitiveModules, DaggerAdapter::moduleClass)
          .asMap()
          .forEach(
              (moduleClass, duplicates) -> {
                // Select all module instances (i.e. ignore Class objects) and materialize into a
                // set so that they're deduplicated. If multiple conflicting module instances exist,
                // an error is reported since we don't know which one to use and they may have
                // instance state which is relevant to binding methods.
                ImmutableSet<Object> instances =
                    duplicates.stream()
                        .filter(module -> !(module instanceof Class))
                        .collect(toImmutableSet());
                if (instances.size() > 1) {
                  binder.addError(
                      "Duplicate module instances provided for %s: %s", moduleClass, instances);
                }

                // Prefer module instances to module Class objects. If no instance exists, take any
                // of `duplicates`, which should be identical since class instances are singletons
                deduplicatedModules.add(getFirst(instances, duplicates.iterator().next()));
              });

      return deduplicatedModules.build();
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("modules", declaredModules).toString();
    }
  }

  private static Class<?> moduleClass(Object module) {
    return module instanceof Class ? (Class<?>) module : module.getClass();
  }

  private static class ModuleTraversingQueue {
    private final Deque<Class<?>> queue = new ArrayDeque<>();
    private final Set<Object> visited = new HashSet<>();
    private final ImmutableList.Builder<Object> transitiveModules = ImmutableList.builder();

    void add(Object module) {
      if (visited.add(module)) {
        transitiveModules.add(module);
        queue.add(moduleClass(module));
      }
    }

    boolean isEmpty() {
      return queue.isEmpty();
    }

    Class<?> pop() {
      return queue.pop();
    }

    ImmutableList<Object> transitiveModules() {
      return transitiveModules.build();
    }
  }

  private DaggerAdapter() {}
}
