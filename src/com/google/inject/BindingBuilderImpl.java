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
import com.google.inject.BinderImpl.MembersInjector;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.ScopedBindingBuilder;
import com.google.inject.internal.Annotations;
import com.google.inject.internal.Objects;
import com.google.inject.internal.StackTraceElements;
import com.google.inject.internal.ToStringBuilder;
import com.google.inject.internal.Classes;
import com.google.inject.spi.BindingVisitor;
import com.google.inject.spi.InstanceBinding;
import com.google.inject.spi.LinkedBinding;
import com.google.inject.spi.ProviderInstanceBinding;
import com.google.inject.spi.ProviderBinding;
import com.google.inject.spi.ClassBinding;
import java.lang.annotation.Annotation;
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
  Scope scope;
  boolean preload = false;
  private BinderImpl binder;

  // These fields keep track of the raw implementation for later use in the
  // Binding API.
  T instance;
  Key<? extends T> targetKey;
  Provider<? extends T> providerInstance;
  Key<? extends Provider<? extends T>> providerKey;

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
    this.targetKey = targetKey;
    return this;
  }

  public void toInstance(T instance) {
    ensureImplementationIsNotSet();
    this.instance = instance;
    this.factory = new ConstantFactory<T>(instance, source);
    registerInstanceForInjection(instance);

    // TODO: I don't think this can happen anymore.
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
    this.providerInstance = provider;
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
    this.providerKey = providerKey;

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

  BindingImpl<T> build(InjectorImpl injector) {
    if (this.factory != null) {
      Scope scope = this.scope == null ? Scopes.NO_SCOPE : this.scope;

      InternalFactory<? extends T> scopedFactory
          = Scopes.scope(this.key, injector, this.factory, scope);

      // Instance binding.
      if (instance != null) {
        return new InstanceBindingImpl<T>(
            injector, key, source, scopedFactory, instance);
      }

      // Linked binding.
      if (this.targetKey != null) {
        return new LinkedBindingImpl<T>(
            injector, key, source, scopedFactory, scope, targetKey);
      }

      // Provider instance binding.
      if (this.providerInstance != null) {
        return new ProviderInstanceBindingImpl<T>(
            injector, key, source, scopedFactory, scope, providerInstance);
      }

      // Provider binding.
      if (this.providerKey != null) {
        return new ProviderBindingImpl<T>(
            injector, key, source, scopedFactory, scope, providerKey);
      }

      // Unknown binding type. We just have a raw internal factory.
      // This happens with the Injector binding, for example.
      return new UnknownBindingImpl<T>(injector, key, source, scopedFactory);

    } else {
      // If we're here, the type we bound to is also the implementation.
      // Example: bind(FooImpl.class).in(Scopes.SINGLETON);

      Class<?> rawType = key.getRawType();

      // Error: Inner class.
      if (Classes.isInnerClass(rawType)) {
        injector.errorHandler.handle(source,
            ErrorMessages.CANNOT_INJECT_INNER_CLASS, rawType);
        return invalidBinding(injector);
      }

      // Error: Missing implementation.
      // Example: bind(Runnable.class);
      // Example: bind(Date.class).annotatedWith(Red.class);
      if (key.hasAnnotationType() || !Classes.isConcrete(rawType)) {
        injector.errorHandler.handle(source,
            ErrorMessages.MISSING_IMPLEMENTATION);
        return invalidBinding(injector);
      }

      final ImplicitImplementation<T> implicitImplementation =
          new ImplicitImplementation<T>(key, scope, source);
      binder.creationListeners.add(implicitImplementation);

      // We need to record the scope. If it's singleton, we'll preload in prod.
      if (this.scope == null) {
        // We can ignore errors because the error will already have been
        // recorded.
        this.scope = Scopes.getScopeForType(
            key.getTypeLiteral().getRawType(), binder.scopes, IGNORE_ERRORS);

        if (this.scope == null) {
          this.scope = Scopes.NO_SCOPE;
        }
      }

      return new ClassBindingImpl<T>(injector, key, source,
          implicitImplementation, scope);
    }
  }

  InvalidBinding<T> invalidBinding(InjectorImpl injector) {
    return new InvalidBinding<T>(injector, key, source);
  }

  private static class InvalidBinding<T> extends BindingImpl<T> {

    InvalidBinding(InjectorImpl injector, Key<T> key, Object source) {
      super(injector, key, source, new InternalFactory<T>() {
        public T get(InternalContext context) {
          throw new AssertionError();
        }
      }, Scopes.NO_SCOPE);
    }

    public void accept(BindingVisitor<? super T> bindingVisitor) {
      throw new AssertionError();
    }

    public String toString() {
      return "InvalidBinding";
    }
  }

  private static class ClassBindingImpl<T> extends BindingImpl<T>
      implements ClassBinding<T> {

    ClassBindingImpl(InjectorImpl injector, Key<T> key, Object source,
        InternalFactory<? extends T> internalFactory, Scope scope) {
      super(injector, key, source, internalFactory, scope);
    }

    public void accept(BindingVisitor<? super T> visitor) {
      visitor.visit(this);
    }

    @SuppressWarnings("unchecked")
    public Class<T> getBoundClass() {
      // T should always be the class itself.
      return (Class<T>) key.getRawType();
    }

    @Override
    public String toString() {
      return new ToStringBuilder(ClassBinding.class)
          .add("class", getBoundClass())
          .add("scope", scope)
          .add("source", source)
          .toString();
    }
  }

  private static class UnknownBindingImpl<T> extends BindingImpl<T> {

    UnknownBindingImpl(InjectorImpl injector, Key<T> key, Object source,
        InternalFactory<? extends T> internalFactory) {
      super(injector, key, source, internalFactory, Scopes.NO_SCOPE);
    }

    public void accept(BindingVisitor<? super T> bindingVisitor) {
      bindingVisitor.visitUnknown(this);
    }
  }

  private static class InstanceBindingImpl<T> extends BindingImpl<T>
      implements InstanceBinding<T> {

    final T instance;

    InstanceBindingImpl(InjectorImpl injector, Key<T> key, Object source,
        InternalFactory<? extends T> internalFactory, T instance) {
      super(injector, key, source, internalFactory, Scopes.NO_SCOPE);
      this.instance = instance;
    }

    public void accept(BindingVisitor<? super T> bindingVisitor) {
      bindingVisitor.visit(this);
    }

    public T getInstance() {
      return this.instance;
    }

    @Override
    public String toString() {
      return new ToStringBuilder(InstanceBinding.class)
          .add("key", key)
          .add("instance", instance)
          .add("source", source)
          .toString();
    }
  }

  private static class LinkedBindingImpl<T> extends BindingImpl<T>
      implements LinkedBinding<T> {

    final Key<? extends T> targetKey;

    LinkedBindingImpl(InjectorImpl injector, Key<T> key, Object source,
        InternalFactory<? extends T> internalFactory, Scope scope,
        Key<? extends T> targetKey) {
      super(injector, key, source, internalFactory, scope);
      this.targetKey = targetKey;
    }

    public void accept(BindingVisitor<? super T> bindingVisitor) {
      bindingVisitor.visit(this);
    }

    public Binding<? extends T> getTarget() {
      return injector.getBinding(targetKey);
    }

    @Override
    public String toString() {
      return new ToStringBuilder(LinkedBinding.class)
          .add("key", key)
          .add("target", targetKey)
          .add("scope", scope)
          .add("source", source)
          .toString();
    }
  }

  private static class ProviderInstanceBindingImpl<T> extends BindingImpl<T>
      implements ProviderInstanceBinding<T> {

    final Provider<? extends T> providerInstance;

    ProviderInstanceBindingImpl(InjectorImpl injector, Key<T> key,
        Object source,
        InternalFactory<? extends T> internalFactory, Scope scope,
        Provider<? extends T> providerInstance) {
      super(injector, key, source, internalFactory, scope);
      this.providerInstance = providerInstance;
    }

    public void accept(BindingVisitor<? super T> bindingVisitor) {
      bindingVisitor.visit(this);
    }

    public Provider<? extends T> getProviderInstance() {
      return this.providerInstance;
    }

    @Override
    public String toString() {
      return new ToStringBuilder(ProviderInstanceBinding.class)
          .add("key", key)
          .add("provider", providerInstance)
          .add("scope", scope)
          .add("source", source)
          .toString();
    }
  }

  private static class ProviderBindingImpl<T> extends BindingImpl<T>
      implements ProviderBinding<T> {

    final Key<? extends Provider<? extends T>> providerKey;

    ProviderBindingImpl(InjectorImpl injector, Key<T> key, Object source,
        InternalFactory<? extends T> internalFactory, Scope scope,
        Key<? extends Provider<? extends T>> providerKey) {
      super(injector, key, source, internalFactory, scope);
      this.providerKey = providerKey;
    }

    public void accept(BindingVisitor<? super T> bindingVisitor) {
      bindingVisitor.visit(this);
    }

    public Binding<? extends Provider<? extends T>> getProviderBinding() {
      return injector.getBinding(providerKey);
    }

    @Override
    public String toString() {
      return new ToStringBuilder(ProviderBinding.class)
          .add("key", key)
          .add("provider", providerKey)
          .add("scope", scope)
          .add("source", source)
          .toString();
    }
  }

  boolean isSingletonScoped() {
    return this.scope == Scopes.SINGLETON;
  }

  void registerInstanceForInjection(final Object o) {
    binder.membersInjectors.add(new MembersInjector(o));
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
