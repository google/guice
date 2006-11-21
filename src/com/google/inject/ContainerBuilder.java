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

import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Builds a dependency injection {@link Container}. The combination of
 * dependency type and name uniquely identifies a dependency mapping; you can
 * use the same name for two different types. Not safe for concurrent use.
 *
 * <p>Adds the following factories by default:
 *
 * <ul>
 *   <li>Injects the current {@link Container}.
 *   <li>Injects the {@link Logger} for the injected member's declaring class.
 * </ul>
 *
 * <p>Converts constants as needed from {@code String} to any primitive type
 * in addition to {@code enum} and {@code Class}.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public final class ContainerBuilder {

  final Map<Key<?>, InternalFactory<?>> factories =
      new HashMap<Key<?>, InternalFactory<?>>();
  final List<InternalFactory<?>> singletonFactories =
      new ArrayList<InternalFactory<?>>();
  final List<Class<?>> staticInjections = new ArrayList<Class<?>>();
  boolean created;

  private static final InternalFactory<Container> CONTAINER_FACTORY =
      new InternalFactory<Container>() {
        public Container create(InternalContext context) {
          return context.getContainer();
        }
      };

  private static final InternalFactory<Logger> LOGGER_FACTORY =
      new InternalFactory<Logger>() {
        public Logger create(InternalContext context) {
          Member member = context.getExternalContext().getMember();
          return member == null ? Logger.getAnonymousLogger()
              : Logger.getLogger(member.getDeclaringClass().getName());
        }
      };

  /**
   * Constructs a new builder.
   */
  public ContainerBuilder() {
    // In the current container as the default Container implementation.
    factories.put(Key.newInstance(Container.class, Container.DEFAULT_NAME),
        CONTAINER_FACTORY);

    // Inject the logger for the injected member's declaring class.
    factories.put(Key.newInstance(Logger.class, Container.DEFAULT_NAME),
        LOGGER_FACTORY);
  }

  /**
   * Maps a dependency. All methods in this class ultimately funnel through
   * here.
   */
  private <T> ContainerBuilder factory(final Key<T> key,
      InternalFactory<? extends T> factory, Scope scope) {
    ensureNotCreated();
    checkKey(key);
    final InternalFactory<? extends T> scopedFactory =
        scope.scopeFactory(key.getType(), key.getName(), factory);
    factories.put(key, scopedFactory);
    if (scope == Scope.SINGLETON) {
      singletonFactories.add(new InternalFactory<T>() {
        public T create(InternalContext context) {
          try {
            context.setExternalContext(ExternalContext.newInstance(
                null, key, context.getContainerImpl()));
            return scopedFactory.create(context);
          } finally {
            context.setExternalContext(null);
          }
        }
      });
    }
    return this;
  }

  /**
   * Ensures a key isn't already mapped.
   */
  private void checkKey(Key<?> key) {
    if (factories.containsKey(key)) {
      throw new DependencyException(
          "Dependency mapping for " + key + " already exists.");
    }
  }

  /**
   * Maps a factory to a given dependency type and name.
   *
   * @param type of dependency
   * @param name of dependency
   * @param factory creates objects to inject
   * @param scope scope of injected instances
   * @return this builder
   */
  public <T> ContainerBuilder factory(final Class<T> type, final String name,
      final Factory<? extends T> factory, Scope scope) {
    InternalFactory<T> internalFactory =
        new InternalFactory<T>() {

          public T create(InternalContext context) {
            try {
              Context externalContext = context.getExternalContext();
              return factory.create(externalContext);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          }

          public String toString() {
            return new LinkedHashMap<String, Object>() {{
              put("type", type);
              put("name", name);
              put("factory", factory);
            }}.toString();
          }
        };

    return factory(Key.newInstance(type, name), internalFactory, scope);
  }

  /**
   * Convenience method.&nbsp;Equivalent to {@code factory(type,
   * Container.DEFAULT_NAME, factory, scope)}.
   *
   * @see #factory(Class, String, Factory, Scope)
   */
  public <T> ContainerBuilder factory(Class<T> type,
      Factory<? extends T> factory, Scope scope) {
    return factory(type, Container.DEFAULT_NAME, factory, scope);
  }

  /**
   * Convenience method.&nbsp;Equivalent to {@code factory(type, name, factory,
   * Scope.DEFAULT)}.
   *
   * @see #factory(Class, String, Factory, Scope)
   */
  public <T> ContainerBuilder factory(Class<T> type, String name,
      Factory<? extends T> factory) {
    return factory(type, name, factory, Scope.DEFAULT);
  }

  /**
   * Convenience method.&nbsp;Equivalent to {@code factory(type,
   * Container.DEFAULT_NAME, factory, Scope.DEFAULT)}.
   *
   * @see #factory(Class, String, Factory, Scope)
   */
  public <T> ContainerBuilder factory(Class<T> type,
      Factory<? extends T> factory) {
    return factory(type, Container.DEFAULT_NAME, factory, Scope.DEFAULT);
  }

  /**
   * Maps an implementation class to a given dependency type and name. Creates
   * instances using the container, recursively injecting dependencies.
   *
   * @param type of dependency
   * @param name of dependency
   * @param implementation class
   * @param scope scope of injected instances
   * @return this builder
   */
  public <T> ContainerBuilder factory(final Class<T> type, final String name,
      final Class<? extends T> implementation, final Scope scope) {
    // This factory creates new instances of the given implementation.
    // We have to lazy load the constructor because the Container
    // hasn't been created yet.
    InternalFactory<? extends T> factory = new InternalFactory<T>() {

      volatile ContainerImpl.ConstructorInjector<? extends T> constructor;

      @SuppressWarnings("unchecked")
      public T create(InternalContext context) {
        if (constructor == null) {
          this.constructor =
              context.getContainerImpl().getConstructor(implementation);
        }
        return (T) constructor.construct(context, type);
      }

      public String toString() {
        return new LinkedHashMap<String, Object>() {{
          put("type", type);
          put("name", name);
          put("implementation", implementation);
          put("scope", scope);
        }}.toString();
      }
    };

    return factory(Key.newInstance(type, name), factory, scope);
  }

  /**
   * Maps an implementation class to a given dependency type and name. Creates
   * instances using the container, recursively injecting dependencies.
   *
   * <p>Sets scope to value from {@link Scoped} annotation on the
   * implementation class. Defaults to {@link Scope#DEFAULT} if no annotation
   * is found.
   *
   * @param type of dependency
   * @param name of dependency
   * @param implementation class
   * @return this builder
   */
  public <T> ContainerBuilder factory(final Class<T> type, String name,
      final Class<? extends T> implementation) {
    Scoped scoped = implementation.getAnnotation(Scoped.class);
    Scope scope = scoped == null ? Scope.DEFAULT : scoped.value();
    return factory(type, name, implementation, scope);
  }

  /**
   * Convenience method.&nbsp;Equivalent to {@code factory(type,
   * Container.DEFAULT_NAME, implementation)}.
   *
   * @see #factory(Class, String, Class)
   */
  public <T> ContainerBuilder factory(Class<T> type,
      Class<? extends T> implementation) {
    return factory(type, Container.DEFAULT_NAME, implementation);
  }

  /**
   * Convenience method.&nbsp;Equivalent to {@code factory(type,
   * Container.DEFAULT_NAME, type)}.
   *
   * @see #factory(Class, String, Class)
   */
  public <T> ContainerBuilder factory(Class<T> type) {
    return factory(type, Container.DEFAULT_NAME, type);
  }

  /**
   * Convenience method.&nbsp;Equivalent to {@code factory(type, name, type)}.
   *
   * @see #factory(Class, String, Class)
   */
  public <T> ContainerBuilder factory(Class<T> type, String name) {
    return factory(type, name, type);
  }

  /**
   * Convenience method.&nbsp;Equivalent to {@code factory(type,
   * Container.DEFAULT_NAME, implementation, scope)}.
   *
   * @see #factory(Class, String, Class, Scope)
   */
  public <T> ContainerBuilder factory(Class<T> type,
      Class<? extends T> implementation, Scope scope) {
    return factory(type, Container.DEFAULT_NAME, implementation, scope);
  }

  /**
   * Convenience method.&nbsp;Equivalent to {@code factory(type,
   * Container.DEFAULT_NAME, type, scope)}.
   *
   * @see #factory(Class, String, Class, Scope)
   */
  public <T> ContainerBuilder factory(Class<T> type, Scope scope) {
    return factory(type, Container.DEFAULT_NAME, type, scope);
  }

  /**
   * Convenience method.&nbsp;Equivalent to {@code factory(type, name, type,
   * scope)}.
   *
   * @see #factory(Class, String, Class, Scope)
   */
  public <T> ContainerBuilder factory(Class<T> type, String name, Scope scope) {
    return factory(type, name, type, scope);
  }

  /**
   * Maps constant names to values.
   */
  public ContainerBuilder properties(Map<String, String> properties) {
    for (Map.Entry<String, String> entry : properties.entrySet()) {
      constant(entry.getKey(), entry.getValue());
    }
    return this;
  }

  /**
   * Maps constant names to values.
   */
  public ContainerBuilder properties(Properties properties) {
    for (Map.Entry<Object, Object> entry : properties.entrySet()) {
      constant((String) entry.getKey(), (String) entry.getValue());
    }
    return this;
  }

  /**
   * Convenience method.&nbsp;Equivalent to {@code alias(type,
   * Container.DEFAULT_NAME, type)}.
   *
   * @see #alias(Class, String, String)
   */
  public <T> ContainerBuilder alias(Class<T> type, String alias) {
    return alias(type, Container.DEFAULT_NAME, alias);
  }

  /**
   * Maps an existing factory to a new name.
   *
   * @param type of dependency
   * @param name of dependency
   * @param alias of to the dependency
   * @return this builder
   */
  public <T> ContainerBuilder alias(Class<T> type, String name, String alias) {
    return alias(Key.newInstance(type, name), Key.newInstance(type, alias));
  }

  /**
   * Maps an existing dependency. All methods in this class ultimately funnel
   * through here.
   */
  @SuppressWarnings({"unchecked"})
  private <T> ContainerBuilder alias(final Key<T> key,
      final Key<T> aliasKey) {
    ensureNotCreated();
    checkKey(aliasKey);

    final InternalFactory<? extends T> scopedFactory =
        (InternalFactory<? extends T>) factories.get(key);
    if (scopedFactory == null) {
      throw new DependencyException(
          "Dependency mapping for " + key + " doesn't exists.");
    }
    factories.put(aliasKey, scopedFactory);
    return this;
  }

  /**
   * Maps a constant value to the given name.
   */
  public ContainerBuilder constant(String name, String value) {
    return constant(String.class, name, value);
  }

  /**
   * Maps a constant value to the given name.
   */
  public ContainerBuilder constant(String name, int value) {
    return constant(int.class, name, value);
  }

  /**
   * Maps a constant value to the given name.
   */
  public ContainerBuilder constant(String name, long value) {
    return constant(long.class, name, value);
  }

  /**
   * Maps a constant value to the given name.
   */
  public ContainerBuilder constant(String name, boolean value) {
    return constant(boolean.class, name, value);
  }

  /**
   * Maps a constant value to the given name.
   */
  public ContainerBuilder constant(String name, double value) {
    return constant(double.class, name, value);
  }

  /**
   * Maps a constant value to the given name.
   */
  public ContainerBuilder constant(String name, float value) {
    return constant(float.class, name, value);
  }

  /**
   * Maps a constant value to the given name.
   */
  public ContainerBuilder constant(String name, short value) {
    return constant(short.class, name, value);
  }

  /**
   * Maps a constant value to the given name.
   */
  public ContainerBuilder constant(String name, char value) {
    return constant(char.class, name, value);
  }

  /**
   * Maps a class to the given name.
   */
  public ContainerBuilder constant(String name, Class value) {
    return constant(Class.class, name, value);
  }

  /**
   * Maps an enum to the given name.
   */
  public <E extends Enum<E>> ContainerBuilder constant(String name, E value) {
    return constant(value.getDeclaringClass(), name, value);
  }

  /**
   * Maps a constant value to the given type and name.
   */
  private <T> ContainerBuilder constant(final Class<T> type, final String name,
      final T value) {
    InternalFactory<T> factory = new ConstantFactory<T>(value);
    return factory(Key.newInstance(type, name), factory, Scope.DEFAULT);
  }

  /**
   * Upon creation, the {@link Container} will inject static fields and methods
   * into the given classes.
   *
   * @param types for which static members will be injected
   */
  public ContainerBuilder injectStatics(Class<?>... types) {
    staticInjections.addAll(Arrays.asList(types));
    return this;
  }

  /**
   * Returns true if this builder contains a mapping for the given type and
   * name.
   */
  public boolean contains(Class<?> type, String name) {
    return factories.containsKey(Key.newInstance(type, name));
  }

  /**
   * Convenience method.&nbsp;Equivalent to {@code contains(type,
   * Container.DEFAULT_NAME)}.
   */
  public boolean contains(Class<?> type) {
    return contains(type, Container.DEFAULT_NAME);
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
    final ContainerImpl container = new ContainerImpl(
        new HashMap<Key<?>, InternalFactory<?>>(factories));
    if (loadSingletons) {
      container.callInContext(new ContainerImpl.ContextualCallable<Void>() {
        public Void call(InternalContext context) {
          for (InternalFactory<?> factory : singletonFactories) {
            factory.create(context);
          }
          return null;
        }
      });
    }
    container.injectStatics(staticInjections);
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
   * Implemented by classes which participate in building a container.
   */
  public interface Command {

    /**
     * Contributes factories to the given builder.
     *
     * @param builder
     */
    void build(ContainerBuilder builder);
  }
}
