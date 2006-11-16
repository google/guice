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

import com.google.inject.util.ReferenceCache;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Default {@link Container} implementation.
 *
 * @see ContainerBuilder
 * @author crazybob@google.com (Bob Lee)
 */
class ContainerImpl implements Container {

  final Map<Key<?>, InternalFactory<?>> factories;

  ContainerImpl(Map<Key<?>, InternalFactory<?>> factories) {
    this.factories = factories;
  }

  @SuppressWarnings("unchecked")
  <T> InternalFactory<? extends T> getFactory(Key<T> key) {
    return (InternalFactory<T>) factories.get(key);
  }

  /**
   * Field and method injectors.
   */
  final Map<Class<?>, List<Injector>> injectors =
      new ReferenceCache<Class<?>, List<Injector>>() {
        protected List<Injector> create(Class<?> key) {
          List<Injector> injectors = new ArrayList<Injector>();
          addInjectors(key, injectors);
          return injectors;
        }
      };

  /**
   * Recursively adds injectors for fields and methods from the given class to
   * the given list. Injects parent classes before sub classes.
   */
  void addInjectors(Class clazz, List<Injector> injectors) {
    if (clazz == Object.class) {
      return;
    }

    // Add injectors for superclass first.
    addInjectors(clazz.getSuperclass(), injectors);

    // TODO (crazybob): Filter out overridden members.
    addInjectorsForFields(clazz.getDeclaredFields(), false, injectors);
    addInjectorsForMethods(clazz.getDeclaredMethods(), false, injectors);
  }

  void injectStatics(List<Class<?>> staticInjections) {
    final List<Injector> injectors = new ArrayList<Injector>();

    for (Class<?> clazz : staticInjections) {
      addInjectorsForFields(clazz.getDeclaredFields(), true, injectors);
      addInjectorsForMethods(clazz.getDeclaredMethods(), true, injectors);
    }

    callInContext(new ContextualCallable<Void>() {
      public Void call(InternalContext context) {
        for (Injector injector : injectors) {
          injector.inject(context, null);
        }
        return null;
      }
    });
  }

  void addInjectorsForMethods(Method[] methods, boolean statics,
      List<Injector> injectors) {
    addInjectorsForMembers(Arrays.asList(methods), statics, injectors,
        new InjectorFactory<Method>() {
          public Injector create(ContainerImpl container, Method method,
              String name) throws MissingDependencyException {
            return new MethodInjector(container, method, name);
          }
        });
  }

  void addInjectorsForFields(Field[] fields, boolean statics,
      List<Injector> injectors) {
    addInjectorsForMembers(Arrays.asList(fields), statics, injectors,
        new InjectorFactory<Field>() {
          public Injector create(ContainerImpl container, Field field,
              String name) throws MissingDependencyException {
            return new FieldInjector(container, field, name);
          }
        });
  }

  <M extends Member & AnnotatedElement> void addInjectorsForMembers(
      List<M> members, boolean statics, List<Injector> injectors,
      InjectorFactory<M> injectorFactory) {
    for (M member : members) {
      if (isStatic(member) == statics) {
        Inject inject = member.getAnnotation(Inject.class);
        if (inject != null) {
          try {
            injectors.add(injectorFactory.create(this, member, inject.value()));
          } catch (MissingDependencyException e) {
            if (inject.required()) {
              throw new DependencyException(e);
            }
          }
        }
      }
    }
  }

  interface InjectorFactory<M extends Member & AnnotatedElement> {
    Injector create(ContainerImpl container, M member, String name)
        throws MissingDependencyException;
  }

  private boolean isStatic(Member member) {
    return Modifier.isStatic(member.getModifiers());
  }

  static class FieldInjector implements Injector {

    final Field field;
    final InternalFactory<?> factory;
    final ExternalContext<?> externalContext;

    public FieldInjector(ContainerImpl container, Field field, String name)
        throws MissingDependencyException {
      this.field = field;
      field.setAccessible(true);

      Key<?> key = Key.newInstance(field.getType(), name);
      factory = container.getFactory(key);
      if (factory == null) {
        throw new MissingDependencyException(
            "No mapping found for dependency " + key + " in " + field + ".");
      }

      this.externalContext = ExternalContext.newInstance(field, key, container);
    }

    public void inject(InternalContext context, Object o) {
      ExternalContext<?> previous = context.getExternalContext();
      context.setExternalContext(externalContext);
      try {
        field.set(o, factory.create(context));
      } catch (IllegalAccessException e) {
        throw new AssertionError(e);
      } finally {
        context.setExternalContext(previous);
      }
    }
  }

