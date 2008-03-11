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

package com.google.inject;

import com.google.inject.commands.BindCommand;
import com.google.inject.commands.BindConstantCommand;
import com.google.inject.commands.BindScoping;
import com.google.inject.commands.BindTarget;
import com.google.inject.internal.Annotations;
import com.google.inject.internal.Objects;
import com.google.inject.internal.StackTraceElements;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.*;
import java.util.logging.Logger;

/**
 * Handles {@link Binder#bind} and {@link Binder#bindConstant} commands.
 *
 * @author crazybob@google.com (Bob Lee)
 * @author jessewilson@google.com (Jesse Wilson)
 */
class BindCommandProcessor extends CommandProcessor {

  private final InjectorImpl injector;
  private final Map<Class<? extends Annotation>, Scope> scopes;
  private final List<CreationListener> creationListeners
      = new ArrayList<CreationListener>();
  private final List<ContextualCallable<Void>> eagerSingletonCreators
      = new ArrayList<ContextualCallable<Void>>();
  private final Stage stage;
  private final Map<Key<?>, BindingImpl<?>> bindings;
  private final Map<Object, Void> outstandingInjections;

  BindCommandProcessor(InjectorImpl injector,
      Map<Class<? extends Annotation>, Scope> scopes,
      Stage stage,
      Map<Key<?>, BindingImpl<?>> bindings,
      Map<Object, Void> outstandingInjections) {
    this.injector = injector;
    this.scopes = scopes;
    this.stage = stage;
    this.bindings = bindings;
    this.outstandingInjections = outstandingInjections;
  }

  @Override public <T> Boolean visitBind(BindCommand<T> command) {
    final Object source = command.getSource();

    final Key<T> key = command.getKey();
    Class<? super T> rawType = key.getTypeLiteral().getRawType();

    if (rawType == Provider.class) {
      addError(source, ErrorMessages.BINDING_TO_PROVIDER);
      return true;
    }

    if (Logger.class == rawType) {
      // TODO(jessewilson): assert this is coming from the internal module?
      // addError(source, ErrorMessages.LOGGER_ALREADY_BOUND);
      // return true;
    }

    validateKey(command.getSource(), command.getKey());

    // TODO(jessewilson): Scope annotation on type, like @Singleton
    final boolean shouldPreload = command.getScoping().isEagerSingleton();
    final Scope scope = command.getScoping().acceptVisitor(new BindScoping.Visitor<Scope>() {
      public Scope visitEagerSingleton() {
        return Scopes.SINGLETON;
      }

      public Scope visitScope(Scope scope) {
        return scope;
      }

      public Scope visitScopeAnnotation(Class<? extends Annotation> scopeAnnotation) {
        Scope scope = scopes.get(scopeAnnotation);
        if (scope != null) {
          return scope;
        } else {
          addError(source, ErrorMessages.SCOPE_NOT_FOUND,
              "@" + scopeAnnotation.getSimpleName());
          return Scopes.NO_SCOPE;
        }
      }

      public Scope visitNoScoping() {
        return null;
      }
    });

    command.getTarget().acceptVisitor(new BindTarget.Visitor<T, Void>() {
      public Void visitToInstance(T instance) {
        ConstantFactory<? extends T> factory = new ConstantFactory<T>(instance);
        outstandingInjections.put(instance, null);
        InternalFactory<? extends T> scopedFactory
            = Scopes.scope(key, injector, factory, scope);
        createBinding(source, shouldPreload, new InstanceBindingImpl<T>(
            injector, key, source, scopedFactory, instance));
        return null;
      }

      public Void visitToProvider(Provider<? extends T> provider) {
        InternalFactoryToProviderAdapter<? extends T> factory
            = new InternalFactoryToProviderAdapter<T>(provider, source);
        outstandingInjections.put(provider, null);
        InternalFactory<? extends T> scopedFactory
            = Scopes.scope(key, injector, factory, scope);
        createBinding(source, shouldPreload, new ProviderInstanceBindingImpl<T>(
            injector, key, source, scopedFactory, scope, provider));
        return null;
      }

      public Void visitToProviderKey(Key<? extends Provider<? extends T>> providerKey) {
        final BoundProviderFactory<T> boundProviderFactory =
            new BoundProviderFactory<T>(providerKey, source);
        creationListeners.add(boundProviderFactory);
        InternalFactory<? extends T> scopedFactory = Scopes.scope(
            key, injector, (InternalFactory<? extends T>) boundProviderFactory, scope);
        createBinding(source, shouldPreload, new LinkedProviderBindingImpl<T>(
            injector, key, source, scopedFactory, scope, providerKey));
        return null;
      }

      public Void visitToKey(Key<? extends T> targetKey) {
        if (key.equals(targetKey)) {
          addError(source, ErrorMessages.RECURSIVE_BINDING);
        }

        FactoryProxy<T> factory = new FactoryProxy<T>(key, targetKey, source);
        creationListeners.add(factory);
        InternalFactory<? extends T> scopedFactory
            = Scopes.scope(key, injector, factory, scope);
        createBinding(source, shouldPreload, new LinkedBindingImpl<T>(
            injector, key, source, scopedFactory, scope, targetKey));
        return null;
      }

      public Void visitUntargetted() {
        Type type = key.getTypeLiteral().getType();

        // Error: Missing implementation.
        // Example: bind(Date.class).annotatedWith(Red.class);
        // We can't assume abstract types aren't injectable. They may have an
        // @ImplementedBy annotation or something.
        if (key.hasAnnotationType() || !(type instanceof Class<?>)) {
          addError(source, ErrorMessages.MISSING_IMPLEMENTATION);
          createBinding(source, shouldPreload, invalidBinding(injector, key, source));
          return null;
        }

        // This cast is safe after the preceeding check.
        @SuppressWarnings("unchecked")
        Class<T> clazz = (Class<T>) type;

        BindingImpl<T> binding = injector.createBindingFromType(clazz, scope, source);
        // TODO: Should we clean up the binding left behind in jitBindings?

        if (binding == null) {
          addError(source, ErrorMessages.CANNOT_INJECT_ABSTRACT_TYPE, clazz);
          createBinding(source, shouldPreload, invalidBinding(injector, key, source));
          return null;
        }

        createBinding(source, shouldPreload, binding);
        return null;
      }
    });

    return true;
  }

