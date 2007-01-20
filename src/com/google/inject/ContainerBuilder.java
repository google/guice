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
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.logging.Logger;

/**
 * Builds a dependency injection {@link Container}. Binds {@link Key}s to
 * implementations. For example, a binding implementation could be anything
 * from a constant value to an object in the HTTP session.
 *
 * <p>Not safe for concurrent use.
 *
 * <p>Default bindings include:
 *
 * <ul>
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

  final List<BindingBuilder<?>> bindingBuilders =
      new ArrayList<BindingBuilder<?>>();
  final List<ConstantBindingBuilder> constantBindingBuilders =
      new ArrayList<ConstantBindingBuilder>();
  final List<LinkedBindingBuilder<?>> linkedBindingBuilders =
      new ArrayList<LinkedBindingBuilder<?>>();

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

  /**
   * Constructs a new builder.
   */
  public ContainerBuilder() {
    bind(Container.class).to(CONTAINER_FACTORY);
    bind(Logger.class).to(LOGGER_FACTORY);
  }

  /**
   * Creates a source object to be associated with a binding. Called by
   * default for each binding. The default implementation returns {@code
   * ContainerBuilder}'s caller's {@code StackTraceElement}.
   *
   * <p>If you plan on manually setting the source (say for example you've
   * implemented an XML configuration), you might override this method and
   * return {@code null} to avoid unnecessary overhead.
   */
  protected Object source() {
    // Search up the stack until we find a class outside of this one.
    for (StackTraceElement element : new Throwable().getStackTrace()) {
      if (!element.getClassName().equals(ContainerBuilder.class.getName()))
        return element;
    }
    throw new AssertionError();
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
  public <T> BindingBuilder<T> bind(TypeToken<T> typeToken) {
    return bind(Key.get(typeToken));
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
   * Binds string constants based on the given properties.
   */
  public ContainerBuilder bindProperties(Map<String, String> properties) {
    ensureNotCreated();
    Object source = source();
    for (Map.Entry<String, String> entry : properties.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      bind(key, source).to(value);
    }
    return this;
  }

  /**
   * Binds string constants based on the given properties.
   */
  public ContainerBuilder bindProperties(Properties properties) {
    ensureNotCreated();
    Object source = source();
    for (Map.Entry<Object, Object> entry : properties.entrySet()) {
      String key = (String) entry.getKey();
      String value = (String) entry.getValue();
      bind(key, source).to(value);
    }
    return this;
  }

  /**
   * Upon creation, the {@link Container} will inject static fields and methods
   * in the given classes.
   *
   * @param types for which static members will be injected
   */
  public ContainerBuilder injectStatics(Class<?>... types) {
    staticInjections.addAll(Arrays.asList(types));
    return this;
  }

  /**
   * Adds an error message to be reported at creation time.
   */
  void add(ErrorMessage errorMessage) {
    errorMessages.add(errorMessage);
  }

  /**
   * Creates a {@link Container} instance. Injects static members for classes
   * which were registered using {@link #injectStatics(Class...)}.
   *
   * @param loadSingletons If true, the container will load all singletons
   *  now. If false, the container will lazily load singletons. Eager loading
   *  is appropriate for production use while lazy loading can speed
   *  development.
   * @throws IllegalStateException if called more than once
   */
  public Container create(boolean loadSingletons) {
    ensureNotCreated();
    created = true;

    HashMap<Key<?>, InternalFactory<?>> factories =
        new HashMap<Key<?>, InternalFactory<?>>();

    for (ConstantBindingBuilder builder : constantBindingBuilders) {
      if (builder.hasValue()) {
        factories.put(builder.getKey(), builder.getInternalFactory());
      } else {
        add(new ErrorMessage(builder.getSource(),
            "Constant value isn't set."));
      }
    }

    final List<ContainerImpl.ContextualCallable<Void>> singletonLoaders =
        new ArrayList<ContainerImpl.ContextualCallable<Void>>();

    for (BindingBuilder<?> builder : bindingBuilders) {
      final Key<?> key = builder.getKey();
      final InternalFactory<?> factory = builder.getInternalFactory();
      factories.put(key, factory);

      if (builder.isSingleton()) {
        singletonLoaders.add(new ContainerImpl.ContextualCallable<Void>() {
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

    for (LinkedBindingBuilder<?> builder : linkedBindingBuilders) {
      // TODO: Support alias to a later-declared alias.
      Key<?> destination = builder.getDestination();
      if (destination == null) {
        add(new ErrorMessage(builder.getSource(),
            "Link destination isn't set."));
        continue;
      }

      InternalFactory<?> factory = factories.get(destination);
      if (factory == null) {
        add(new ErrorMessage(builder.getSource(),
            "Destination of link binding not found: " + destination));
        continue;
      }

      factories.put(builder.getKey(), factory);
    }

    // TODO: Handle this better.
    if (!errorMessages.isEmpty()) {
      for (ErrorMessage errorMessage : errorMessages) {
        System.err.println(errorMessage);
        throw new DependencyException("Configuration errors.");
      }
    }

    final ContainerImpl container = new ContainerImpl(factories);

    container.injectStatics(staticInjections);

    if (loadSingletons) {
      container.callInContext(new ContainerImpl.ContextualCallable<Void>() {
        public Void call(InternalContext context) {
          for (ContainerImpl.ContextualCallable<Void> singletonLoader
              : singletonLoaders) {
            singletonLoader.call(context);
          }
          return null;
        }
      });
    }

    return container;
  }

  /**
   * Currently we only support creating one Container instance per builder.
   * If we want to support creating more than one container per builder,
   * we should move to a "factory factory" model where we create a factory
   * instance per Container. Right now, one factory instance would be
   * shared across all the containers, singletons synchronize on the
   * container when lazy loading, etc.
   */
  private void ensureNotCreated() {
    if (created) {
      throw new IllegalStateException("Container already created.");
    }
  }

  /**
   * Implemented by classes which participate in building a container. Useful
   * for encapsulating and reusing configuration logic.
   */
  public interface Command {

    /**
     * Configures the given builder.
     */
    void build(ContainerBuilder builder);
  }

  /**
   * Binds a {@link Key} to an implementation in a given scope.
   */
  public class BindingBuilder<T> implements SourceAware<BindingBuilder<T>> {

    Object source = ContainerBuilder.UNKNOWN_SOURCE;
    Key<T> key;
    InternalFactory<? extends T> factory;
    Scope scope;

    BindingBuilder(Key<T> key) {
      this.key = nonNull(key, "key");
    }

    Key<T> getKey() {
      return key;
    }

    public BindingBuilder<T> from(Object source) {
      this.source = source;
      return this;
    }

    /**
     * Sets the name of this binding.
     */
    public BindingBuilder<T> named(String name) {
      if (!this.key.hasDefaultName()) {
        add(new ErrorMessage(source, "Name set more than once."));
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
      return to(TypeToken.get(implementation));
    }

    /**
     * Binds to instances of the given implementation type. The {@link
     * Container} will inject the implementation instances as well. Sets the
     * scope based on the @{@link Scoped} annotation on the implementation
     * class if present.
     */
    public <I extends T> BindingBuilder<T> to(TypeToken<I> implementation) {
      ensureImplementationIsNotSet();
      this.factory = new DefaultFactory<I>(implementation);
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

      this.factory = new InternalFactory<T>() {
        public T get(InternalContext context) {
          return factory.get(context.getExternalContext());
        }

        public String toString() {
          return factory.toString();
        }
      };

      return this;
    }

    /**
     * Binds to instances from the given factory.
     */
    public BindingBuilder<T> to(final Factory<? extends T> factory) {
      ensureImplementationIsNotSet();

      this.factory = new InternalFactory<T>() {
        public T get(InternalContext context) {
          return factory.get();
        }

        public String toString() {
          return factory.toString();
        }
      };

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
        add(new ErrorMessage(source, "Implementation set more than once."));
      }
    }

    /**
     * Specifies the scope.
     */
    public BindingBuilder<T> in(Scope scope) {
      if (this.scope != null) {
        add(new ErrorMessage(source, "Scope set more than once."));
      }

      this.scope = scope;
      return this;
    }

    InternalFactory<? extends T> getInternalFactory() {
      // If an implementation wasn't specified, use the injection type.
      if (this.factory == null) {
        to(key.getTypeToken());
      }

      if (scope == null) {
        return this.factory;
      }

      return scope.scopeFactory(key, this.factory);
    }

    boolean isSingleton() {
      return this.scope == Scope.SINGLETON;
    }

    /**
     * Injects new instances of the specified implementation class.
     */
    private class DefaultFactory<I extends T> implements InternalFactory<I> {

      volatile ContainerImpl.ConstructorInjector<I> constructor;

      private final TypeToken<I> implementation;

      public DefaultFactory(TypeToken<I> implementation) {
        // TODO: Ensure this is a concrete implementation.
        this.implementation = implementation;
      }

      @SuppressWarnings("unchecked")
      public I get(InternalContext context) {
        if (constructor == null) {
          this.constructor =
              context.getContainerImpl().getConstructor(implementation);
        }
        return (I) constructor.construct(context, key.getRawType());
      }

      public String toString() {
        return implementation.toString();
      }
    }
  }

  /**
   * Builds a constant binding.
   */
  public class ConstantBindingBuilder
      implements SourceAware<ConstantBindingBuilder> {

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

    public ConstantBindingBuilder from(Object source) {
      this.source = source;
      return this;
    }

    /**
     * Binds constant to the given value.
     */
    public ConstantBindingBuilder to(String value) {
      return to(String.class, value);
    }

    /**
     * Binds constant to the given value.
     */
    public ConstantBindingBuilder to(int value) {
      return to(int.class, value);
    }

    /**
     * Binds constant to the given value.
     */
    public ConstantBindingBuilder to(long value) {
      return to(long.class, value);
    }

    /**
     * Binds constant to the given value.
     */
    public ConstantBindingBuilder to(boolean value) {
      return to(boolean.class, value);
    }

    /**
     * Binds constant to the given value.
     */
    public ConstantBindingBuilder to(double value) {
      return to(double.class, value);
    }

    /**
     * Binds constant to the given value.
     */
    public ConstantBindingBuilder to(float value) {
      return to(float.class, value);
    }

    /**
     * Binds constant to the given value.
     */
    public ConstantBindingBuilder to(short value) {
      return to(short.class, value);
    }

    /**
     * Binds constant to the given value.
     */
    public ConstantBindingBuilder to(char value) {
      return to(char.class, value);
    }

    /**
     * Binds constant to the given value.
     */
    public ConstantBindingBuilder to(Class<?> value) {
      return to(Class.class, value);
    }

    /**
     * Binds constant to the given value.
     */
    public <E extends Enum<E>> ConstantBindingBuilder to(E value) {
      return to(value.getDeclaringClass(), value);
    }

    /**
     * Maps a constant value to the given type and name.
     */
    <T> ConstantBindingBuilder to(final Class<T> type, final T value) {
      this.type = type;
      this.value = value;
      return this;
    }
  }

  /**
   * Links one binding to another.
   */
  public class LinkedBindingBuilder<T>
      implements SourceAware<LinkedBindingBuilder<T>> {

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

    public LinkedBindingBuilder<T> from(Object source) {
      this.source = source;
      return this;
    }

    /**
     * Links to another binding with the given key.
     */
    public LinkedBindingBuilder<T> to(Key<? extends T> destination) {
      this.destination = destination;
      return this;
    }
  }
}
