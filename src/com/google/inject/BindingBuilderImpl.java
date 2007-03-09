/*
 * Copyright (C) 2007 Google Inc.
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

import com.google.inject.BinderImpl.CreationListener;
import com.google.inject.binder.ScopedBindingBuilder;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.util.Annotations;
import com.google.inject.util.Objects;
import com.google.inject.util.StackTraceElements;
import com.google.inject.util.ToStringBuilder;
import java.lang.annotation.Annotation;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Binds a {@link com.google.inject.Key} to an implementation in a given scope.
 */
class BindingBuilderImpl<T> implements AnnotatedBindingBuilder<T> {

  private static final Logger logger
      = Logger.getLogger(BindingBuilderImpl.class.getName());

  final Object source;
  Key<T> key;
  InternalFactory<? extends T> factory;
  T instance;
  Scope scope;
  boolean preload = false;
  private BinderImpl binder;

  BindingBuilderImpl(BinderImpl binder, Key<T> key, Object source) {
    this.binder = binder;
    this.key = Objects.nonNull(key, "key");
    this.source = source;
  }

  Object getSource() {
    return source;
  }

  Key<T> getKey() {
    return key;
  }

  public BindingBuilderImpl<T> annotatedWith(
      Class<? extends Annotation> annotationType) {
    if (this.key.hasAnnotationType()) {
      binder.addError(source, ErrorMessages.ANNOTATION_ALREADY_SPECIFIED);
    } else {
      boolean retainedAtRuntime =
          Annotations.isRetainedAtRuntime(annotationType);
      boolean bindingAnnotation = Key.isBindingAnnotation(annotationType);

      if (!retainedAtRuntime) {
        binder.addError(StackTraceElements.forType(annotationType),
            ErrorMessages.MISSING_RUNTIME_RETENTION, binder.source());
      }

      if (!bindingAnnotation) {
        binder.addError(StackTraceElements.forType(annotationType),
            ErrorMessages.MISSING_BINDING_ANNOTATION, binder.source());
      }

      if (retainedAtRuntime && bindingAnnotation) {
        this.key = Key.get(this.key.getTypeLiteral(), annotationType);
      }
    }
    return this;
  }

  public BindingBuilderImpl<T> annotatedWith(Annotation annotation) {
    if (this.key.hasAnnotationType()) {
      binder.addError(source, ErrorMessages.ANNOTATION_ALREADY_SPECIFIED);
    } else {
      Class<? extends Annotation> annotationType = annotation.annotationType();

      boolean retainedAtRuntime =
          Annotations.isRetainedAtRuntime(annotationType);
      boolean bindingAnnotation = Key.isBindingAnnotation(annotationType);

      if (!retainedAtRuntime) {
        binder.addError(StackTraceElements.forType(annotationType),
            ErrorMessages.MISSING_RUNTIME_RETENTION, binder.source());
      }

      if (!bindingAnnotation) {
        binder.addError(StackTraceElements.forType(annotationType),
            ErrorMessages.MISSING_BINDING_ANNOTATION, binder.source());
      }

      if (retainedAtRuntime && bindingAnnotation) {
        this.key = Key.get(this.key.getTypeLiteral(), annotation);
      }
    }
    return this;
  }

  public ScopedBindingBuilder to(Class<? extends T> implementation) {
    return to(TypeLiteral.get(implementation));
  }

  public ScopedBindingBuilder to(TypeLiteral<? extends T> implementation) {
    return to(Key.get(implementation));
  }

  public ScopedBindingBuilder to(Key<? extends T> targetKey) {
    ensureImplementationIsNotSet();

    if (key.equals(targetKey)) {
      binder.addError(source, ErrorMessages.RECURSIVE_BINDING);
    }

    final FactoryProxy<? extends T> factoryProxy =
        new FactoryProxy<T>(key, targetKey, source);
    this.factory = factoryProxy;
    binder.creationListeners.add(factoryProxy);
    return this;
  }

  public void toInstance(T instance) {
    ensureImplementationIsNotSet();
    this.instance = Objects.nonNull(instance, "instance");
    this.factory = new ConstantFactory<T>(instance);
    registerInstanceForInjection(instance);
    if (this.scope != null) {
      binder.addError(source, ErrorMessages.SINGLE_INSTANCE_AND_SCOPE);
    }
  }

  /**
   * Binds to instances from the given factory.
   */
  BindingBuilderImpl<T> to(InternalFactory<? extends T> factory) {
    ensureImplementationIsNotSet();
    this.factory = factory;
    return this;
  }

  public ScopedBindingBuilder toProvider(Provider<? extends T> provider) {
    ensureImplementationIsNotSet();
    this.factory = new InternalFactoryToProviderAdapter<T>(provider, source);
    registerInstanceForInjection(provider);
    return this;
  }

  public BindingBuilderImpl<T> toProvider(
      Class<? extends Provider<? extends T>> providerType) {
    return toProvider(Key.get(providerType));
  }

