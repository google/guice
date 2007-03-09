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

import com.google.inject.InjectorImpl.SingleMemberInjector;
import com.google.inject.Key.AnnotationStrategy;
import static com.google.inject.Scopes.SINGLETON;
import com.google.inject.matcher.Matcher;
import com.google.inject.spi.Message;
import com.google.inject.spi.SourceProviders;
import com.google.inject.util.Annotations;
import static com.google.inject.util.Objects.nonNull;
import com.google.inject.util.StackTraceElements;
import com.google.inject.util.Stopwatch;
import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * Builds a dependency injection {@link Injector}. Binds {@link Key}s to
 * implementations.
 *
 *
 * @author crazybob@google.com (Bob Lee)
 */
class BinderImpl implements Binder {

  static {
    SourceProviders.skip(BinderImpl.class);
  }

  private static final Logger logger
      = Logger.getLogger(BinderImpl.class.getName());

  final List<BindingBuilderImpl<?>> bindingBuilders
      = new ArrayList<BindingBuilderImpl<?>>();
  final List<ConstantBindingBuilderImpl> constantBindingBuilders
      = new ArrayList<ConstantBindingBuilderImpl>();
  final Map<Class<? extends Annotation>, Scope> scopes =
      new HashMap<Class<? extends Annotation>, Scope>();

  final List<StaticInjection> staticInjections
      = new ArrayList<StaticInjection>();

  InjectorImpl injector;

  final Stage stage;

  final Collection<Message> errorMessages = new ArrayList<Message>();

  private static final InternalFactory<Injector> INJECTOR_FACTORY
      = new InternalFactory<Injector>() {
    public Injector get(InternalContext context) {
      return context.getInjectorImpl();
    }

    public String toString() {
      return "Provider<Injector>";
    }
  };

  private static final InternalFactory<Logger> LOGGER_FACTORY
      = new InternalFactory<Logger>() {
    // not test-covered?
    public Logger get(InternalContext context) {
      Member member = context.getExternalContext().getMember();
      return member == null
          ? Logger.getAnonymousLogger()
          : Logger.getLogger(member.getDeclaringClass().getName());
    }

    public String toString() {
      return "Provider<Logger>";
    }
  };

  final ProxyFactoryBuilder proxyFactoryBuilder;

  /**
   * Constructs a new builder.
   *
   * @param stage we're running in. If the stage is {@link Stage#PRODUCTION},
   *  we will eagerly load singletons.
   */
  public BinderImpl(Stage stage) {
    bindScope(Singleton.class, SINGLETON);

    bind(Injector.class).to(INJECTOR_FACTORY);
    bind(Logger.class).to(LOGGER_FACTORY);
    bind(Stage.class).toInstance(stage);

    this.proxyFactoryBuilder = new ProxyFactoryBuilder();

    this.stage = stage;
  }

  /**
   * Constructs a new builder for a development environment (see
   * {@link Stage#DEVELOPMENT}).
   */
  public BinderImpl() {
    this(Stage.DEVELOPMENT);
  }

  public Stage currentStage() {
    return stage;
  }

  final List<CreationListener> creationListeners
      = new ArrayList<CreationListener>();
  final List<CreationListener> instanceInjectors
      = new ArrayList<CreationListener>();

  interface CreationListener {
    void notify(InjectorImpl injector);
  }

  public void bindInterceptor(Matcher<? super Class<?>> classMatcher,
      Matcher<? super Method> methodMatcher, MethodInterceptor... interceptors) {
    proxyFactoryBuilder.intercept(classMatcher, methodMatcher, interceptors);
  }

  public void bindScope(Class<? extends Annotation> annotationType,
      Scope scope) {
    if (!Scopes.isScopeAnnotation(annotationType)) {
      addError(StackTraceElements.forType(annotationType),
          ErrorMessages.MISSING_SCOPE_ANNOTATION);
      // Go ahead and bind anyway so we don't get collateral errors.
    }

    if (!Annotations.isRetainedAtRuntime(annotationType)) {
      addError(StackTraceElements.forType(annotationType),
          ErrorMessages.MISSING_RUNTIME_RETENTION, source());
      // Go ahead and bind anyway so we don't get collateral errors.
    }

    Scope existing = scopes.get(nonNull(annotationType, "annotation type"));
    if (existing != null) {
      addError(source(), ErrorMessages.DUPLICATE_SCOPES, existing,
          annotationType, scope);
    }
    else {
      scopes.put(annotationType, nonNull(scope, "scope"));
    }
  }

  public <T> BindingBuilderImpl<T> bind(Key<T> key) {
    BindingBuilderImpl<T> builder =
        new BindingBuilderImpl<T>(this, key, source());
    bindingBuilders.add(builder);
    return builder;
  }

