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

import com.google.inject.ContainerImpl.Injector;
import com.google.inject.Key.AnnotationStrategy;
import static com.google.inject.Scopes.CONTAINER;
import com.google.inject.matcher.Matcher;
import com.google.inject.spi.Message;
import com.google.inject.spi.SourceProviders;
import static com.google.inject.util.Objects.nonNull;
import com.google.inject.util.Stopwatch;
import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * Builds a dependency injection {@link Container}. Binds {@link Key}s to
 * implementations.
 *
 * <p>Creates several bindings by default:
 *
 * <ul>
 * <li>A {@code Locator<T>} for each binding of type {@code T}
 * <li>The {@link Container} iself
 * <li>The {@link Logger} for the class being injected
 * <li>The {@link Stage} passed to the builder's constructor
 * </ul>
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
  final List<LinkedBindingBuilderImpl<?>> linkedBindingBuilders
      = new ArrayList<LinkedBindingBuilderImpl<?>>();
  final Map<Class<? extends Annotation>, Scope> scopes =
      new HashMap<Class<? extends Annotation>, Scope>();

  final List<StaticInjection> staticInjections
      = new ArrayList<StaticInjection>();

  ContainerImpl container;

  final Stage stage;

  /**
   * Keeps error messages in order and prevents duplicates.
   */
  final Collection<Message> errorMessages = new ArrayList<Message>();

  private static final InternalFactory<Container> CONTAINER_FACTORY
      = new InternalFactory<Container>() {
    public Container get(InternalContext context) {
      return context.getContainerImpl();
    }

    public String toString() {
      return "Locator<Container>";
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
      return "Locator<Logger>";
    }
  };

  static final String UNKNOWN_SOURCE = "[unknown source]";

  final ProxyFactoryBuilder proxyFactoryBuilder;

  /**
   * Constructs a new builder.
   *
   * @param stage we're running in. If the stage is {@link Stage#PRODUCTION},
   *  we will eagerly load container-scoped objects.
   */
  public BinderImpl(Stage stage) {
    bindScope(ContainerScoped.class, CONTAINER);

    bind(Container.class).to(CONTAINER_FACTORY);
    bind(Logger.class).to(LOGGER_FACTORY);
    bind(Stage.class).to(stage);

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

  final List<CreationListener> creationListeners
      = new ArrayList<CreationListener>();

  interface CreationListener {
    void notify(ContainerImpl container);
  }

  public void bindInterceptor(Matcher<? super Class<?>> classMatcher,
      Matcher<? super Method> methodMatcher, MethodInterceptor... interceptors) {
    proxyFactoryBuilder.intercept(classMatcher, methodMatcher, interceptors);
  }

  public void bindScope(Class<? extends Annotation> annotationType,
      Scope scope) {
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
    BindingBuilderImpl<T> builder = new BindingBuilderImpl<T>(this, key, source());
    bindingBuilders.add(builder);
    return builder;
  }

  public <T> BindingBuilderImpl<T> bind(TypeLiteral<T> typeLiteral) {
    return bind(Key.get(typeLiteral));
  }

  public <T> BindingBuilderImpl<T> bind(Class<T> clazz) {
    return bind(Key.get(clazz));
  }

  public <T> LinkedBindingBuilderImpl<T> link(Key<T> key) {
    LinkedBindingBuilderImpl<T> builder =
        new LinkedBindingBuilderImpl<T>(this, key).from(source());
    linkedBindingBuilders.add(builder);
    return builder;
  }

  // Next three methods not test-covered?

  public <T> LinkedBindingBuilderImpl<T> link(Class<T> type) {
    return link(Key.get(type));
  }

  public <T> LinkedBindingBuilderImpl<T> link(TypeLiteral<T> type) {
    return link(Key.get(type));
  }

  public ConstantBindingBuilderImpl bindConstant(Annotation annotation) {
    return bind(source(), Key.strategyFor(annotation));
  }

  public ConstantBindingBuilderImpl bindConstant(
      Class<? extends Annotation> annotationType) {
    return bind(source(), Key.strategyFor(annotationType));
  }

  private ConstantBindingBuilderImpl bind(Object source,
      AnnotationStrategy annotationStrategy) {
    ConstantBindingBuilderImpl builder =
        new ConstantBindingBuilderImpl(this, annotationStrategy).from(source);
    constantBindingBuilders.add(builder);
    return builder;
  }

  public void requestStaticInjection(Class<?>... types) {
    staticInjections.add(new StaticInjection(source(), types));
  }

  public void install(Module module) {
    module.configure(this);
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
   * Creates a {@link Container} instance. Injects static members for classes
   * which were registered using {@link #requestStaticInjection(Class...)}.
   *
   * @throws CreationException if configuration errors are found. The
   *     expectation is that the application will log this exception and exit.
   * @throws IllegalStateException if called more than once
   */
  Container createContainer() throws CreationException {
    stopwatch.resetAndLog(logger, "Configuration");

    // Create the container.
    Map<Key<?>, Binding<?>> bindings = new HashMap<Key<?>, Binding<?>>();
    container = new ContainerImpl(
        proxyFactoryBuilder.create(), bindings, scopes);
    container.setErrorHandler(configurationErrorHandler);

    createConstantBindings();

    // Commands to execute before returning the Container instance.
    final List<ContextualCallable<Void>> preloaders
        = new ArrayList<ContextualCallable<Void>>();

    createBindings(preloaders);
    createLinkedBindings();

    stopwatch.resetAndLog(logger, "Binding creation");

    container.index();

    stopwatch.resetAndLog(logger, "Binding indexing");

    for (CreationListener creationListener : creationListeners) {
      creationListener.notify(container);
    }

    stopwatch.resetAndLog(logger, "Validation");

    for (StaticInjection staticInjection : staticInjections) {
      staticInjection.createInjectors(container);
    }

    stopwatch.resetAndLog(logger, "Static validation");

    // Blow up if we encountered errors.
    if (!errorMessages.isEmpty()) {
      throw new CreationException(errorMessages);
    }

    // Switch to runtime error handling.
    container.setErrorHandler(new RuntimeErrorHandler());

    // Inject static members.
    for (StaticInjection staticInjection : staticInjections) {
      staticInjection.runInjectors(container);
    }

    stopwatch.resetAndLog(logger, "Static member injection");

    // Run preloading commands.
    runPreloaders(container, preloaders);

    stopwatch.resetAndLog(logger, "Preloading");

    return container;
  }

  private void runPreloaders(ContainerImpl container,
      final List<ContextualCallable<Void>> preloaders) {
    container.callInContext(new ContextualCallable<Void>() {
      public Void call(InternalContext context) {
        for (ContextualCallable<Void> preloader : preloaders) {
          preloader.call(context);
        }
        return null;
      }
    });
  }

  private void createLinkedBindings() {
    for (LinkedBindingBuilderImpl<?> builder : linkedBindingBuilders) {
      createLinkedBinding(builder);
    }
  }

  private <T> void createLinkedBinding(LinkedBindingBuilderImpl<T> builder) {
    // TODO: Support linking to a later-declared link?
    Key<? extends T> destinationKey = builder.getDestination();
    if (destinationKey == null) {
      addError(builder.getSource(), ErrorMessages.MISSING_LINK_DESTINATION);
      return;
    }

    Binding<? extends T> destination = container.getBinding(destinationKey);
    if (destination == null) {
      addError(builder.getSource(), ErrorMessages.LINK_DESTINATION_NOT_FOUND,
          destinationKey);
      return;
    }

    Binding<?> binding = Binding.newInstance(container, builder.getKey(),
        builder.getSource(), destination.getInternalFactory());

    putBinding(binding);
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
        = builder.getInternalFactory(container);
    Binding<?> binding
        = Binding.newInstance(container, key, builder.getSource(), factory);

    putBinding(binding);

    // Register to preload if necessary.
    boolean preload = stage == Stage.PRODUCTION;
    if (builder.isContainerScoped()) {
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
      putBinding(builder.createBinding(container));
    }
    else {
      addError(builder.getSource(), ErrorMessages.MISSING_CONSTANT_VALUE);
    }
  }

  void putBinding(Binding<?> binding) {
    Key<?> key = binding.getKey();
    Map<Key<?>, Binding<?>> bindings = container.internalBindings();
    Binding<?> original = bindings.get(key);

    // Binding to Locator<?> is not allowed.
    if (key.getRawType().equals(Locator.class)) {
      addError(binding.getSource(), ErrorMessages.CANNOT_BIND_TO_LOCATOR);
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
   * Handles errors after the container is created.
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
    final List<Injector> injectors = new ArrayList<Injector>();

    public StaticInjection(Object source, Class<?>[] types) {
      this.source = source;
      this.types = types;
    }

    void createInjectors(final ContainerImpl container) {
      container.withDefaultSource(source,
          new Runnable() {
            public void run() {
              for (Class<?> clazz : types) {
                container.addInjectorsForFields(
                    clazz.getDeclaredFields(), true, injectors);
                container.addInjectorsForMethods(
                    clazz.getDeclaredMethods(), true, injectors);
              }
            }
          });
    }

    void runInjectors(ContainerImpl container) {
      container.callInContext(new ContextualCallable<Void>() {
        public Void call(InternalContext context) {
          for (Injector injector : injectors) {
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
          = ExternalContext.newInstance(null, key, context.getContainerImpl());
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