  public BindingBuilderImpl<T> toProvider(
      Key<? extends Provider<? extends T>> providerKey) {
    ensureImplementationIsNotSet();

    final BoundProviderFactory<T> boundProviderFactory =
        new BoundProviderFactory<T>(providerKey, source);
    binder.creationListeners.add(boundProviderFactory);
    this.factory = boundProviderFactory;

    return this;
  }

  /**
   * Adds an error message if the implementation has already been bound.
   */
  private void ensureImplementationIsNotSet() {
    if (factory != null) {
      binder.addError(source, ErrorMessages.IMPLEMENTATION_ALREADY_SET);
    }
  }

  public void in(Class<? extends Annotation> scopeAnnotation) {
    // this method not test-covered
    ensureScopeNotSet();

    // We could defer this lookup to when we create the Injector, but this
    // is fine for now.
    this.scope = binder.scopes.get(
        Objects.nonNull(scopeAnnotation, "scope annotation"));
    if (this.scope == null) {
      binder.addError(source, ErrorMessages.SCOPE_NOT_FOUND,
          "@" + scopeAnnotation.getSimpleName());
    }
  }

  public void in(Scope scope) {
    ensureScopeNotSet();
    this.scope = Objects.nonNull(scope, "scope");
  }

  private void ensureScopeNotSet() {
    // Scoping isn't allowed when we have only one instance.
    if (this.instance != null) {
      binder.addError(source, ErrorMessages.SINGLE_INSTANCE_AND_SCOPE);
      return;
    }

    if (this.scope != null) {
      binder.addError(source, ErrorMessages.SCOPE_ALREADY_SET);
    }
  }

  public void asEagerSingleton() {
    in(Scopes.SINGLETON);
    this.preload = true;
  }

  boolean shouldPreload() {
    return preload;
  }

  InternalFactory<? extends T> getInternalFactory(InjectorImpl injector) {
    if (this.factory == null && !key.hasAnnotationType()) {
      // Try an implicit binding.
      final ImplicitImplementation<T> implicitImplementation =
          new ImplicitImplementation<T>(key, scope, source);
      binder.creationListeners.add(implicitImplementation);

      // We need to record the scope. If it's singleton, we'll preload in prod.
      if (this.scope == null) {
        // We can ignore errors because the error will already have been
        // recorded.
        this.scope = Scopes.getScopeForType(
            key.getTypeLiteral().getRawType(), binder.scopes, IGNORE_ERRORS);
      }

      return implicitImplementation;
    }

    return Scopes.scope(this.key, injector, this.factory, scope);
  }

  boolean isSingletonScoped() {
    return this.scope == Scopes.SINGLETON;
  }

  void registerInstanceForInjection(final Object o) {
    binder.instanceInjectors.add(new CreationListener() {
      public void notify(InjectorImpl injector) {
        try {
          injector.injectMembers(o);
        }
        catch (Exception e) {
          String className = e.getClass().getSimpleName();
          String message = ErrorMessages.getRootMessage(e);
          String logMessage = String.format(
              ErrorMessages.ERROR_INJECTING_MEMBERS, o, message);
          logger.log(Level.INFO, logMessage, e);
          binder.addError(source, ErrorMessages.ERROR_INJECTING_MEMBERS_SEE_LOG,
              className, o, message);
        }
      }
    });
  }

  /**
   * A placeholder which enables us to swap in the real factory once the
   * container is created.
   */
  private static class FactoryProxy<T> implements InternalFactory<T>,
      CreationListener {

    private final Key<T> key;
    private final Key<? extends T> targetKey;
    private final Object source;

    InternalFactory<? extends T> targetFactory;

    FactoryProxy(Key<T> key, Key<? extends T> targetKey, Object source) {
      this.key = key;
      this.targetKey = targetKey;
      this.source = source;
    }

    public void notify(final InjectorImpl injector) {
      injector.withDefaultSource(source, new Runnable() {
        public void run() {
          targetFactory = injector.getInternalFactory(null, targetKey);
        }
      });
    }

    public T get(InternalContext context) {
      return targetFactory.get(context);
    }

    public String toString() {
      return new ToStringBuilder(FactoryProxy.class)
          .add("key", key)
          .add("provider", targetFactory)
          .toString();
    }
  }

  private static class ImplicitImplementation<T> implements InternalFactory<T>,
      CreationListener {

    private final Key<T> key;
    private final Object source;
    private final Scope scope;
    InternalFactory<? extends T> implicitFactory;

    ImplicitImplementation(Key<T> key, Scope scope, Object source) {
      this.key = key;
      this.scope = scope;
      this.source = source;
    }

    public void notify(final InjectorImpl injector) {
      injector.withDefaultSource(source, new Runnable() {
        public void run() {
          implicitFactory = injector.getImplicitBinding(null,
              (Class) key.getTypeLiteral().getRawType(), scope);
        }
      });
    }

    public T get(InternalContext context) {
      return implicitFactory.get(context);
    }

    public String toString() {
      return new ToStringBuilder(FactoryProxy.class)
          .add("key", key)
          .add("provider", implicitFactory)
          .toString();
    }
  }

  static ErrorHandler IGNORE_ERRORS = new ErrorHandler() {
    public void handle(Object source, String message) {}
    public void handle(Object source, String message, Object... arguments) {}
  };
}
