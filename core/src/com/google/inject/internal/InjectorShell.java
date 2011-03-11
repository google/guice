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

package com.google.inject.internal;

import com.google.inject.Binder;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import static com.google.inject.Scopes.SINGLETON;
import com.google.inject.Singleton;
import com.google.inject.Stage;
import com.google.inject.internal.InjectorImpl.InjectorOptions;
import com.google.inject.internal.util.ImmutableSet;
import com.google.inject.internal.util.Lists;
import static com.google.inject.internal.util.Preconditions.checkNotNull;
import static com.google.inject.internal.util.Preconditions.checkState;
import com.google.inject.internal.util.SourceProvider;
import com.google.inject.internal.util.Stopwatch;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.PrivateElements;
import com.google.inject.spi.TypeListenerBinding;
import java.util.List;
import java.util.logging.Logger;

/**
 * A partially-initialized injector. See {@link InternalInjectorCreator}, which
 * uses this to build a tree of injectors in batch.
 * 
 * @author jessewilson@google.com (Jesse Wilson)
 */
final class InjectorShell {

  private final List<Element> elements;
  private final InjectorImpl injector;

  private InjectorShell(Builder builder, List<Element> elements, InjectorImpl injector) {
    this.elements = elements;
    this.injector = injector;
  }

  InjectorImpl getInjector() {
    return injector;
  }

  List<Element> getElements() {
    return elements;
  }

  static class Builder {
    private final List<Element> elements = Lists.newArrayList();
    private final List<Module> modules = Lists.newArrayList();

    /** lazily constructed */
    private State state;

    private InjectorImpl parent;
    private InjectorOptions options;
    private Stage stage;

    /** null unless this exists in a {@link Binder#newPrivateBinder private environment} */
    private PrivateElementsImpl privateElements;
    
    Builder stage(Stage stage) {
      this.stage = stage;
      return this;
    }

    Builder parent(InjectorImpl parent) {
      this.parent = parent;
      this.state = new InheritingState(parent.state);
      this.options = parent.options;
      this.stage = options.stage;
      return this;
    }

    Builder privateElements(PrivateElements privateElements) {
      this.privateElements = (PrivateElementsImpl) privateElements;
      this.elements.addAll(privateElements.getElements());
      return this;
    }

    void addModules(Iterable<? extends Module> modules) {
      for (Module module : modules) {
        this.modules.add(module);
      }
    }
    
    Stage getStage() {
      return options.stage;
    }

    /** Synchronize on this before calling {@link #build}. */
    Object lock() {
      return getState().lock();
    }