  private <T> void validateKey(Object source, Key<T> key) {
    if (key.hasAnnotationType()) {
      Class<? extends Annotation> annotationType = key.getAnnotationType();

      if (!Annotations.isRetainedAtRuntime(annotationType)) {
        addError(StackTraceElements.forType(annotationType),
            ErrorMessages.MISSING_RUNTIME_RETENTION, source);
      }

      if (!Key.isBindingAnnotation(annotationType)) {
        addError(StackTraceElements.forType(annotationType),
            ErrorMessages.MISSING_BINDING_ANNOTATION, source);
      }
    }
  }

  <T> InvalidBindingImpl<T> invalidBinding(InjectorImpl injector, Key<T> key, Object source) {
    return new InvalidBindingImpl<T>(injector, key, source);
  }

  @Override public Boolean visitBindConstant(BindConstantCommand command) {
    Object value = command.getTarget().get();
    if (value == null) {
      addError(command.getSource(), ErrorMessages.MISSING_CONSTANT_VALUE);
    }

    validateKey(command.getSource(), command.getKey());
    ConstantFactory<Object> factory = new ConstantFactory<Object>(value);
    putBinding(new ContantBindingImpl<Object>(
        injector, command.getKey(), command.getSource(), factory, value));

    return true;
  }

  private <T> void createBinding(Object source, boolean shouldPreload,
      BindingImpl<T> binding) {
    putBinding(binding);

    // Register to preload if necessary.
    if (binding.getScope() == Scopes.SINGLETON) {
      if (stage == Stage.PRODUCTION || shouldPreload) {
        eagerSingletonCreators.add(new EagerSingletonCreator(binding.key, binding.internalFactory));
      }
    } else {
      if (shouldPreload) {
        addError(source, ErrorMessages.PRELOAD_NOT_ALLOWED);
      }
    }
  }

  public void createEagerSingletons(InjectorImpl injector) {
    for (ContextualCallable<Void> preloader : eagerSingletonCreators) {
      injector.callInContext(preloader);
    }
  }

  public void runCreationListeners(InjectorImpl injector) {
    for (CreationListener creationListener : creationListeners) {
      creationListener.notify(injector);
    }
  }

  private static class EagerSingletonCreator implements ContextualCallable<Void> {
    private final Key<?> key;
    private final InternalFactory<?> factory;

    public EagerSingletonCreator(Key<?> key, InternalFactory<?> factory) {
      this.key = key;
      this.factory = Objects.nonNull(factory, "factory");
    }

    public Void call(InternalContext context) {
      InjectionPoint<?> injectionPoint
          = InjectionPoint.newInstance(key, context.getInjectorImpl());
      context.setInjectionPoint(injectionPoint);
      try {
        factory.get(context, injectionPoint);
        return null;
      } catch(ProvisionException provisionException) {
        provisionException.addContext(injectionPoint);
        throw provisionException;
      } finally {
        context.setInjectionPoint(null);
      }
    }
  }

  private void putBinding(BindingImpl<?> binding) {
    Key<?> key = binding.getKey();
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
    } else {
      bindings.put(key, binding);
    }
  }

  private static Set<Class<?>> FORBIDDEN_TYPES = forbiddenTypes();

  @SuppressWarnings("unchecked") // For generic array creation.
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

  interface CreationListener {
    void notify(InjectorImpl injector);
  }
}
