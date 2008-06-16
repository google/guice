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
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.inject.Reflection.Factory;
import static com.google.inject.Scopes.SINGLETON;
import com.google.inject.commands.Command;
import com.google.inject.commands.CommandRecorder;
import com.google.inject.commands.FutureInjector;
import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;
import com.google.inject.internal.Stopwatch;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.SourceProviders;
import java.lang.reflect.Member;
import java.util.List;
import java.util.logging.Logger;

/**
 * Builds a dependency injection {@link Injector}.
 *
 * @author crazybob@google.com (Bob Lee)
 * @author jessewilson@google.com (Jesse Wilson)
 */
class InjectorBuilder {

  private final Stopwatch stopwatch = new Stopwatch();

  private Injector parent;
  private Stage stage;
  private Factory reflectionFactory = new RuntimeReflectionFactory();
  private final List<Module> modules = Lists.newLinkedList();

  private InjectorImpl injector;
  private Errors errors = new Errors();

  private final FutureInjector futureInjector = new FutureInjector();
  private final List<Command> commands = Lists.newArrayList();

  private BindCommandProcessor bindCommandProcesor;
  private RequestStaticInjectionCommandProcessor requestStaticInjectionCommandProcessor;

  /**
   * @param stage we're running in. If the stage is {@link Stage#PRODUCTION}, we will eagerly load
   * singletons.
   */
  InjectorBuilder stage(Stage stage) {
    this.stage = stage;
    return this;
  }

  InjectorBuilder usingReflectionFactory(Factory reflectionFactory) {
    this.reflectionFactory = reflectionFactory;
    return this;
  }

  InjectorBuilder parentInjector(Injector parent) {
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

    modules.add(0, new BuiltInModule(injector, stage));

    CommandRecorder commandRecorder = new CommandRecorder(futureInjector);
    commandRecorder.setCurrentStage(stage);
    commands.addAll(commandRecorder.recordCommands(modules));

    buildCoreInjector();

    validate();

    errors.throwCreationExceptionIfErrorsExist();

    // If we're in the tool stage, stop here. Don't eagerly inject or load
    // anything.
    if (stage == Stage.TOOL) {
      // TODO: Wrap this and prevent usage of anything besides getBindings().
      return injector;
    }

    fulfillInjectionRequests();

    if (!commands.isEmpty()) {
      throw new AssertionError("Failed to execute " + commands);
    }

    return injector;
  }

  /** Builds the injector. */
  private void buildCoreInjector() {
    new ErrorsCommandProcessor(errors)
        .processCommands(commands);

    BindInterceptorCommandProcessor bindInterceptorCommandProcessor
        = new BindInterceptorCommandProcessor(errors);
    bindInterceptorCommandProcessor.processCommands(commands);
    ConstructionProxyFactory proxyFactory = bindInterceptorCommandProcessor.createProxyFactory();
    injector.reflection = reflectionFactory.create(proxyFactory);
    stopwatch.resetAndLog("Interceptors creation");

    new ScopesCommandProcessor(errors, injector.scopes).processCommands(commands);
    stopwatch.resetAndLog("Scopes creation");

    new ConvertToTypesCommandProcessor(errors, injector.converters).processCommands(commands);
    stopwatch.resetAndLog("Converters creation");

    bindLogger();
    bindCommandProcesor = new BindCommandProcessor(errors,
        injector, injector.scopes, injector.explicitBindings,
        injector.outstandingInjections);
    bindCommandProcesor.processCommands(commands);
    bindCommandProcesor.createUntargettedBindings();
    stopwatch.resetAndLog("Binding creation");

    injector.index();
    stopwatch.resetAndLog("Binding indexing");

    requestStaticInjectionCommandProcessor
        = new RequestStaticInjectionCommandProcessor(errors);
    requestStaticInjectionCommandProcessor
        .processCommands(commands);
    stopwatch.resetAndLog("Static injection");
  }

