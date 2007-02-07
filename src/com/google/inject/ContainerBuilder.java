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

import com.google.inject.spi.ConstructionProxyFactory;
import com.google.inject.spi.DefaultConstructionProxyFactory;
import com.google.inject.spi.Message;
import com.google.inject.spi.SourceConsumer;
import com.google.inject.util.Objects;
import static com.google.inject.util.Objects.nonNull;
import com.google.inject.util.Stopwatch;
import com.google.inject.util.ToStringBuilder;
import static com.google.inject.Scopes.*;

import java.lang.reflect.Member;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;

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
 *   <li>A {@code Factory<T>} for each binding of type {@code T}
 *   <li>The {@link Container} iself
 *   <li>The {@link Logger} for the class being injected
 * </ul>
 *
 * <p>Converts constants as needed from {@code String} to any primitive type
 * in addition to {@code enum} and {@code Class<?>}.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public final class ContainerBuilder extends SourceConsumer {

  private static final Logger logger =
      Logger.getLogger(ContainerBuilder.class.getName());

  final List<BindingBuilder<?>> bindingBuilders =
      new ArrayList<BindingBuilder<?>>();
  final List<ConstantBindingBuilder> constantBindingBuilders =
      new ArrayList<ConstantBindingBuilder>();
  final List<LinkedBindingBuilder<?>> linkedBindingBuilders =
      new ArrayList<LinkedBindingBuilder<?>>();
  final Map<String, Scope> scopes = new HashMap<String, Scope>();

  final List<StaticInjection> staticInjections =
      new ArrayList<StaticInjection>();

  ContainerImpl container;

  /**
   * Keeps error messages in order and prevents duplicates.
   */
  Set<Message> errorMessages = new LinkedHashSet<Message>();

  private static final InternalFactory<Container> CONTAINER_FACTORY =
      new InternalFactory<Container>() {
        public Container get(InternalContext context) {
          return context.getContainer();
        }
      };

  private static final InternalFactory<Logger> LOGGER_FACTORY =
      new InternalFactory<Logger>() {
        public Logger get(InternalContext context) {
          Member member = context.getExternalContext().getMember();
          return member == null ? Logger.getAnonymousLogger()
              : Logger.getLogger(member.getDeclaringClass().getName());
        }
      };

  static final String UNKNOWN_SOURCE = "[unknown source]";

  final ConstructionProxyFactory constructionProxyFactory;

  /**
   * Constructs a new builder.
   *
   * @param constructionProxyFactory to use when constructing objects
   */
  public ContainerBuilder(ConstructionProxyFactory constructionProxyFactory) {
    scope(DEFAULT_NAME, DEFAULT);
    scope(CONTAINER_NAME, CONTAINER);

    bind(Container.class).to(CONTAINER_FACTORY);
    bind(Logger.class).to(LOGGER_FACTORY);

    this.constructionProxyFactory = nonNull(constructionProxyFactory,
        "construction proxy factory");
  }

  /**
   * Constructs a new builder.
   */
  public ContainerBuilder() {
    this(new DefaultConstructionProxyFactory());
  }

  final List<Validation> validations = new ArrayList<Validation>();

  /**
   * Registers a type to be validated for injection when we create the
   * container.
   */
  void validate(final Object source, final Class<?> type) {
    validations.add(new Validation() {
      public void run(final ContainerImpl container) {
        container.withErrorHandler(new ConfigurationErrorHandler(source),
            new Runnable() {
              public void run() {
                container.getConstructor(type);
              }
            });
      }
    });
  }

  /**
   * A validation command to run after we create the container but before
   * we return it to the client.
   */
  interface Validation {
    void run(ContainerImpl container);
  }

  /**
   * Adds a new scope. Maps a {@link Scope} instance to a given scope name.
   * Scopes should be mapped before used in bindings. {@link Scoped#value()}
   * references this name.
   */
  public void scope(String name, Scope scope) {
    if (scopes.containsKey(nonNull(name, "name"))) {
      addError(source(), ErrorMessages.DUPLICATE_SCOPES, name);
    } else {
      scopes.put(nonNull(name, "name"), nonNull(scope, "scope"));
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
   * Binds a constant to the given name.
   */
  public ConstantBindingBuilder bind(String name) {
    ensureNotCreated();
    return bind(name, source());
  }

  /**
   * Binds a constant to the given name from the given source.
   */
  private ConstantBindingBuilder bind(String name, Object source) {
    ConstantBindingBuilder builder =
        new ConstantBindingBuilder(Objects.nonNull(name, "name")).from(source);
    constantBindingBuilders.add(builder);
    return builder;
  }

  /**
   * Binds a string constant for each property.
   */
  public void bindProperties(Map<String, String> properties) {
    ensureNotCreated();
    Object source = source();
    for (Map.Entry<String, String> entry : properties.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      bind(key, source).to(value);
    }
  }

  /**
   * Binds a string constant for each property.
   */
  public void bindProperties(Properties properties) {
    ensureNotCreated();
    Object source = source();
    for (Map.Entry<Object, Object> entry : properties.entrySet()) {
      String key = (String) entry.getKey();
      String value = (String) entry.getValue();
      bind(key, source).to(value);
    }
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

  Stopwatch stopwatch = new Stopwatch();

  /**
   * Creates a {@link Container} instance. Injects static members for classes
   * which were registered using {@link #requestStaticInjection(Class...)}.
   *
   * @param preload If true, the container will load all container-scoped
   *  bindings now. If false, the container will lazily load them. Eager
   *  loading is appropriate for production use (catch errors early and take
   *  any performance hit up front) while lazy loading can speed development.
   *
   * @throws ContainerCreationException if configuration errors are found. The
   *  expectation is that the application will log this exception and exit.
   * @throws IllegalStateException if called more than once
   */
  public synchronized Container create(boolean preload)
      throws ContainerCreationException {
    stopwatch.resetAndLog(logger, "Configuration");

    // Create the container.
    ensureNotCreated();
    Map<Key<?>, Binding<?>> bindings =
        new HashMap<Key<?>, Binding<?>>();
    container = new ContainerImpl(constructionProxyFactory, bindings);

    createConstantBindings();

    // Commands to execute before returning the Container instance.
    final List<ContextualCallable<Void>> preloaders =
        new ArrayList<ContextualCallable<Void>>();

    createBindings(preload, preloaders);
    createLinkedBindings();

    stopwatch.resetAndLog(logger, "Binding creation");

    container.index();

    stopwatch.resetAndLog(logger, "Binding indexing");

    // Run validations.
    for (Validation validation : validations) {
      validation.run(container);
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

  private <T> void createLinkedBinding(
      LinkedBindingBuilder<T> builder) {
    // TODO: Support linking to a later-declared link?
    Key<? extends T> destinationKey = builder.getDestination();
    if (destinationKey == null) {
      addError(builder.getSource(), ErrorMessages.MISSING_LINK_DESTINATION);
      return;
    }

    Binding<? extends T> destination = getBinding(destinationKey);
    if (destination == null) {
      addError(builder.getSource(), ErrorMessages.LINK_DESTINATION_NOT_FOUND,
          destinationKey);
      return;
    }

    Binding<?> binding =
        Binding.newInstance(container, builder.getKey(), builder.getSource(),
            destination.getInternalFactory());

    putBinding(binding);
  }

  @SuppressWarnings({"unchecked"})
  private <T> Binding<T> getBinding(Key<T> destinationKey) {
    return (Binding<T>) container.internalBindings().get(destinationKey);
  }

  private void createBindings(boolean preload,
      List<ContextualCallable<Void>> preloaders) {
    for (BindingBuilder<?> builder : bindingBuilders) {
      createBinding(builder, preload, preloaders);
    }
  }

  private <T> void createBinding(BindingBuilder<T> builder,
      boolean preload, List<ContextualCallable<Void>> preloaders) {
    final Key<T> key = builder.getKey();
    final InternalFactory<? extends T> factory =
        builder.getInternalFactory(container);
    Binding<?> binding = Binding.newInstance(
        container, key, builder.getSource(), factory);

    putBinding(binding);

    // Register to preload if necessary.
    if (builder.isContainerScoped()) {
      if (preload || builder.shouldPreload()) {
        preloaders.add(new BindingPreloader(key, factory));
      }
    } else {
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

  @SuppressWarnings({"unchecked"})
  private <T> void createConstantBinding(ConstantBindingBuilder builder) {
    if (builder.hasValue()) {
      Key<T> key = (Key<T>) builder.getKey();
      InternalFactory<? extends T> factory =
          (InternalFactory<? extends T>) builder.getInternalFactory();
      Binding<?> binding =
          Binding.newInstance(container, key, builder.getSource(), factory);
      putBinding(binding);
    } else {
      addError(builder.getSource(), ErrorMessages.MISSING_CONSTANT_VALUE);
    }
  }

  void putFactory(Object source, Map<Key<?>, InternalFactory<?>> factories,
      Key<?> key, InternalFactory<?> factory) {
    if (factories.containsKey(key)) {
      addError(source, ErrorMessages.BINDING_ALREADY_SET, key);
    } else {
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
    } else {
      bindings.put(key, binding);
    }
  }

  /**
   * Currently we only support creating one Container instance per builder.
   * If we want to support creating more than one container per builder,
   * we should move to a "factory factory" model where we create a factory
   * instance per Container. Right now, one factory instance would be
   * shared across all the containers, which means container-scoped objects
   * would be shared, etc.
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
    Key<T> key;
    InternalFactory<? extends T> factory;
    Scope scope;
    String scopeName;
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
      return this;
    }

    /**
     * Sets the name of this binding.
     */
    public BindingBuilder<T> named(String name) {
      if (!this.key.hasDefaultName()) {
        addError(source, ErrorMessages.NAME_ALREADY_SET);
      }

      this.key = this.key.named(name);
      return this;
    }

    /**
     * Binds to instances of the given implementation class. The {@link
     * Container} will inject the implementation instances as well. Sets the
     * scope based on the @{@link Scoped} annotation on the implementation
     * class if present.
     */
    public <I extends T> BindingBuilder<T> to(Class<I> implementation) {
      return to(TypeLiteral.get(implementation));
    }

    /**
     * Binds to instances of the given implementation type. The {@link
     * Container} will inject the implementation instances as well. Sets the
     * scope based on the @{@link Scoped} annotation on the implementation
     * class if present.
     */
    public <I extends T> BindingBuilder<T> to(TypeLiteral<I> implementation) {
      ensureImplementationIsNotSet();
      validate(source, implementation.getRawType());
      this.factory = new DefaultFactory<I>(key, implementation);
      setScopeFromType(implementation.getRawType());
      return this;
    }

    private void setScopeFromType(Class<?> implementation) {
      for (Annotation annotation : implementation.getAnnotations()) {
        Class<? extends Annotation> annotationType =
            annotation.annotationType();
        if (annotationType == Scoped.class) {
          in(((Scoped) annotation).value());
        } else {
          Scoped scoped = annotationType.getAnnotation(Scoped.class);
          if (scoped != null) {
            in(scoped.value());
          }
        }
      }
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
      this.factory = new ConstantFactory<T>(instance);
      in(CONTAINER);
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
        addError(source, ErrorMessages.IMPLEMENTATION_ALREADY_SET);
      }
    }

    /**
     * Specifies the scope. References the name passed to {@link
     * ContainerBuilder#scope(String, Scope)}.
     */
    public BindingBuilder<T> in(String scopeName) {
      ensureScopeNotSet();

      // We could defer this lookup to when we create the container, but this
      // is fine for now.
      this.scope = scopes.get(nonNull(scopeName, "scope name"));
      if (this.scope == null) {
        addError(source, ErrorMessages.SCOPE_NOT_FOUND, scopeName,
            scopes.keySet());
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

    /**
     * Specifies container scope (i.e.&nbsp;one instance per container).
     */
    public BindingBuilder<T> inContainerScope() {
      return in(CONTAINER);
    }

    private void ensureScopeNotSet() {
      if (this.scope != null) {
        addError(source, ErrorMessages.SCOPE_ALREADY_SET);
      }
    }

    /**
     * Instructs the builder to eagerly load this binding when it creates
     * the container. Useful for application initialization logic. Currently
     * only supported for container-scoped bindings.
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

      // Default scope does nothing.
      if (scope == null || scope == DEFAULT) {
        return this.factory;
      }

      Factory<T> scoped = scope.scope(this.key,
          new FactoryToInternalFactoryAdapter<T>(container, this.factory));
      return new InternalFactoryToFactoryAdapter<T>(scoped);
    }

    boolean isContainerScoped() {
      return this.scope == Scopes.CONTAINER;
    }
  }

  /**
   * Injects new instances of the specified implementation class.
   */
  private static class DefaultFactory<T> implements InternalFactory<T> {

    volatile ConstructorInjector<T> constructor;

    private final TypeLiteral<T> implementation;
    private final Key<? super T> key;

    public DefaultFactory(Key<? super T> key, TypeLiteral<T> implementation) {
      this.key = key;
      this.implementation = implementation;
    }

    @SuppressWarnings("unchecked")
    public T get(InternalContext context) {
      if (constructor == null) {
        this.constructor =
            context.getContainerImpl().getConstructor(implementation);
      }
      return (T) constructor.construct(context, key.getRawType());
    }

    public String toString() {
      return new ToStringBuilder(Factory.class)
          .add("implementation", implementation)
          .toString();
    }
  }

  /**
   * Builds a constant binding.
   */
  public class ConstantBindingBuilder {

    final String name;
    Class<?> type;
    Object value;
    Object source = ContainerBuilder.UNKNOWN_SOURCE;

    ConstantBindingBuilder(String name) {
      this.name = name;
    }

    Key<?> getKey() {
      return Key.get(type, name);
    }

    boolean hasValue() {
      return type != null;
    }

    Object getSource() {
      return source;
    }

    InternalFactory<?> getInternalFactory() {
      return new ConstantFactory<Object>(value);
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
      if (this.value != null) {
        addError(source, ErrorMessages.CONSTANT_VALUE_ALREADY_SET);
      } else {
        this.type = type;
        this.value = value;
      }
    }
  }

  /**
   * Links one binding to another.
   */
  public class LinkedBindingBuilder<T> {

    Key<T> key;
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
  class RuntimeErrorHandler extends AbstractErrorHandler {

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
    final List<ContainerImpl.Injector> injectors =
        new ArrayList<ContainerImpl.Injector>();

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
          for (ContainerImpl.Injector injector : injectors) {
            injector.inject(context, null);
          }
          return null;
        }
      });
    }
  }

  static class BindingPreloader
      implements ContextualCallable<Void> {

    private final Key<?> key;
    private final InternalFactory<?> factory;

    public BindingPreloader(Key<?> key, InternalFactory<?> factory) {
      this.key = key;
      this.factory = factory;
    }

    public Void call(InternalContext context) {
      ExternalContext<?> externalContext =
          ExternalContext.newInstance(null, key, context.getContainerImpl());
      context.setExternalContext(externalContext);
      try {
        factory.get(context);
        return null;
      } finally {
        context.setExternalContext(null);
      }
    }
  }
}
