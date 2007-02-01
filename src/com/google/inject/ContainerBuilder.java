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

import com.google.inject.util.Objects;
import static com.google.inject.util.Objects.nonNull;

import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.HashSet;
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
public final class ContainerBuilder {

  private static final Logger logger =
      Logger.getLogger(ContainerBuilder.class.getName());

  final List<BindingBuilder<?>> bindingBuilders =
      new ArrayList<BindingBuilder<?>>();
  final List<ConstantBindingBuilder> constantBindingBuilders =
      new ArrayList<ConstantBindingBuilder>();
  final List<LinkedBindingBuilder<?>> linkedBindingBuilders =
      new ArrayList<LinkedBindingBuilder<?>>();
  final Map<String, Scope> scopes = new HashMap<String, Scope>();

  final List<Class<?>> staticInjections = new ArrayList<Class<?>>();

  boolean created;

  /**
   * Keeps error messages in order and prevents duplicates.
   */
  Set<ErrorMessage> errorMessages = new LinkedHashSet<ErrorMessage>();

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

  static final Scope DEFAULT_SCOPE = new Scope() {
    public <T> Factory<T> scope(Key<T> key, Factory<T> creator) {
      // We special case optimize default scope, so this never actually runs.
      throw new AssertionError();
    }
  };

  /**
   * Constructs a new builder.
   */
  public ContainerBuilder() {
    put(Scopes.DEFAULT, DEFAULT_SCOPE);
    put(Scopes.CONTAINER, ContainerScope.INSTANCE);

    bind(Container.class).to(CONTAINER_FACTORY);
    bind(Logger.class).to(LOGGER_FACTORY);
  }

  final List<Validation> validations = new ArrayList<Validation>();