  /** Validate everything that we can validate now that the injector is ready for use. */
  private void validate() {
    bindCommandProcesor.runCreationListeners(injector);
    stopwatch.resetAndLog("Validation");

    requestStaticInjectionCommandProcessor.validate(injector);
    stopwatch.resetAndLog("Static validation");

    injector.validateOustandingInjections(errors);
    stopwatch.resetAndLog("Instance member validation");

    new GetProviderProcessor(errors, injector).processCommands(commands);
    stopwatch.resetAndLog("Provider verification");

    errors.throwCreationExceptionIfErrorsExist();
  }

  /** Inject everything that can be injected. This uses runtime error handling. */
  private void fulfillInjectionRequests() {
    futureInjector.initialize(injector);

    requestStaticInjectionCommandProcessor.injectMembers(injector);
    stopwatch.resetAndLog("Static member injection");
    injector.fulfillOutstandingInjections(errors);
    stopwatch.resetAndLog("Instance injection");

    loadEagerSingletons();
    stopwatch.resetAndLog("Preloading");
  }

  public void loadEagerSingletons() {
    // load eager singletons, or all singletons if we're in Stage.PRODUCTION.
    for (final BindingImpl<?> binding
        : Iterables.concat(injector.explicitBindings.values(), injector.jitBindings.values())) {
      if ((stage == Stage.PRODUCTION && binding.getScope() == Scopes.SINGLETON)
          || binding.getLoadStrategy() == LoadStrategy.EAGER) {
        try {
          injector.callInContext(new ContextualCallable<Void>() {
            public Void call(InternalContext context) {
              InjectionPoint<?> injectionPoint = InjectionPoint.newInstance(binding.key);
              context.setInjectionPoint(injectionPoint);
              errors.pushInjectionPoint(injectionPoint);
              try {
                binding.internalFactory.get(errors, context, injectionPoint);
              } catch (ErrorsException e) {
                errors.merge(e.getErrors());
              } finally {
                context.setInjectionPoint(null);
                errors.popInjectionPoint(injectionPoint);
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

  private static class BuiltInModule extends AbstractModule {
    final Injector injector;
    final Stage stage;

    private BuiltInModule(Injector injector, Stage stage) {
      this.injector = checkNotNull(injector, "injector");
      this.stage = checkNotNull(stage, "stage");
    }

    protected void configure() {
      SourceProviders.withDefault(SourceProviders.UNKNOWN_SOURCE, new Runnable() {
        public void run() {
          bind(Stage.class).toInstance(stage);
          bindScope(Singleton.class, SINGLETON);
          // Create default bindings.
          // We use toProvider() instead of toInstance() to avoid infinite recursion
          // in toString().
          bind(Injector.class).toProvider(new InjectorProvider(injector));

        }
      });
    }

    class InjectorProvider implements Provider<Injector> {
      final Injector injector;

      InjectorProvider(Injector injector) {
        this.injector = injector;
      }

      public Injector get() {
        return injector;
      }

      public String toString() {
        return "Provider<Injector>";
      }
    }
  }

  /**
   * The Logger is a special case because it knows the injection point of the injected member. It's
   * the only binding that does this.
   */
  private void bindLogger() {
    Key<Logger> key = Key.get(Logger.class);
    LoggerFactory loggerFactory = new LoggerFactory();
    injector.explicitBindings.put(key,
        new ProviderInstanceBindingImpl<Logger>(injector, key,
            SourceProviders.UNKNOWN_SOURCE, loggerFactory, Scopes.NO_SCOPE,
            loggerFactory, LoadStrategy.LAZY));
  }

  static class LoggerFactory implements InternalFactory<Logger>, Provider<Logger> {
    public Logger get(Errors errors, InternalContext context, InjectionPoint<?> injectionPoint) {
      Member member = injectionPoint.getMember();
      return member == null
          ? Logger.getAnonymousLogger()
          : Logger.getLogger(member.getDeclaringClass().getName());
    }

    public Logger get() {
      return Logger.getAnonymousLogger();
    }

    public String toString() {
      return "Provider<Logger>";
    }
  }

}