  public <T> BindingBuilderImpl<T> bind(TypeLiteral<T> typeLiteral) {
    return bind(Key.get(typeLiteral));
  }

  public <T> BindingBuilderImpl<T> bind(Class<T> clazz) {
    return bind(Key.get(clazz));
  }

  public ConstantBindingBuilderImpl bindConstant() {
    ConstantBindingBuilderImpl constantBuilder
        = new ConstantBindingBuilderImpl(this, source());
    constantBindingBuilders.add(constantBuilder);
    return constantBuilder;
  }

  public void requestStaticInjection(Class<?>... types) {
    staticInjections.add(new StaticInjection(source(), types));
  }

  public void install(Module module) {
    module.configure(this);
  }

  public void addError(String message, Object... arguments) {
    configurationErrorHandler.handle(source(), message, arguments);
  }

  public void addError(Throwable t) {
    Object source = source();
    String className = t.getClass().getSimpleName();
    String message = ErrorMessages.getRootMessage(t);
    String logMessage = String.format(
        ErrorMessages.EXCEPTION_REPORTED_BY_MODULE, message);
    logger.log(Level.INFO, logMessage, t);
    addError(source, ErrorMessages.EXCEPTION_REPORTED_BY_MODULE_SEE_LOG, message);
  }

  void addError(Object source, String message, Object... arguments) {
    configurationErrorHandler.handle(source, message, arguments);
  }

  void addError(Object source, String message) {
    configurationErrorHandler.handle(source, message);
  }

  /**
   * Adds an error message to be reported at creation time.
   */
  void add(Message errorMessage) {
    errorMessages.add(errorMessage);
  }

  final Stopwatch stopwatch = new Stopwatch();

  /**
   * Creates a {@link Injector} instance. Injects static members for classes
   * which were registered using {@link #requestStaticInjection(Class...)}.
   *
   * @throws CreationException if configuration errors are found. The
   *     expectation is that the application will log this exception and exit.
   * @throws IllegalStateException if called more than once
   */
  Injector createInjector() throws CreationException {
    stopwatch.resetAndLog(logger, "Configuration");

    Map<Key<?>, BindingImpl<?>> bindings = new HashMap<Key<?>, BindingImpl<?>>();
    injector = new InjectorImpl(
        proxyFactoryBuilder.create(), bindings, scopes);
    injector.setErrorHandler(configurationErrorHandler);

    createConstantBindings();

    // Commands to execute before returning the Injector instance.
    final List<ContextualCallable<Void>> preloaders
        = new ArrayList<ContextualCallable<Void>>();

    createBindings(preloaders);

    stopwatch.resetAndLog(logger, "Binding creation");

    injector.index();

    stopwatch.resetAndLog(logger, "Binding indexing");

    for (CreationListener creationListener : creationListeners) {
      creationListener.notify(injector);
    }

    stopwatch.resetAndLog(logger, "Validation");

    for (StaticInjection staticInjection : staticInjections) {
      staticInjection.createMemberInjectors(injector);
    }

    stopwatch.resetAndLog(logger, "Static validation");

    // Blow up if we encountered errors.
    if (!errorMessages.isEmpty()) {
      throw new CreationException(errorMessages);
    }

    // Switch to runtime error handling.
    injector.setErrorHandler(new RuntimeErrorHandler());

    // Inject static members.
    for (StaticInjection staticInjection : staticInjections) {
      staticInjection.runMemberInjectors(injector);
    }

    stopwatch.resetAndLog(logger, "Static member injection");

    // Inject pre-existing instances.
    for (CreationListener instanceInjector : instanceInjectors) {
      instanceInjector.notify(injector);
    }

    stopwatch.resetAndLog(logger, "Instance injection");

    // Run preloading commands.
    runPreloaders(injector, preloaders);

    stopwatch.resetAndLog(logger, "Preloading");

    return injector;
  }

  private void runPreloaders(InjectorImpl injector,
      final List<ContextualCallable<Void>> preloaders) {
    injector.callInContext(new ContextualCallable<Void>() {
      public Void call(InternalContext context) {
        for (ContextualCallable<Void> preloader : preloaders) {
          preloader.call(context);
        }
        return null;
      }
    });
  }

  private void createBindings(List<ContextualCallable<Void>> preloaders) {
    for (BindingBuilderImpl<?> builder : bindingBuilders) {
      createBinding(builder, preloaders);
    }
  }