  /**
   * Registers a type to be validated for injection when we create the
   * container.
   */
  void validate(final Object source, final Class<?> type) {
    validations.add(new Validation() {
      public void run(ContainerImpl container) {
        ErrorHandler previous = container.getErrorHandler();
        try {
          container.setErrorHandler(new ConfigurationErrorHandler(source));
          container.getConstructor(type);
        } finally {
          container.setErrorHandler(previous);
        }
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
   * Maps a {@link Scope} instance to a given scope name. Scopes should be
   * mapped before used in bindings. @{@link Scoped#value()} references this
   * name.
   */
  public void put(String name, Scope scope) {
    if (scopes.containsKey(nonNull(name, "name"))) {
      addError(source(), ErrorMessage.DUPLICATE_SCOPES, name);
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
    staticInjections.addAll(Arrays.asList(types));
  }

  /**
   * Applies the given module to this builder.
   */
  public void apply(Module module) {
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
  void add(ErrorMessage errorMessage) {
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
    // Only one Container per builder.
    ensureNotCreated();
    created = true;

    stopwatch.resetAndLog(logger, "Configuration");

    // Create the container.
    HashMap<Key<?>, InternalFactory<?>> factories =
        new HashMap<Key<?>, InternalFactory<?>>();
    ContainerImpl container = new ContainerImpl(factories);

    createConstantBindings(factories);

    // Commands to execute before returning the Container instance.
    final List<ContainerImpl.ContextualCallable<Void>> preloaders =
        new ArrayList<ContainerImpl.ContextualCallable<Void>>();

    createBindings(container, factories, preload, preloaders);
    createLinkedBindings(factories);

    stopwatch.resetAndLog(logger, "Binding creation");

    // Run validations.
    for (Validation validation : validations) {
      validation.run(container);
    }

    stopwatch.resetAndLog(logger, "Validation");

    // Blow up.
    if (!errorMessages.isEmpty()) {
      throw new ContainerCreationException(createErrorMessage());
    }

    // Switch to runtime error handling.
    container.setErrorHandler(new RuntimeErrorHandler());

    // Inject static members.
    container.injectStatics(staticInjections);

    stopwatch.resetAndLog(logger, "Static member injection");

    // Run preloading commands.
    if (preload) {
      container.callInContext(new ContainerImpl.ContextualCallable<Void>() {
        public Void call(InternalContext context) {
          for (ContainerImpl.ContextualCallable<Void> preloader
              : preloaders) {
            preloader.call(context);
          }
          return null;
        }
      });
    }

    stopwatch.resetAndLog(logger, "Preloading");

    return container;
  }

  private String createErrorMessage() {
    StringBuilder error = new StringBuilder();
    error.append("Guice configuration errors:\n\n");
    int index = 1;
    for (ErrorMessage errorMessage : errorMessages) {
      error.append(index++)
          .append(") ")
          .append("Error at ")
          .append(errorMessage.getSource())
          .append(':')
          .append('\n')
          .append("  ")
          .append(errorMessage.getMessage())
          .append("\n\n");
    }
    error.append(errorMessages.size()).append(" error[s]\n");
    return error.toString();
  }

  private void createLinkedBindings(
      HashMap<Key<?>, InternalFactory<?>> factories) {
    for (LinkedBindingBuilder<?> builder : linkedBindingBuilders) {
      // TODO: Support alias to a later-declared alias.
      Key<?> destination = builder.getDestination();
      if (destination == null) {
        addError(builder.getSource(), ErrorMessage.MISSING_LINK_DESTINATION);
        continue;
      }

      InternalFactory<?> factory = factories.get(destination);
      if (factory == null) {
        addError(builder.getSource(), ErrorMessage.LINK_DESTINATION_NOT_FOUND,
            destination);
        continue;
      }

      putFactory(builder.getSource(), factories, builder.getKey(), factory);
    }
  }

  private void createBindings(ContainerImpl container,
      HashMap<Key<?>, InternalFactory<?>> factories, boolean preload,
      List<ContainerImpl.ContextualCallable<Void>> preloaders) {
    for (BindingBuilder<?> builder : bindingBuilders) {
      final Key<?> key = builder.getKey();
      final InternalFactory<?> factory = builder.getInternalFactory(container);
      putFactory(builder.getSource(), factories, key, factory);

      if (preload && builder.isInContainerScope()) {
        preloaders.add(new ContainerImpl.ContextualCallable<Void>() {
          public Void call(InternalContext context) {
            context.setExternalContext(
                ExternalContext.newInstance(null, key,
                    context.getContainerImpl()));
            try {
              factory.get(context);
              return null;
            } finally {
              context.setExternalContext(null);
            }
          }
        });
      }
    }
  }

  private void createConstantBindings(
      HashMap<Key<?>, InternalFactory<?>> factories) {
    for (ConstantBindingBuilder builder : constantBindingBuilders) {
      if (builder.hasValue()) {
        Key<?> key = builder.getKey();
        InternalFactory<?> factory = builder.getInternalFactory();
        putFactory(builder.getSource(), factories, key, factory);
      } else {
        addError(builder.getSource(), ErrorMessage.MISSING_CONSTANT_VALUE);
      }
    }
  }

  void putFactory(Object source, Map<Key<?>, InternalFactory<?>> factories,
      Key<?> key, InternalFactory<?> factory) {
    if (factories.containsKey(key)) {
      addError(source, ErrorMessage.BINDING_ALREADY_SET, key);
    } else {
      factories.put(key, factory);
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
    if (created) {
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
        addError(source, ErrorMessage.NAME_ALREADY_SET);
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
      Scoped scoped = implementation.getAnnotation(Scoped.class);
      if (scoped != null) {
        in(scoped.value());
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
    BindingBuilder<T> to(T instance) {
      ensureImplementationIsNotSet();
      this.factory = new ConstantFactory<T>(instance);
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
        addError(source, ErrorMessage.IMPLEMENTATION_ALREADY_SET);
      }
    }

    /**
     * Specifies the scope. References the name passed to {@link
     * ContainerBuilder#put(String, Scope)}.
     */
    public BindingBuilder<T> in(String scopeName) {
      ensureScopeNotSet();

      // We could defer this lookup to when we create the container, but this
      // is fine for now.
      this.scope = scopes.get(nonNull(scopeName, "scope name"));
      if (this.scope == null) {
        addError(source, ErrorMessage.SCOPE_NOT_FOUND, scopeName,
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

    private void ensureScopeNotSet() {
      if (this.scope != null) {
        addError(source, ErrorMessage.SCOPE_ALREADY_SET);
      }
    }

    InternalFactory<? extends T> getInternalFactory(
        final ContainerImpl container) {
      // If an implementation wasn't specified, use the injection type.
      if (this.factory == null) {
        to(key.getType());
      }

      if (scope == null || scope == DEFAULT_SCOPE) {
        return this.factory;
      }

      Factory<T> scoped = scope.scope(this.key,
          new FactoryToInternalFactoryAdapter<T>(container, this.factory));
      return new InternalFactoryToFactoryAdapter<T>(scoped);
    }

    boolean isInContainerScope() {
      return this.scope == ContainerScope.INSTANCE;
    }
  }

  /**
   * Injects new instances of the specified implementation class.
   */
  private static class DefaultFactory<T> implements InternalFactory<T> {

    volatile ContainerImpl.ConstructorInjector<T> constructor;

    private final TypeLiteral<T> implementation;
    private final Key<? super T> key;

    public DefaultFactory(Key<? super T> key, TypeLiteral<T> implementation) {
      this.key = key;
      this.implementation = implementation;
    }

    @SuppressWarnings("unchecked")
    public T get(InternalContext context) {
      if (constructor == null) {
        // This unnecessary cast is a workaround for an annoying compiler
        // bug I keep running into.
        Object c = context.getContainerImpl().getConstructor(implementation);
        this.constructor = (ContainerImpl.ConstructorInjector<T>) c;
      }
      return (T) constructor.construct(context, key.getRawType());
    }

    public String toString() {
      return implementation.toString();
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
        addError(source, ErrorMessage.CONSTANT_VALUE_ALREADY_SET);
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
        addError(source, ErrorMessage.LINK_DESTINATION_ALREADY_SET);
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
      add(new ErrorMessage(source, message));
    }

    public void handle(Throwable t) {
      add(new ErrorMessage(source, t.getMessage()));
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

  final Set<String> skippedClassNames = new HashSet<String>(Arrays.asList(
      ContainerBuilder.class.getName(),
      AbstractModule.class.getName()
  ));

  /**
   * Instructs the builder to skip the given class in the stack trace when
   * determining the source of a binding. Use this to keep the container
   * builder from logging utility methods as the sources of bindings (i.e.
   * it will skip to the utility methods' callers instead).
   *
   * <p>Skipping only takes place after this method is called.
   */
  void skipSource(Class<?> clazz) {
    skippedClassNames.add(clazz.getName());
  }

  /**
   * Creates an object pointing to the current location within the
   * configuration. If we run into a problem later, we'll be able to trace it
   * back to the original source. Useful for debugging. The default
   * implementation returns {@code ContainerBuilder}'s caller's {@code
   * StackTraceElement}.
   */
  Object source() {
    // Search up the stack until we find a class outside of this one.
    for (final StackTraceElement element : new Throwable().getStackTrace()) {
      String className = element.getClassName();
      if (!skippedClassNames.contains(className)) {
        return element;
      }
    }
    throw new AssertionError();
  }
}