    /**
     * Creates and returns the injector shells for the current modules. Multiple shells will be
     * returned if any modules contain {@link Binder#newPrivateBinder private environments}. The
     * primary injector will be first in the returned list.
     */
    List<InjectorShell> build(
        Initializer initializer,
        ProcessedBindingData bindingData,
        Stopwatch stopwatch,
        Errors errors) {
      checkState(stage != null, "Stage not initialized");
      checkState(privateElements == null || parent != null, "PrivateElements with no parent");
      checkState(state != null, "no state. Did you remember to lock() ?");

      // bind Stage and Singleton if this is a top-level injector
      if (parent == null) {
        modules.add(0, new RootModule(stage));
      }
      elements.addAll(Elements.getElements(stage, modules));
      
      // Look for injector-changing options
      InjectorOptionsProcessor optionsProcessor = new InjectorOptionsProcessor(errors);
      optionsProcessor.process(null, elements);
      options = optionsProcessor.getOptions(stage, options);
      
      InjectorImpl injector = new InjectorImpl(parent, state, options);
      if (privateElements != null) {
        privateElements.initInjector(injector);
      }

      // add default type converters if this is a top-level injector
      if (parent == null) {
        new TypeConverterBindingProcessor(errors).prepareBuiltInConverters(injector);
      }

      stopwatch.resetAndLog("Module execution");

      new MessageProcessor(errors).process(injector, elements);

      /*if[AOP]*/
      InterceptorBindingProcessor interceptors = new InterceptorBindingProcessor(errors);
      interceptors.process(injector, elements);
      stopwatch.resetAndLog("Interceptors creation");
      /*end[AOP]*/

      new TypeListenerBindingProcessor(errors).process(injector, elements);
      List<TypeListenerBinding> listenerBindings = injector.state.getTypeListenerBindings();
      injector.membersInjectorStore = new MembersInjectorStore(injector, listenerBindings);
      stopwatch.resetAndLog("TypeListeners creation");

      new ScopeBindingProcessor(errors).process(injector, elements);
      stopwatch.resetAndLog("Scopes creation");

      new TypeConverterBindingProcessor(errors).process(injector, elements);
      stopwatch.resetAndLog("Converters creation");

      bindInjector(injector);
      bindLogger(injector);
      
      // Process all normal bindings, then UntargettedBindings.
      // This is necessary because UntargettedBindings can create JIT bindings
      // and need all their other dependencies set up ahead of time.
      new BindingProcessor(errors, initializer, bindingData).process(injector, elements);
      new UntargettedBindingProcessor(errors, bindingData).process(injector, elements);
      stopwatch.resetAndLog("Binding creation");

      List<InjectorShell> injectorShells = Lists.newArrayList();
      injectorShells.add(new InjectorShell(this, elements, injector));

      // recursively build child shells
      PrivateElementProcessor processor = new PrivateElementProcessor(errors);
      processor.process(injector, elements);
      for (Builder builder : processor.getInjectorShellBuilders()) {
        injectorShells.addAll(builder.build(initializer, bindingData, stopwatch, errors));
      }
      stopwatch.resetAndLog("Private environment creation");

      return injectorShells;
    }

    private State getState() {
      if (state == null) {
        state = new InheritingState(State.NONE);
      }
      return state;
    }
  }

  /**
   * The Injector is a special case because we allow both parent and child injectors to both have
   * a binding for that key.
   */
  private static void bindInjector(InjectorImpl injector) {
    Key<Injector> key = Key.get(Injector.class);
    InjectorFactory injectorFactory = new InjectorFactory(injector);
    injector.state.putBinding(key,
        new ProviderInstanceBindingImpl<Injector>(injector, key, SourceProvider.UNKNOWN_SOURCE,
            injectorFactory, Scoping.UNSCOPED, injectorFactory,
            ImmutableSet.<InjectionPoint>of()));
  }

  private static class InjectorFactory implements InternalFactory<Injector>, Provider<Injector> {
    private final Injector injector;

    private InjectorFactory(Injector injector) {
      this.injector = injector;
    }

    public Injector get(Errors errors, InternalContext context, Dependency<?> dependency, boolean linked)
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
  private static void bindLogger(InjectorImpl injector) {
    Key<Logger> key = Key.get(Logger.class);
    LoggerFactory loggerFactory = new LoggerFactory();
    injector.state.putBinding(key,
        new ProviderInstanceBindingImpl<Logger>(injector, key,
            SourceProvider.UNKNOWN_SOURCE, loggerFactory, Scoping.UNSCOPED,
            loggerFactory, ImmutableSet.<InjectionPoint>of()));
  }

  private static class LoggerFactory implements InternalFactory<Logger>, Provider<Logger> {
    public Logger get(Errors errors, InternalContext context, Dependency<?> dependency, boolean linked) {
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

  private static class RootModule implements Module {
    final Stage stage;

    private RootModule(Stage stage) {
      this.stage = checkNotNull(stage, "stage");
    }

    public void configure(Binder binder) {
      binder = binder.withSource(SourceProvider.UNKNOWN_SOURCE);
      binder.bind(Stage.class).toInstance(stage);
      binder.bindScope(Singleton.class, SINGLETON);
      binder.bindScope(javax.inject.Singleton.class, SINGLETON);
    }
  }
}
