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
import com.google.inject.spi.SourceConsumer;
import static com.google.inject.util.Objects.nonNull;
import com.google.inject.util.Stopwatch;
import com.google.inject.util.ToStringBuilder;
import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * Builds a dependency injection {@link Container}. Binds {@link Key}s to
 * implementations. A binding implementation could be anything from a constant
 * value to an object in the HTTP session.
 *
 * <p>Not safe for concurrent use.
 *
 * <p>Default bindings include:
 *
 * <ul>
 * <li>A {@code Factory<T>} for each binding of type {@code T}
 * <li>The {@link Container} iself
 * <li>The {@link Logger} for the class being injected
 * </ul>
 *
 * <p>Converts constants as needed from {@code String} to any primitive type in
 * addition to {@code enum} and {@code Class<?>}.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public final class ContainerBuilder extends SourceConsumer {

  private static final Logger logger
      = Logger.getLogger(ContainerBuilder.class.getName());

  final List<BindingBuilder<?>> bindingBuilders
      = new ArrayList<BindingBuilder<?>>();
  final List<ConstantBindingBuilder> constantBindingBuilders
      = new ArrayList<ConstantBindingBuilder>();
  final List<LinkedBindingBuilder<?>> linkedBindingBuilders
      = new ArrayList<LinkedBindingBuilder<?>>();
  final Map<Class<? extends Annotation>, Scope> scopes =
      new HashMap<Class<? extends Annotation>, Scope>();

  final List<StaticInjection> staticInjections
      = new ArrayList<StaticInjection>();

  ContainerImpl container;

  /**
   * Keeps error messages in order and prevents duplicates.
   */
  final Collection<Message> errorMessages = new ArrayList<Message>();

  private static final InternalFactory<Container> CONTAINER_FACTORY
      = new InternalFactory<Container>() {
    public Container get(InternalContext context) {
      return context.getContainer();
    }
  };

  private static final InternalFactory<Logger> LOGGER_FACTORY
      = new InternalFactory<Logger>() {
    public Logger get(InternalContext context) {
      Member member = context.getExternalContext().getMember();
      return member == null
          ? Logger.getAnonymousLogger()
          : Logger.getLogger(member.getDeclaringClass().getName());
    }
  };

  static final String UNKNOWN_SOURCE = "[unknown source]";

  final ProxyFactoryBuilder proxyFactoryBuilder;

  /**
   * Constructs a new builder.
   */
  public ContainerBuilder() {
    scope(ContainerScoped.class, CONTAINER);

    bind(Container.class).to(CONTAINER_FACTORY);
    bind(Logger.class).to(LOGGER_FACTORY);

    this.proxyFactoryBuilder = new ProxyFactoryBuilder();
  }

  final List<CreationListener> creationListeners
      = new ArrayList<CreationListener>();

  interface CreationListener {
    void notify(ContainerImpl container);
  }

  /**
   * Applies the given method interceptor to the methods matched by the class
   * and method matchers.
   *
   * @param classMatcher matches classes the interceptor should apply to. For
   *     example: {@code only(Runnable.class)}.
   * @param methodMatcher matches methods the interceptor should apply to. For
   *     example: {@code annotatedWith(Transactional.class)}.
   * @param interceptors to apply
   */
  public void intercept(Matcher<? super Class<?>> classMatcher,
      Matcher<? super Method> methodMatcher, MethodInterceptor... interceptors) {
    ensureNotCreated();
    proxyFactoryBuilder.intercept(classMatcher, methodMatcher, interceptors);
  }

  /**
   * Adds a new scope. Maps a {@link Scope} instance to a given annotation.
   */
  public void scope(Class<? extends Annotation> annotationType, Scope scope) {
    ensureNotCreated();
    Scope existing = scopes.get(nonNull(annotationType, "annotation type"));
    if (existing != null) {
      addError(source(), ErrorMessages.DUPLICATE_SCOPES, existing,
          annotationType, scope);
    }
    else {
      scopes.put(annotationType, nonNull(scope, "scope"));
    }
  }

  /**
   * Binds the given key.
   */
  public <T> BindingBuilder<T> bind(Key<T> key) {
    ensureNotCreated();
    BindingBuilder<T> builder = new BindingBuilder<T>(key).from(source());
    bindingBuilders.add(builder);
    return builder;
  }

  /**
   * Binds the given type.
   */
  public <T> BindingBuilder<T> bind(TypeLiteral<T> typeLiteral) {
    return bind(Key.get(typeLiteral));
  }

  /**
   * Binds the given type.
   */
  public <T> BindingBuilder<T> bind(Class<T> clazz) {
    return bind(Key.get(clazz));
  }

  /**
   * Links the given key to another key effectively creating an alias for a
   * binding.
   */
  public <T> LinkedBindingBuilder<T> link(Key<T> key) {
    ensureNotCreated();
    LinkedBindingBuilder<T> builder =
        new LinkedBindingBuilder<T>(key).from(source());
    linkedBindingBuilders.add(builder);
    return builder;
  }

  /**
   * Binds a constant to the given annotation.
   */
  public ConstantBindingBuilder bindConstant(Annotation annotation) {
    ensureNotCreated();
    return bind(source(), Key.strategyFor(annotation));
  }

  /**
   * Binds a constant to the given annotation type.
   */
  public ConstantBindingBuilder bindConstant(
      Class<? extends Annotation> annotationType) {
    ensureNotCreated();
    return bind(source(), Key.strategyFor(annotationType));
  }

  /**
   * Binds a constant to the given name from the given source.
   */
  private ConstantBindingBuilder bind(Object source,
      AnnotationStrategy annotationStrategy) {
    ConstantBindingBuilder builder =
        new ConstantBindingBuilder(annotationStrategy).from(source);
    constantBindingBuilders.add(builder);
    return builder;
  }

  /**
   * Upon successful creation, the {@link Container} will inject static fields
   * and methods in the given classes.
   *
   * @param types for which static members will be injected
   */
  public void requestStaticInjection(Class<?>... types) {
    staticInjections.add(new StaticInjection(source(), types));
  }

  /**
   * Applies the given module to this builder.
   */
  public void install(Module module) {
    module.configure(this);
  }

  void addError(Object source, String message, Object... arguments) {
    new ConfigurationErrorHandler(source).handle(message, arguments);
  }

  void addError(Object source, String message) {
    new ConfigurationErrorHandler(source).handle(message);
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
   * @param preload If true, the container will load all container-scoped
   *     bindings now. If false, the container will lazily load them. Eager
   *     loading is appropriate for production use (catch errors early and take
   *     any performance hit up front) while lazy loading can speed development.
   * @throws ContainerCreationException if configuration errors are found. The
   *     expectation is that the application will log this exception and exit.
   * @throws IllegalStateException if called more than once
   */
  public synchronized Container create(boolean preload)
      throws ContainerCreationException {
    stopwatch.resetAndLog(logger, "Configuration");

    // Create the container.
    ensureNotCreated();
    Map<Key<?>, Binding<?>> bindings = new HashMap<Key<?>, Binding<?>>();
    container = new ContainerImpl(
        proxyFactoryBuilder.create(), bindings, scopes);

    createConstantBindings();

    // Commands to execute before returning the Container instance.
    final List<ContextualCallable<Void>> preloaders
        = new ArrayList<ContextualCallable<Void>>();

    createBindings(preload, preloaders);
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
      throw new ContainerCreationException(errorMessages);
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
    for (LinkedBindingBuilder<?> builder : linkedBindingBuilders) {
      createLinkedBinding(builder);
    }
  }

  private <T> void createLinkedBinding(LinkedBindingBuilder<T> builder) {
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

  private void createBindings(boolean preload,
      List<ContextualCallable<Void>> preloaders) {
    for (BindingBuilder<?> builder : bindingBuilders) {
      createBinding(builder, preload, preloaders);
    }
  }

  private <T> void createBinding(BindingBuilder<T> builder, boolean preload,
      List<ContextualCallable<Void>> preloaders) {
    final Key<T> key = builder.getKey();
    final InternalFactory<? extends T> factory
        = builder.getInternalFactory(container);
    Binding<?> binding
        = Binding.newInstance(container, key, builder.getSource(), factory);

    putBinding(binding);

    // Register to preload if necessary.
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
    for (ConstantBindingBuilder builder : constantBindingBuilders) {
      createConstantBinding(builder);
    }
  }

  private void createConstantBinding(ConstantBindingBuilder builder) {
    if (builder.hasValue()) {
      putBinding(builder.createBinding(container));
    }
    else {
      addError(builder.getSource(), ErrorMessages.MISSING_CONSTANT_VALUE);
    }
  }

  void putFactory(Object source, Map<Key<?>, InternalFactory<?>> factories,
      Key<?> key, InternalFactory<?> factory) {
    if (factories.containsKey(key)) {
      addError(source, ErrorMessages.BINDING_ALREADY_SET, key);
    }
    else {
      factories.put(key, factory);
    }
  }

  void putBinding(Binding<?> binding) {
    Key<?> key = binding.getKey();
    Map<Key<?>, Binding<?>> bindings = container.internalBindings();
    Binding<?> original = bindings.get(key);
    if (bindings.containsKey(key)) {
      addError(binding.getSource(), ErrorMessages.BINDING_ALREADY_SET, key,
          original.getSource());
    }
    else {
      bindings.put(key, binding);
    }
  }

  /**
   * Currently we only support creating one Container instance per builder. If
   * we want to support creating more than one container per builder, we should
   * move to a "factory factory" model where we create a factory instance per
   * Container. Right now, one factory instance would be shared across all the
   * containers, which means container-scoped objects would be shared, etc.
   */
  private void ensureNotCreated() {
    if (container != null) {
      throw new IllegalStateException("Container already created.");
    }
  }

  /**
   * Binds a {@link Key} to an implementation in a given scope.
   */
  public class BindingBuilder<T> {

    Object source = ContainerBuilder.UNKNOWN_SOURCE;
    ErrorHandler errorHandler = RuntimeErrorHandler.INSTANCE;
    Key<T> key;
    InternalFactory<? extends T> factory;
    TypeLiteral<? extends T> implementation;
    T instance;
    Scope scope;
    boolean preload = false;

    BindingBuilder(Key<T> key) {
      this.key = nonNull(key, "key");
    }

    Object getSource() {
      return source;
    }

    Key<T> getKey() {
      return key;
    }

    BindingBuilder<T> from(Object source) {
      this.source = source;
      this.errorHandler = new ConfigurationErrorHandler(source);
      return this;
    }

    /**
     * Specifies the annotation type for this binding.
     */
    public BindingBuilder<T> annotatedWith(
        Class<? extends Annotation> annotationType) {
      if (this.key.hasAnnotationType()) {
        errorHandler.handle(ErrorMessages.ANNOTATION_ALREADY_SPECIFIED);
      } else {
        this.key = Key.get(this.key.getType(), annotationType);
      }
      return this;
    }

    /**
     * Specifies an annotation for this binding.
     */
    public BindingBuilder<T> annotatedWith(Annotation annotation) {
      if (this.key.hasAnnotationType()) {
        errorHandler.handle(ErrorMessages.ANNOTATION_ALREADY_SPECIFIED);
      } else {
        this.key = Key.get(this.key.getType(), annotation);
      }
      return this;
    }

    /**
     * Binds to instances of the given implementation class. The
     * {@link Container} will inject the implementation instances as well. Sets
     * the scope based on an annotation on the implementation class if present.
     */
    public <I extends T> BindingBuilder<T> to(Class<I> implementation) {
      return to(TypeLiteral.get(implementation));
    }

    /**
     * Binds to instances of the given implementation type. The
     * {@link Container} will inject the implementation instances as well. Sets
     * the scope based on an annotation on the implementation class if present.
     */
    public <I extends T> BindingBuilder<T> to(
        final TypeLiteral<I> implementation) {
      ensureImplementationIsNotSet();
      this.implementation = implementation;
      final DefaultFactory<I> defaultFactory
          = new DefaultFactory<I>(key, implementation);
      this.factory = defaultFactory;
      creationListeners.add(new CreationListener() {
        public void notify(final ContainerImpl container) {
          container.withErrorHandler(errorHandler, new Runnable() {
            public void run() {
              defaultFactory.setConstructor(
                  container.getConstructor(implementation));
            }
          });
        }
      });
      return this;
    }

    /**
     * Binds to instances from the given factory.
     */
    public BindingBuilder<T> to(
        final ContextualFactory<? extends T> factory) {
      ensureImplementationIsNotSet();
      this.factory = new InternalToContextualFactoryAdapter<T>(factory);
      return this;
    }

    /**
     * Binds to instances from the given factory.
     */
    public BindingBuilder<T> to(final Factory<? extends T> factory) {
      ensureImplementationIsNotSet();
      this.factory = new InternalFactoryToFactoryAdapter<T>(factory);
      return this;
    }

    /**
     * Binds to the given instance.
     */
    public BindingBuilder<T> to(T instance) {
      ensureImplementationIsNotSet();
      this.instance = nonNull(instance, "instance");
      this.factory = new ConstantFactory<T>(instance);
      if (this.scope != null) {
        errorHandler.handle(ErrorMessages.SINGLE_INSTANCE_AND_SCOPE);
      }
      this.scope = CONTAINER;
      return this;
    }

    /**
     * Binds to instances from the given factory.
     */
    BindingBuilder<T> to(final InternalFactory<? extends T> factory) {
      ensureImplementationIsNotSet();
      this.factory = factory;
      return this;
    }

    /**
     * Adds an error message if the implementation has already been bound.
     */
    private void ensureImplementationIsNotSet() {
      if (factory != null) {
        errorHandler.handle(ErrorMessages.IMPLEMENTATION_ALREADY_SET);
      }
    }

    /**
     * Specifies the scope. References the annotation passed to {@link
     * ContainerBuilder#scope(Class, Scope)}.
     */
    public BindingBuilder<T> in(Class<? extends Annotation> scopeAnnotation) {
      ensureScopeNotSet();

      // We could defer this lookup to when we create the container, but this
      // is fine for now.
      this.scope = scopes.get(nonNull(scopeAnnotation, "scope annotation"));
      if (this.scope == null) {
        errorHandler.handle(ErrorMessages.SCOPE_NOT_FOUND,
            "@" + scopeAnnotation.getSimpleName());
      }
      return this;
    }

    /**
     * Specifies the scope.
     */
    public BindingBuilder<T> in(Scope scope) {
      ensureScopeNotSet();
      this.scope = nonNull(scope, "scope");
      return this;
    }

    private void ensureScopeNotSet() {
      // Scoping isn't allowed when we have only one instance.
      if (this.instance != null) {
        errorHandler.handle(ErrorMessages.SINGLE_INSTANCE_AND_SCOPE);
        return;
      }

      if (this.scope != null) {
        errorHandler.handle(ErrorMessages.SCOPE_ALREADY_SET);
      }
    }

    /**
     * Instructs the builder to eagerly load this binding when it creates the
     * container. Useful for application initialization logic. Currently only
     * supported for container-scoped bindings.
     */
    public BindingBuilder<T> preload() {
      this.preload = true;
      return this;
    }

    boolean shouldPreload() {
      return preload;
    }

    InternalFactory<? extends T> getInternalFactory(
        final ContainerImpl container) {
      // If an implementation wasn't specified, use the injection type.
      if (this.factory == null) {
        to(key.getType());
      }

      // Look for @Scoped on the implementation type.
      if (implementation != null) {
        Scope fromAnnotation = Scopes.getScopeForType(
            implementation.getRawType(), scopes, errorHandler);
        if (fromAnnotation != null) {
          if (this.scope == null) {
            this.scope = fromAnnotation;
          } else {
            logger.info("Overriding scope specified by annotation at "
                + source + ".");
          }
        }
      }

      return Scopes.scope(this.key, container, this.factory, scope);
    }

    boolean isContainerScoped() {
      return this.scope == Scopes.CONTAINER;
    }
  }

  /**
   * Injects new instances of the specified implementation class.
   */
  private static class DefaultFactory<T> implements InternalFactory<T> {

    private final TypeLiteral<T> implementation;
    private final Key<? super T> key;

    ConstructorInjector<T> constructor;

    DefaultFactory(Key<? super T> key, TypeLiteral<T> implementation) {
      this.key = key;
      this.implementation = implementation;
    }

    void setConstructor(ConstructorInjector<T> constructor) {
      this.constructor = constructor;
    }

    public T get(InternalContext context) {
      // TODO: figure out if we can suppress this warning
      return constructor.construct(context, (Class<T>) key.getRawType());
    }

    public String toString() {
      return new ToStringBuilder(Factory.class)
          .add("implementation", implementation)
          .toString();
    }
  }

  private static class BindingInfo<T> {

    final Class<T> type;
    final T value;
    final AnnotationStrategy annotationStrategy;
    final Object source;

    BindingInfo(Class<T> type, T value,
        AnnotationStrategy annotationStrategy, Object source) {
      this.type = type;
      this.value = value;
      this.annotationStrategy = annotationStrategy;
      this.source = source;
    }

    Binding<T> createBinding(ContainerImpl container) {
      Key<T> key = Key.get(type, annotationStrategy);
      ConstantFactory<T> factory = new ConstantFactory<T>(value);
      return Binding.newInstance(container, key, source, factory);
    }
  }

  /**
   * Builds a constant binding.
   */
  public class ConstantBindingBuilder {

    BindingInfo<?> bindingInfo;
    final AnnotationStrategy annotationStrategy;
    Object source = ContainerBuilder.UNKNOWN_SOURCE;

    ConstantBindingBuilder(AnnotationStrategy annotationStrategy) {
      this.annotationStrategy = annotationStrategy;
    }

    boolean hasValue() {
      return bindingInfo != null;
    }

    Object getSource() {
      return source;
    }

    ConstantBindingBuilder from(Object source) {
      this.source = source;
      return this;
    }

    /**
     * Binds constant to the given value.
     */
    public void to(String value) {
      to(String.class, value);
    }

    /**
     * Binds constant to the given value.
     */
    public void to(int value) {
      to(int.class, value);
    }

    /**
     * Binds constant to the given value.
     */
    public void to(long value) {
      to(long.class, value);
    }

    /**
     * Binds constant to the given value.
     */
    public void to(boolean value) {
      to(boolean.class, value);
    }

    /**
     * Binds constant to the given value.
     */
    public void to(double value) {
      to(double.class, value);
    }

    /**
     * Binds constant to the given value.
     */
    public void to(float value) {
      to(float.class, value);
    }

    /**
     * Binds constant to the given value.
     */
    public void to(short value) {
      to(short.class, value);
    }

    /**
     * Binds constant to the given value.
     */
    public void to(char value) {
      to(char.class, value);
    }

    /**
     * Binds constant to the given value.
     */
    public void to(Class<?> value) {
      to(Class.class, value);
    }

    /**
     * Binds constant to the given value.
     */
    public <E extends Enum<E>> void to(E value) {
      to(value.getDeclaringClass(), value);
    }

    /**
     * Maps a constant value to the given type and name.
     */
    <T> void to(final Class<T> type, final T value) {
      if (this.bindingInfo != null) {
        addError(source, ErrorMessages.CONSTANT_VALUE_ALREADY_SET);
      } else {
        this.bindingInfo
            = new BindingInfo<T>(type, value, annotationStrategy, source);
      }
    }

    public Binding<?> createBinding(ContainerImpl container) {
      return bindingInfo.createBinding(container);
    }
  }

  /**
   * Links one binding to another.
   */
  public class LinkedBindingBuilder<T> {

    final Key<T> key;
    Key<? extends T> destination;
    Object source = ContainerBuilder.UNKNOWN_SOURCE;

    LinkedBindingBuilder(Key<T> key) {
      this.key = key;
    }

    Object getSource() {
      return source;
    }

    Key<T> getKey() {
      return key;
    }

    Key<? extends T> getDestination() {
      return destination;
    }

    LinkedBindingBuilder<T> from(Object source) {
      this.source = source;
      return this;
    }

    /**
     * Links to another binding with the given key.
     */
    public void to(Key<? extends T> destination) {
      if (this.destination != null) {
        addError(source, ErrorMessages.LINK_DESTINATION_ALREADY_SET);
      } else {
        this.destination = destination;
      }
    }
  }

  /**
   * Handles errors up until we successfully create the container.
   */
  class ConfigurationErrorHandler extends AbstractErrorHandler {

    final Object source;

    ConfigurationErrorHandler(Object source) {
      this.source = source;
    }

    public void handle(String message) {
      add(new Message(source, message));
    }

    public void handle(Throwable t) {
      add(new Message(source, t.getMessage()));
    }
  }

  /**
   * Handles errors after the container is created.
   */
  static class RuntimeErrorHandler extends AbstractErrorHandler {

    static ErrorHandler INSTANCE = new RuntimeErrorHandler();

    public void handle(String message) {
      throw new ConfigurationException(message);
    }

    public void handle(Throwable t) {
      throw new ConfigurationException(t);
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
      container.withErrorHandler(new ConfigurationErrorHandler(source),
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