  private <T> void createBinding(BindingBuilderImpl<T> builder,
      List<ContextualCallable<Void>> preloaders) {
    final Key<T> key = builder.getKey();
    final InternalFactory<? extends T> factory
        = builder.getInternalFactory(injector);
    BindingImpl<?> binding
        = BindingImpl.newInstance(injector, key, builder.getSource(), factory);

    putBinding(binding);

    // Register to preload if necessary.
    boolean preload = stage == Stage.PRODUCTION;
    if (builder.isSingletonScoped()) {
      if (preload || builder.shouldPreload()) {
        preloaders.add(new BindingPreloader(key, factory));
      }
    }
    else {
      if (builder.shouldPreload()) {
        addError(builder.getSource(), ErrorMessages.PRELOAD_NOT_ALLOWED);
      }
    }
  }

  private void createConstantBindings() {
    for (ConstantBindingBuilderImpl builder : constantBindingBuilders) {
      createConstantBinding(builder);
    }
  }

  private void createConstantBinding(ConstantBindingBuilderImpl builder) {
    if (builder.hasValue()) {
      putBinding(builder.createBinding(injector));
    }
    else {
      addError(builder.getSource(), ErrorMessages.MISSING_CONSTANT_VALUE);
    }
  }

  private static Set<Class<?>> FORBIDDEN_TYPES = forbiddenTypes();

  private static Set<Class<?>> forbiddenTypes() {
    Set<Class<?>> set = new HashSet<Class<?>>();
    Collections.addAll(set,

        // It's unfortunate that we have to maintain a blacklist of specific
        // classes, but we can't easily block the whole package because of
        // all our unit tests.

        AbstractModule.class,
        Binder.class,
        Binding.class,
        Key.class,
        Module.class,
        Provider.class,
        Scope.class,
        TypeLiteral.class);
    return Collections.unmodifiableSet(set);
  }

  void putBinding(BindingImpl<?> binding) {
    Key<?> key = binding.getKey();
    Map<Key<?>, BindingImpl<?>> bindings = injector.internalBindings();
    Binding<?> original = bindings.get(key);

    Class<?> rawType = key.getRawType();
    if (FORBIDDEN_TYPES.contains(rawType)) {
      addError(binding.getSource(), ErrorMessages.CANNOT_BIND_TO_GUICE_TYPE,
          rawType.getSimpleName());
      return;
    }

    if (bindings.containsKey(key)) {
      addError(binding.getSource(), ErrorMessages.BINDING_ALREADY_SET, key,
          original.getSource());
    }
    else {
      bindings.put(key, binding);
    }
  }

  /**
   * Gets the current source.
   */
  Object source() {
    return SourceProviders.defaultSource();    
  }

  ErrorHandler configurationErrorHandler = new AbstractErrorHandler() {

    public void handle(Object source, String message) {
      add(new Message(source, message));
    }
  };

  /**
   * Handles errors after the injector is created.
   */
  static class RuntimeErrorHandler extends AbstractErrorHandler {

    static ErrorHandler INSTANCE = new RuntimeErrorHandler();

    public void handle(Object source, String message) {
      throw new ConfigurationException("Error at " + source + " " + message);
    }
  }

  /**
   * A requested static injection.
   */
  class StaticInjection {

    final Object source;
    final Class<?>[] types;
    final List<SingleMemberInjector> memberInjectors
        = new ArrayList<SingleMemberInjector>();

    public StaticInjection(Object source, Class<?>[] types) {
      this.source = source;
      this.types = types;
    }

    void createMemberInjectors(final InjectorImpl injector) {
      injector.withDefaultSource(source,
          new Runnable() {
            public void run() {
              for (Class<?> clazz : types) {
                injector.addSingleInjectorsForFields(
                    clazz.getDeclaredFields(), true, memberInjectors);
                injector.addSingleInjectorsForMethods(
                    clazz.getDeclaredMethods(), true, memberInjectors);
              }
            }
          });
    }

    void runMemberInjectors(InjectorImpl injector) {
      injector.callInContext(new ContextualCallable<Void>() {
        public Void call(InternalContext context) {
          for (SingleMemberInjector injector : memberInjectors) {
            injector.inject(context, null);
          }
          return null;
        }
      });
    }
  }

  static class BindingPreloader implements ContextualCallable<Void> {

    private final Key<?> key;
    private final InternalFactory<?> factory;

    public BindingPreloader(Key<?> key, InternalFactory<?> factory) {
      this.key = key;
      this.factory = factory;
    }

    public Void call(InternalContext context) {
      ExternalContext<?> externalContext
          = ExternalContext.newInstance(null, key, context.getInjectorImpl());
      context.setExternalContext(externalContext);
      try {
        factory.get(context);
        return null;
      }
      finally {
        context.setExternalContext(null);
      }
    }
  }
}
