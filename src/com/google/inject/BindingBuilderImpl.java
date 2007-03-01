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
import com.google.inject.binder.BindingBuilder;
import com.google.inject.binder.BindingScopeBuilder;
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
class BindingBuilderImpl<T> implements BindingBuilder<T> {

  private static final Logger logger
      = Logger.getLogger(BindingBuilderImpl.class.getName());

  final Object source;
  Key<T> key;
  InternalFactory<? extends T> factory;
  TypeLiteral<? extends T> implementation;
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

  public BindingScopeBuilder to(Class<? extends T> implementation) {
    return to(TypeLiteral.get(implementation));
  }

  public BindingScopeBuilder to(TypeLiteral<? extends T> implementation) {
    ensureImplementationIsNotSet();
    this.implementation = implementation;
    final DefaultFactory<? extends T> defaultFactory
        = new DefaultFactory<T>(key, implementation, source);
    this.factory = defaultFactory;
    binder.creationListeners.add(defaultFactory);
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

  public BindingScopeBuilder toProvider(Provider<? extends T> provider) {
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

  public void eagerly() {
    in(Scopes.SINGLETON);
    this.preload = true;
  }

  boolean shouldPreload() {
    return preload;
  }

  InternalFactory<? extends T> getInternalFactory(InjectorImpl injector) {
    // If an implementation wasn't specified, use the injection type.
    if (this.factory == null) {
      to(key.getTypeLiteral());
    }

    // Look for @Scoped on the implementation type.
    if (implementation != null) {
      Scope fromAnnotation = Scopes.getScopeForType(
          implementation.getRawType(), binder.scopes,
          binder.configurationErrorHandler);
      if (fromAnnotation != null) {
        if (this.scope == null) {
          this.scope = fromAnnotation;
        } else {
          logger.info("Overriding scope specified by annotation at "
              + source + ".");
        }
      }
    }

    return Scopes.scope(this.key, injector, this.factory, scope);
  }

  boolean isSingletonScoped() {
    return this.scope == Scopes.SINGLETON;
  }

  /**
   * Delegates to a custom factory which is also bound in the injector.
   */
  private static class BoundProviderFactory<T>
      implements InternalFactory<T>, CreationListener {

    final Key<? extends Provider<? extends T>> providerKey;
    final Object source;
    private InternalFactory<? extends Provider<? extends T>> providerFactory;

    public BoundProviderFactory(
        Key<? extends Provider<? extends T>> providerKey,
        Object source) {
      this.providerKey = providerKey;
      this.source = source;
    }

    public void notify(final InjectorImpl injector) {
      injector.withDefaultSource(source, new Runnable() {
        public void run() {
          providerFactory = injector.getInternalFactory(null, providerKey);
        }
      });
    }

    public String toString() {
      return providerKey.toString();
    }

    public T get(InternalContext context) {
      return providerFactory.get(context).get();
    }
  }

  void registerInstanceForInjection(final Object o) {
    binder.creationListeners.add(new CreationListener() {
      public void notify(InjectorImpl injector) {
        try {
          injector.injectMembers(o);
        }
        catch (Exception e) {
          String className = e.getClass().getSimpleName();
          String message = e.getMessage();
          String logMessage = String.format(
              ErrorMessages.ERROR_INJECTING_MEMBERS, className, o, message);
          logger.log(Level.INFO, logMessage, e);
          binder.addError(source, ErrorMessages.ERROR_INJECTING_MEMBERS_SEE_LOG,
              className, o, message);
        }
      }
    });
  }

  /**
   * Injects new instances of the specified implementation class.
   */
  private static class DefaultFactory<T> implements InternalFactory<T>,
      CreationListener {

    private final TypeLiteral<? extends T> implementation;
    private final Key<T> key;
    private final Object source;

    ConstructorInjector<? extends T> constructor;

    DefaultFactory(Key<T> key, TypeLiteral<? extends T> implementation,
        Object source) {
      this.key = key;
      this.implementation = implementation;
      this.source = source;
    }

    public void notify(final InjectorImpl injector) {
      injector.withDefaultSource(source, new Runnable() {
        public void run() {
          constructor = injector.getConstructor(implementation);
        }
      });
    }

    public T get(InternalContext context) {
      return constructor.construct(context, (Class) key.getRawType());
    }

    public String toString() {
      return new ToStringBuilder(Provider.class)
          .add("implementation", implementation)
          .toString();
    }
  }
}
