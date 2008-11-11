/**
 * Copyright (C) 2006 Google Inc.
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

import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import static com.google.inject.Scopes.SINGLETON;
import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;
import com.google.inject.internal.SourceProvider;
import com.google.inject.internal.Stopwatch;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.google.inject.spi.InjectionPoint;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Builds a dependency injection {@link Injector}. Injector construction happens in two phases.
 * <ol>
 *   <li>Static building. In this phase, we interpret commands, create bindings, and inspect 
 *     dependencies. During this phase, we hold a lock to ensure consistency with parent injectors.
 *     No user code is executed in this phase.</li>
 *   <li>Dynamic injection. In this phase, we call user code. We inject members that requested
 *     injection. This may require user's objects be created and their providers be called. And we
 *     create eager singletons. In this phase, user code may have started other threads. This phase
 *     is not executed for injectors created using {@link Stage#TOOL the tool stage}</li>
 * </ol>
 *
 * @author crazybob@google.com (Bob Lee)
 * @author jessewilson@google.com (Jesse Wilson)
 */
class InjectorBuilder {

  private final Stopwatch stopwatch = new Stopwatch();

  private InjectorImpl parent = null;
  private Stage stage;
  private final List<Module> modules = Lists.newLinkedList();

  private InjectorImpl injector;
  private Errors errors = new Errors();

  private final List<Element> elements = Lists.newArrayList();

  private BindingProcessor bindCommandProcesor;
  private InjectionRequestProcessor injectionCommandProcessor;

  /**
   * @param stage we're running in. If the stage is {@link Stage#PRODUCTION}, we will eagerly load
   * singletons.
   */
  InjectorBuilder stage(Stage stage) {
    this.stage = stage;
    return this;
  }

  InjectorBuilder parentInjector(InjectorImpl parent) {
    this.parent = parent;
    return this;
  }

  InjectorBuilder addModules(Iterable<? extends Module> modules) {
    for (Module module : modules) {
      this.modules.add(module);
    }
    return this;
  }

  Injector build() {
    if (injector != null) {
      throw new AssertionError("Already built, builders are not reusable.");
    }

    injector = new InjectorImpl(parent);

    buildStatically();

    // If we're in the tool stage, stop here. Don't eagerly inject or load anything.
    if (stage == Stage.TOOL) {
      return new ToolStageInjector(injector);
    }

    injectDynamically();

    return injector;
  }

  /**
   * Synchronize while we're building up the bindings and other injector state. This ensures that
   * the JIT bindings in the parent injector don't change while we're being built
   */
  void buildStatically() {
    synchronized (injector.state.lock()) {
      // bind Stage and Singleton if this is a top-level injector
      if (parent == null) {
        modules.add(0, new RootModule(stage));
      }

      elements.addAll(Elements.getElements(stage, modules));
      stopwatch.resetAndLog("Module execution");

      new MessageProcessor(errors).processCommands(elements);

      InterceptorBindingProcessor interceptorCommandProcessor
          = new InterceptorBindingProcessor(errors, injector.state);
      interceptorCommandProcessor.processCommands(elements);
      injector.constructionProxyFactory = interceptorCommandProcessor.createProxyFactory();
      stopwatch.resetAndLog("Interceptors creation");

      new ScopeBindingProcessor(errors, injector.state).processCommands(elements);
      stopwatch.resetAndLog("Scopes creation");

      new TypeConverterBindingProcessor(errors, injector.state).processCommands(elements);
      stopwatch.resetAndLog("Converters creation");

      bindInjector();
      bindLogger();
      bindCommandProcesor = new BindingProcessor(errors,
          injector, injector.state, injector.initializer);
      bindCommandProcesor.processCommands(elements);
      bindCommandProcesor.createUntargettedBindings();
      stopwatch.resetAndLog("Binding creation");

      injector.index();
      stopwatch.resetAndLog("Binding indexing");

      injectionCommandProcessor = new InjectionRequestProcessor(errors, injector.initializer);
      injectionCommandProcessor.processCommands(elements);
      stopwatch.resetAndLog("Static injection");

      validateStatically();

      if (!elements.isEmpty()) {
        throw new AssertionError("Failed to execute " + elements);
      }
    }
  }

  /** Validate everything that we can validate now that the injector is ready for use. */
  private void validateStatically() {
    bindCommandProcesor.runCreationListeners(injector);
    stopwatch.resetAndLog("Validation");

    injectionCommandProcessor.validate(injector);
    stopwatch.resetAndLog("Static validation");

    injector.initializer.validateOustandingInjections(errors);
    stopwatch.resetAndLog("Instance member validation");

    new ProviderLookupProcessor(errors, injector).processCommands(elements);
    stopwatch.resetAndLog("Provider verification");

    errors.throwCreationExceptionIfErrorsExist();
  }

  /**
   * Inject everything that can be injected. This method is intentionally not synchronized. If we
   * locked while injecting members (ie. running user code), things would deadlock should the user
   * code build a just-in-time binding from another thread.
   */
  private void injectDynamically() {
    injectionCommandProcessor.injectMembers(injector);
    stopwatch.resetAndLog("Static member injection");

    injector.initializer.injectAll(errors);
    stopwatch.resetAndLog("Instance injection");
    errors.throwCreationExceptionIfErrorsExist();

    loadEagerSingletons();
    stopwatch.resetAndLog("Preloading");
    errors.throwCreationExceptionIfErrorsExist();
  }

