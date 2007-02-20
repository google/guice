package com.google.inject;

import com.google.inject.BinderImpl.CreationListener;
import com.google.inject.binder.BindingBuilder;
import com.google.inject.util.Objects;
import com.google.inject.util.ToStringBuilder;
import java.lang.annotation.Annotation;
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
      this.key = Key.get(this.key.getType(), annotationType);
    }
    return this;
  }

  public BindingBuilderImpl<T> annotatedWith(Annotation annotation) {
    if (this.key.hasAnnotationType()) {
      binder.addError(source, ErrorMessages.ANNOTATION_ALREADY_SPECIFIED);
    } else {
      // not test-covered?
      this.key = Key.get(this.key.getType(), annotation);
    }
    return this;
  }

  public <I extends T> BindingBuilderImpl<T> to(Class<I> implementation) {
    return to(TypeLiteral.get(implementation));
  }

  public <I extends T> BindingBuilderImpl<T> to(
      final TypeLiteral<I> implementation) {
    ensureImplementationIsNotSet();
    this.implementation = implementation;
    final DefaultFactory<I> defaultFactory
        = new DefaultFactory<I>(key, implementation, source);
    this.factory = defaultFactory;
    binder.creationListeners.add(defaultFactory);
    return this;
  }

  public BindingBuilderImpl<T> to(Factory<? extends T> factory) {
    ensureImplementationIsNotSet();
    this.factory = new InternalFactoryToFactoryAdapter<T>(factory);
    return this;
  }

  public BindingBuilderImpl<T> to(T instance) {
    ensureImplementationIsNotSet();
    this.instance = Objects.nonNull(instance, "instance");
    this.factory = new ConstantFactory<T>(instance);
    if (this.scope != null) {
      binder.addError(source, ErrorMessages.SINGLE_INSTANCE_AND_SCOPE);
    }
    this.scope = Scopes.CONTAINER;
    return this;
  }

  /**
   * Binds to instances from the given factory.
   */
  BindingBuilderImpl<T> to(InternalFactory<? extends T> factory) {
    ensureImplementationIsNotSet();
    this.factory = factory;
    return this;
  }

  public BindingBuilderImpl<T> toFactory(
      final Class<? extends Factory<T>> factoryType) {
    return toFactory(Key.get(factoryType));
  }

  public BindingBuilderImpl<T> toFactory(
      final TypeLiteral<? extends Factory<T>> factoryType) {
    return toFactory(Key.get(factoryType));
  }

  public BindingBuilderImpl<T> toFactory(
      final Key<? extends Factory<T>> factoryKey) {
    ensureImplementationIsNotSet();

    final BoundFactory<T> boundFactory =
        new BoundFactory<T>(factoryKey, source);
    binder.creationListeners.add(boundFactory);
    this.factory = boundFactory;

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

  public BindingBuilderImpl<T> in(Class<? extends Annotation> scopeAnnotation) {
    // this method not test-covered

    ensureScopeNotSet();

    // We could defer this lookup to when we create the container, but this
    // is fine for now.
    this.scope = binder.scopes.get(Objects.nonNull(scopeAnnotation, "scope annotation"));
    if (this.scope == null) {
      binder.addError(source, ErrorMessages.SCOPE_NOT_FOUND,
          "@" + scopeAnnotation.getSimpleName());
    }
    return this;
  }

  public BindingBuilderImpl<T> in(Scope scope) {
    ensureScopeNotSet();
    this.scope = Objects.nonNull(scope, "scope");
    return this;
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

  public BindingBuilderImpl<T> eagerly() {
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

    return Scopes.scope(this.key, container, this.factory, scope);
  }

  boolean isContainerScoped() {
    return this.scope == Scopes.CONTAINER;
  }

  /**
   * Delegates to a custom factory which is also bound in the container.
   */
  private static class BoundFactory<T>
      implements InternalFactory<T>, CreationListener {

    final Key<? extends Factory<? extends T>> factoryKey;
    final Object source;
    private InternalFactory<? extends Factory<? extends T>> factoryFactory;

    public BoundFactory(
        Key<? extends Factory<? extends T>> factoryKey,
        Object source) {
      this.factoryKey = factoryKey;
      this.source = source;
    }

    public void notify(final ContainerImpl container) {
      container.withDefaultSource(source, new Runnable() {
        public void run() {
          factoryFactory = container.getInternalFactory(null, factoryKey);
        }
      });
    }

    public String toString() {
      return factoryKey.toString();
    }

    public T get(InternalContext context) {
      return factoryFactory
          .get(context)
          .get(context.getExternalContext());
    }
  }

  /**
   * Injects new instances of the specified implementation class.
   */
  private static class DefaultFactory<T> implements InternalFactory<T>,
      CreationListener {

    private final TypeLiteral<T> implementation;
    private final Key<? super T> key;
    private final Object source;

    ConstructorInjector<T> constructor;

    DefaultFactory(Key<? super T> key, TypeLiteral<T> implementation,
        Object source) {
      this.key = key;
      this.implementation = implementation;
      this.source = source;
    }

    public void notify(final ContainerImpl container) {
      container.withDefaultSource(source, new Runnable() {
        public void run() {
          constructor = container.getConstructor(implementation);
        }
      });
    }

    public T get(InternalContext context) {
      // TODO: figure out if we can suppress this warning
      return constructor.construct(context, (Class<T>) key.getRawType());
    }

    public String toString() {
      return new ToStringBuilder(Locator.class)
          .add("implementation", implementation)
          .toString();
    }
  }
}