  /**
   * Gets parameter injectors.
   *
   * @param member to which the parameters belong
   * @param annotations on the parameters
   * @param parameterTypes parameter types
   * @return injections
   */
  <M extends AccessibleObject & Member> ParameterInjector<?>[]
      getParametersInjectors(M member,
      Annotation[][] annotations, Class[] parameterTypes, String defaultName)
      throws MissingDependencyException {
    List<ParameterInjector<?>> parameterInjectors =
        new ArrayList<ParameterInjector<?>>();

    Iterator<Annotation[]> annotationsIterator =
        Arrays.asList(annotations).iterator();
    for (Class<?> parameterType : parameterTypes) {
      Inject annotation = findInject(annotationsIterator.next());
      String name = annotation == null ? defaultName : annotation.value();
      Key<?> key = Key.newInstance(parameterType, name);
      parameterInjectors.add(createParameterInjector(key, member));
    }

    return toArray(parameterInjectors);
  }

  <T> ParameterInjector<T> createParameterInjector(
      Key<T> key, Member member) throws MissingDependencyException {
    InternalFactory<? extends T> factory = getFactory(key);
    if (factory == null) {
      throw new MissingDependencyException(
          "No mapping found for dependency " + key + " in " + member + ".");
    }

    ExternalContext<T> externalContext =
        ExternalContext.newInstance(member, key, this);
    return new ParameterInjector<T>(externalContext, factory);
  }

  @SuppressWarnings("unchecked")
  private ParameterInjector<?>[] toArray(
      List<ParameterInjector<?>> parameterInjections) {
    return parameterInjections.toArray(
        new ParameterInjector[parameterInjections.size()]);
  }

  /**
   * Finds the {@link Inject} annotation in an array of annotations.
   */
  Inject findInject(Annotation[] annotations) {
    for (Annotation annotation : annotations) {
      if (annotation.annotationType() == Inject.class) {
        return Inject.class.cast(annotation);
      }
    }
    return null;
  }

  static class MethodInjector implements Injector {

    final Method method;
    final ParameterInjector<?>[] parameterInjectors;

    public MethodInjector(ContainerImpl container, Method method, String name)
        throws MissingDependencyException {
      this.method = method;
      method.setAccessible(true);

      Class<?>[] parameterTypes = method.getParameterTypes();
      if (parameterTypes.length == 0) {
        throw new DependencyException(
            method + " has no parameters to inject.");
      }
      parameterInjectors = container.getParametersInjectors(
          method, method.getParameterAnnotations(), parameterTypes, name);
    }