  public void loadEagerSingletons() {
    // load eager singletons, or all singletons if we're in Stage.PRODUCTION.
    // Bindings discovered while we're binding these singletons are not be eager.
    @SuppressWarnings("unchecked") // casting Collection<Binding> to Collection<BindingImpl> is safe
    Set<BindingImpl<?>> candidateBindings = ImmutableSet.copyOf(Iterables.concat(
        (Collection) injector.state.getExplicitBindingsThisLevel().values(),
        injector.jitBindings.values()));
    for (final BindingImpl<?> binding : candidateBindings) {
      if ((stage == Stage.PRODUCTION && binding.getScope() == SINGLETON)
          || binding.getLoadStrategy() == LoadStrategy.EAGER) {
        try {
          injector.callInContext(new ContextualCallable<Void>() {
            Dependency<?> dependency = Dependency.get(binding.key);
            public Void call(InternalContext context) {
              context.setDependency(dependency);
              Errors errorsForBinding = errors.withSource(dependency);
              try {
                binding.internalFactory.get(errorsForBinding, context, dependency);
              } catch (ErrorsException e) {
                errorsForBinding.merge(e.getErrors());
              } finally {
                context.setDependency(null);
              }

              return null;
            }
          });
        } catch (ErrorsException e) {
          throw new AssertionError();
        }
      }
    }
  }

  private static class RootModule implements Module {
    final Stage stage;

    private RootModule(Stage stage) {
      this.stage = checkNotNull(stage, "stage");
    }

    public void configure(Binder binder) {
      binder = binder.withSource(SourceProvider.UNKNOWN_SOURCE);
      binder.bind(Stage.class).toInstance(stage);
      binder.bindScope(Singleton.class, SINGLETON);
    }
  }

  /**
   * The Injector is a special case because we allow both parent and child injectors to both have
   * a binding for that key.
   */
  private void bindInjector() {
    Key<Injector> key = Key.get(Injector.class);
    InjectorFactory injectorFactory = new InjectorFactory(injector);
    injector.state.putBinding(key,
        new ProviderInstanceBindingImpl<Injector>(injector, key, SourceProvider.UNKNOWN_SOURCE,
            injectorFactory, Scopes.NO_SCOPE, injectorFactory, LoadStrategy.LAZY,
            ImmutableSet.<InjectionPoint>of()));
  }

  static class InjectorFactory implements  InternalFactory<Injector>, Provider<Injector> {
    private final Injector injector;

    private InjectorFactory(Injector injector) {
      this.injector = injector;
    }

    public Injector get(Errors errors, InternalContext context, Dependency<?> dependency)
        throws ErrorsException {
      return injector;
    }

    public Injector get() {
      return injector;
    }

    public String toString() {
      return "Provider<Injector>";
    }
  }

  /**
   * The Logger is a special case because it knows the injection point of the injected member. It's
   * the only binding that does this.
   */
  private void bindLogger() {
    Key<Logger> key = Key.get(Logger.class);
    LoggerFactory loggerFactory = new LoggerFactory();
    injector.state.putBinding(key,
        new ProviderInstanceBindingImpl<Logger>(injector, key,
            SourceProvider.UNKNOWN_SOURCE, loggerFactory, Scopes.NO_SCOPE,
            loggerFactory, LoadStrategy.LAZY, ImmutableSet.<InjectionPoint>of()));
  }

  static class LoggerFactory implements InternalFactory<Logger>, Provider<Logger> {
    public Logger get(Errors errors, InternalContext context, Dependency<?> dependency) {
      InjectionPoint injectionPoint = dependency.getInjectionPoint();
      return injectionPoint == null
          ? Logger.getAnonymousLogger()
          : Logger.getLogger(injectionPoint.getMember().getDeclaringClass().getName());
    }

    public Logger get() {
      return Logger.getAnonymousLogger();
    }

    public String toString() {
      return "Provider<Logger>";
    }
  }

  /** {@link Injector} exposed to users in {@link Stage#TOOL}. */
  static class ToolStageInjector implements Injector {
    private final Injector delegateInjector;
    
    ToolStageInjector(Injector delegateInjector) {
      this.delegateInjector = delegateInjector;
    }
    public void injectMembers(Object o) {
      throw new UnsupportedOperationException(
        "Injector.injectMembers(Object) is not supported in Stage.TOOL");
    }
    public Map<Key<?>, Binding<?>> getBindings() {
      return this.delegateInjector.getBindings();
    }
    public <T> Binding<T> getBinding(Key<T> key) {
      return this.delegateInjector.getBinding(key);
    }
    public <T> Binding<T> getBinding(Class<T> type) {
      return this.delegateInjector.getBinding(type);
    }
    public <T> List<Binding<T>> findBindingsByType(TypeLiteral<T> type) {
      return this.delegateInjector.findBindingsByType(type);
    }
    public Injector getParent() {
      return delegateInjector.getParent();
    }
    public Injector createChildInjector(Iterable<? extends Module> modules) {
      return delegateInjector.createChildInjector(modules);
    }
    public Injector createChildInjector(Module... modules) {
      return delegateInjector.createChildInjector(modules);
    }
    public <T> Provider<T> getProvider(Key<T> key) {
      throw new UnsupportedOperationException(
        "Injector.getProvider(Key<T>) is not supported in Stage.TOOL");
    }
    public <T> Provider<T> getProvider(Class<T> type) {
      throw new UnsupportedOperationException(
        "Injector.getProvider(Class<T>) is not supported in Stage.TOOL");
    }
    public <T> T getInstance(Key<T> key) {
      throw new UnsupportedOperationException(
        "Injector.getInstance(Key<T>) is not supported in Stage.TOOL");
    }
    public <T> T getInstance(Class<T> type) {
      throw new UnsupportedOperationException(
        "Injector.getInstance(Class<T>) is not supported in Stage.TOOL");
    }
  }
}