    public void inject(InternalContext context, Object o) {
      try {
        method.invoke(o, getParameters(method, context, parameterInjectors));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  Map<Class<?>, ConstructorInjector> constructors =
      new ReferenceCache<Class<?>, ConstructorInjector>() {
        @SuppressWarnings("unchecked")
        protected ConstructorInjector<?> create(Class<?> implementation) {
          return new ConstructorInjector(ContainerImpl.this, implementation);
        }
      };

  static class ConstructorInjector<T> {

    final Class<T> implementation;
    final List<Injector> injectors;
    final Constructor<T> constructor;
    final ParameterInjector<?>[] parameterInjectors;

    ConstructorInjector(ContainerImpl container, Class<T> implementation) {
      this.implementation = implementation;

      constructor = findConstructorIn(implementation);
      constructor.setAccessible(true);

      try {
        Inject inject = constructor.getAnnotation(Inject.class);
        parameterInjectors = inject == null
            ? null // default constructor.
            : container.getParametersInjectors(
                constructor,
                constructor.getParameterAnnotations(),
                constructor.getParameterTypes(),
                inject.value()
              );
      } catch (MissingDependencyException e) {
        throw new DependencyException(e);
      }
      injectors = container.injectors.get(implementation);
    }

    private Constructor<T> findConstructorIn(Class<T> implementation) {
      Constructor<T> found = null;
      for (Constructor<T> constructor
          : implementation.getDeclaredConstructors()) {
        if (constructor.getAnnotation(Inject.class) != null) {
          if (found != null) {
            throw new DependencyException("More than one constructor annotated"
              + " with @Inject found in " + implementation + ".");
          }
          found = constructor;
        }
      }
      if (found != null) {
        return found;
      }

      // If no annotated constructor is found, look for a no-arg constructor
      // instead.
      try {
        return implementation.getDeclaredConstructor();
      } catch (NoSuchMethodException e) {
        throw new DependencyException("Could not find a suitable constructor"
            + " in " + implementation.getName() + ".");
      }
    }

    /**
     * Construct an instance. Returns {@code Object} instead of {@code T}
     * because it may return a proxy.
     */
    Object construct(InternalContext context, Class<? super T> expectedType) {
      ConstructionContext<T> constructionContext =
          context.getConstructionContext(this);

      // We have a circular reference between constructors. Return a proxy.
      if (constructionContext.isConstructing()) {
        // TODO (crazybob): if we can't proxy this object, can we proxy the
        // other object?
        return constructionContext.createProxy(expectedType);
      }

      // If we're re-entering this factory while injecting fields or methods,
      // return the same instance. This prevents infinite loops.
      T t = constructionContext.getCurrentReference();
      if (t != null) {
        return t;
      }

      try {
        // First time through...
        constructionContext.startConstruction();
        try {
          Object[] parameters =
              getParameters(constructor, context, parameterInjectors);
          t = constructor.newInstance(parameters);
          constructionContext.setProxyDelegates(t);
        } finally {
          constructionContext.finishConstruction();
        }

        // Store reference. If an injector re-enters this factory, they'll
        // get the same reference.
        constructionContext.setCurrentReference(t);

        // Inject fields and methods.
        for (Injector injector : injectors) {
          injector.inject(context, t);
        }

        return t;
      } catch (InstantiationException e) {
        throw new RuntimeException(e);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      } catch (InvocationTargetException e) {
        throw new RuntimeException(e);
      } finally {
        constructionContext.removeCurrentReference();
      }
    }
  }

  static class ParameterInjector<T> {

    final ExternalContext<T> externalContext;
    final InternalFactory<? extends T> factory;

    public ParameterInjector(ExternalContext<T> externalContext,
        InternalFactory<? extends T> factory) {
      this.externalContext = externalContext;
      this.factory = factory;
    }

    T inject(Member member, InternalContext context) {
      ExternalContext<?> previous = context.getExternalContext();
      context.setExternalContext(externalContext);
      try {
        return factory.create(context);
      } finally {
        context.setExternalContext(previous);
      }
    }
  }

  private static Object[] getParameters(Member member, InternalContext context,
      ParameterInjector[] parameterInjectors) {
    if (parameterInjectors == null) {
      return null;
    }

    Object[] parameters = new Object[parameterInjectors.length];
    for (int i = 0; i < parameters.length; i++) {
      parameters[i] = parameterInjectors[i].inject(member, context);
    }
    return parameters;
  }

  void inject(Object o, InternalContext context) {
    List<Injector> injectors = this.injectors.get(o.getClass());
    for (Injector injector : injectors) {
      injector.inject(context, o);
    }
  }

  <T> T inject(Class<T> implementation, InternalContext context) {
    try {
      ConstructorInjector<T> constructor = getConstructor(implementation);
      return implementation.cast(
          constructor.construct(context, implementation));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("unchecked")
  <T> T getInstance(Class<T> type, String name, InternalContext context) {
    ExternalContext<?> previous = context.getExternalContext();
    Key<T> key = Key.newInstance(type, name);
    context.setExternalContext(ExternalContext.newInstance(null, key, this));
    try {
      InternalFactory o = getFactory(key);
      if (o != null) {
          return getFactory(key).create(context);
      } else {
          return null;
      }
    } finally {
      context.setExternalContext(previous);
    }
  }

  <T> T getInstance(Class<T> type, InternalContext context) {
    return getInstance(type, DEFAULT_NAME, context);
  }

  public void inject(final Object o) {
    callInContext(new ContextualCallable<Void>() {
      public Void call(InternalContext context) {
        inject(o, context);
        return null;
      }
    });
  }

  public <T> T inject(final Class<T> implementation) {
    return callInContext(new ContextualCallable<T>() {
      public T call(InternalContext context) {
        return inject(implementation, context);
      }
    });
  }

  public <T> T getInstance(final Class<T> type, final String name) {
    return callInContext(new ContextualCallable<T>() {
      public T call(InternalContext context) {
        return getInstance(type, name, context);
      }
    });
  }

  public <T> T getInstance(final Class<T> type) {
    return callInContext(new ContextualCallable<T>() {
      public T call(InternalContext context) {
        return getInstance(type, context);
      }
    });
  }

  ThreadLocal<InternalContext[]> localContext =
      new ThreadLocal<InternalContext[]>() {
        protected InternalContext[] initialValue() {
          return new InternalContext[1];
        }
      };

  /**
   * Looks up thread local context. Creates (and removes) a new context if
   * necessary.
   */
  <T> T callInContext(ContextualCallable<T> callable) {
    InternalContext[] reference = localContext.get();
    if (reference[0] == null) {
      reference[0] = new InternalContext(this);
      try {
        return callable.call(reference[0]);
      } finally {
        // Only remove the context if this call created it.
        reference[0] = null;
      }
    } else {
      // Someone else will clean up this context.
      return callable.call(reference[0]);
    }
  }

  interface ContextualCallable<T> {
    T call(InternalContext context);
  }

  /**
   * Gets a constructor function for a given implementation class.
   */
  @SuppressWarnings("unchecked")
  <T> ConstructorInjector<T> getConstructor(Class<T> implementation) {
    return constructors.get(implementation);
  }

  final ThreadLocal<Scope.Strategy> localScopeStrategy =
      new ThreadLocal<Scope.Strategy>();

  public void setScopeStrategy(Scope.Strategy scopeStrategy) {
    this.localScopeStrategy.set(scopeStrategy);
  }

  public void removeScopeStrategy() {
    this.localScopeStrategy.remove();
  }

  /**
   * Injects a field or method in a given object.
   */
  interface Injector {
    void inject(InternalContext context, Object o);
  }

  static class MissingDependencyException extends Exception {

    MissingDependencyException(String message) {
      super(message);
    }
  }
}
