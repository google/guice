/*
 * Copyright (C) 2016 Google Inc.
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

package com.google.inject.internal;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.inject.internal.Errors.checkConfiguration;
import static com.google.inject.util.Types.newParameterizedType;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.internal.InternalProviderInstanceBindingImpl.InitializationTiming;
import com.google.inject.multibindings.MultibindingsTargetVisitor;
import com.google.inject.multibindings.OptionalBinderBinding;
import com.google.inject.spi.BindingTargetVisitor;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.Element;
import com.google.inject.spi.ProviderInstanceBinding;
import com.google.inject.spi.ProviderWithExtensionVisitor;
import com.google.inject.util.Types;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.reflect.Type;
import java.util.Set;
import javax.inject.Qualifier;

/**
 * The actual OptionalBinder plays several roles. It implements Module to hide that fact from the
 * public API, and installs the various bindings that are exposed to the user.
 */
public final class RealOptionalBinder<T> implements Module {
  public static <T> RealOptionalBinder<T> newRealOptionalBinder(Binder binder, Key<T> type) {
    binder = binder.skipSources(RealOptionalBinder.class);
    RealOptionalBinder<T> optionalBinder = new RealOptionalBinder<>(binder, type);
    binder.install(optionalBinder);
    return optionalBinder;
  }

  @SuppressWarnings("unchecked")
  static <T> TypeLiteral<Optional<T>> optionalOf(TypeLiteral<T> type) {
    return (TypeLiteral<Optional<T>>)
        TypeLiteral.get(Types.newParameterizedType(Optional.class, type.getType()));
  }

  @SuppressWarnings("unchecked")
  static <T> TypeLiteral<java.util.Optional<T>> javaOptionalOf(TypeLiteral<T> type) {
    return (TypeLiteral<java.util.Optional<T>>)
        TypeLiteral.get(Types.newParameterizedType(java.util.Optional.class, type.getType()));
  }

  @SuppressWarnings("unchecked")
  static <T> TypeLiteral<Optional<javax.inject.Provider<T>>> optionalOfJavaxProvider(
      TypeLiteral<T> type) {
    return (TypeLiteral<Optional<javax.inject.Provider<T>>>)
        TypeLiteral.get(
            Types.newParameterizedType(
                Optional.class, newParameterizedType(javax.inject.Provider.class, type.getType())));
  }

  @SuppressWarnings("unchecked")
  static <T> TypeLiteral<java.util.Optional<javax.inject.Provider<T>>> javaOptionalOfJavaxProvider(
      TypeLiteral<T> type) {
    return (TypeLiteral<java.util.Optional<javax.inject.Provider<T>>>)
        TypeLiteral.get(
            Types.newParameterizedType(
                java.util.Optional.class,
                newParameterizedType(javax.inject.Provider.class, type.getType())));
  }

  @SuppressWarnings("unchecked")
  static <T> TypeLiteral<Optional<Provider<T>>> optionalOfProvider(TypeLiteral<T> type) {
    return (TypeLiteral<Optional<Provider<T>>>)
        TypeLiteral.get(
            Types.newParameterizedType(
                Optional.class, newParameterizedType(Provider.class, type.getType())));
  }

  @SuppressWarnings("unchecked")
  static <T> TypeLiteral<java.util.Optional<Provider<T>>> javaOptionalOfProvider(
      TypeLiteral<T> type) {
    return (TypeLiteral<java.util.Optional<Provider<T>>>)
        TypeLiteral.get(
            Types.newParameterizedType(
                java.util.Optional.class, newParameterizedType(Provider.class, type.getType())));
  }

  @SuppressWarnings("unchecked")
  static <T> Key<Provider<T>> providerOf(Key<T> key) {
    Type providerT = Types.providerOf(key.getTypeLiteral().getType());
    return (Key<Provider<T>>) key.ofType(providerT);
  }

  enum Source {
    DEFAULT,
    ACTUAL
  }

  @Retention(RUNTIME)
  @Qualifier
  @interface Default {
    String value();
  }

  @Retention(RUNTIME)
  @Qualifier
  @interface Actual {
    String value();
  }

  private final BindingSelection<T> bindingSelection;
  private final Binder binder;

  private RealOptionalBinder(Binder binder, Key<T> typeKey) {
    this.bindingSelection = new BindingSelection<>(typeKey);
    this.binder = binder;
  }

  /**
   * Adds a binding for T. Multiple calls to this are safe, and will be collapsed as duplicate
   * bindings.
   */
  private void addDirectTypeBinding(Binder binder) {
    binder
        .bind(bindingSelection.getDirectKey())
        .toProvider(new RealDirectTypeProvider<T>(bindingSelection));
  }

  /**
   * Returns the key to use for the default binding.
   *
   * <p>As a side effect this installs support for the 'direct type', so a binding for {@code T}
   * will be made available.
   */
  Key<T> getKeyForDefaultBinding() {
    bindingSelection.checkNotInitialized();
    addDirectTypeBinding(binder);
    return bindingSelection.getKeyForDefaultBinding();
  }

  public LinkedBindingBuilder<T> setDefault() {
    return binder.bind(getKeyForDefaultBinding());
  }

  /**
   * Returns the key to use for the actual binding, overrides the default if set.
   *
   * <p>As a side effect this installs support for the 'direct type', so a binding for {@code T}
   * will be made available.
   */
  Key<T> getKeyForActualBinding() {
    bindingSelection.checkNotInitialized();
    addDirectTypeBinding(binder);
    return bindingSelection.getKeyForActualBinding();
  }

  public LinkedBindingBuilder<T> setBinding() {
    return binder.bind(getKeyForActualBinding());
  }

  @Override
  public void configure(Binder binder) {
    bindingSelection.checkNotInitialized();
    Key<T> key = bindingSelection.getDirectKey();
    TypeLiteral<T> typeLiteral = key.getTypeLiteral();
    // Every OptionalBinder gets the following types bound
    // * {cgcb,ju}.Optional<Provider<T>>
    // * {cgcb,ju}.Optional<javax.inject.Provider<T>>
    // * {cgcb,ju}.Optional<T>
    // If setDefault() or setBinding() is called then also
    // * T is bound

    // cgcb.Optional<Provider<T>>
    InternalProviderInstanceBindingImpl.Factory<Optional<Provider<T>>> optionalProviderFactory =
        new RealOptionalProviderProvider<>(bindingSelection);
    binder.bind(key.ofType(optionalOfProvider(typeLiteral))).toProvider(optionalProviderFactory);
    // ju.Optional<Provider<T>>
    InternalProviderInstanceBindingImpl.Factory<java.util.Optional<Provider<T>>>
        javaOptionalProviderFactory = new JavaOptionalProviderProvider<>(bindingSelection);
    binder
        .bind(key.ofType(javaOptionalOfProvider(typeLiteral)))
        .toProvider(javaOptionalProviderFactory);

    // Provider is assignable to javax.inject.Provider and the provider that the factory contains
    // cannot be modified so we can use some rawtypes hackery to share the same implementation.

    // cgcb.Optional<ji.Provider<T>>
    @SuppressWarnings("unchecked")
    InternalProviderInstanceBindingImpl.Factory<Optional<javax.inject.Provider<T>>>
        optionalJavaxProviderFactory =
            (InternalProviderInstanceBindingImpl.Factory) optionalProviderFactory;
    binder
        .bind(key.ofType(optionalOfJavaxProvider(typeLiteral)))
        .toProvider(optionalJavaxProviderFactory);
    // ju.Optional<ji.Provider<T>>
    @SuppressWarnings("unchecked")
    InternalProviderInstanceBindingImpl.Factory<java.util.Optional<javax.inject.Provider<T>>>
        javaOptionalJavaxProviderFactory =
            (InternalProviderInstanceBindingImpl.Factory) javaOptionalProviderFactory;
    binder
        .bind(key.ofType(javaOptionalOfJavaxProvider(typeLiteral)))
        .toProvider(javaOptionalJavaxProviderFactory);

    // cgcb.Optional<T>
    Key<Optional<T>> optionalKey = key.ofType(optionalOf(typeLiteral));
    binder
        .bind(optionalKey)
        .toProvider(new RealOptionalKeyProvider<>(bindingSelection, optionalKey));
    // ju.Optional<T>
    Key<java.util.Optional<T>> javaOptionalKey = key.ofType(javaOptionalOf(typeLiteral));
    binder
        .bind(javaOptionalKey)
        .toProvider(new JavaOptionalProvider<>(bindingSelection, javaOptionalKey));
  }

  /** Provides the binding for java.util.Optional<T>. */
  private static final class JavaOptionalProvider<T>
      extends RealOptionalBinderProviderWithDependencies<T, java.util.Optional<T>>
      implements ProviderWithExtensionVisitor<java.util.Optional<T>>,
          OptionalBinderBinding<java.util.Optional<T>> {

    private final Key<java.util.Optional<T>> optionalKey;

    private Dependency<?> targetDependency;
    private InternalFactory<? extends T> target;

    JavaOptionalProvider(
        BindingSelection<T> bindingSelection, Key<java.util.Optional<T>> optionalKey) {
      super(bindingSelection);
      this.optionalKey = optionalKey;
    }

    @Override
    void doInitialize() {
      if (bindingSelection.getBinding() != null) {
        target = bindingSelection.getBinding().getInternalFactory();
        targetDependency = bindingSelection.getDependency();
      }
    }

    @Override
    protected java.util.Optional<T> doProvision(
        InternalContext context, Dependency<?> currentDependency)
        throws InternalProvisionException {
      InternalFactory<? extends T> local = target;
      if (local == null) {
        return java.util.Optional.empty();
      }
      Dependency<?> localDependency = targetDependency;
      T result;
      Dependency previous = context.pushDependency(localDependency, getSource());
      try {
        // See comments in RealOptionalKeyProvider, about how localDependency may be more specific
        // than what we actually need.
        result = local.get(context, localDependency, false);
      } catch (InternalProvisionException ipe) {
        throw ipe.addSource(localDependency);
        } finally {
          context.popStateAndSetDependency(previous);
      }
      return java.util.Optional.ofNullable(result);
    }

    @Override
    public Set<Dependency<?>> getDependencies() {
      return bindingSelection.dependencies;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <B, R> R acceptExtensionVisitor(
        BindingTargetVisitor<B, R> visitor, ProviderInstanceBinding<? extends B> binding) {
      if (visitor instanceof MultibindingsTargetVisitor) {
        return ((MultibindingsTargetVisitor<java.util.Optional<T>, R>) visitor).visit(this);
      } else {
        return visitor.visit(binding);
      }
    }

    @Override
    public boolean containsElement(Element element) {
      return bindingSelection.containsElement(element);
    }

    @Override
    public Binding<?> getActualBinding() {
      return bindingSelection.getActualBinding();
    }

    @Override
    public Binding<?> getDefaultBinding() {
      return bindingSelection.getDefaultBinding();
    }

    @Override
    public Key<java.util.Optional<T>> getKey() {
      return optionalKey;
    }

    @Override
    public Set<Key<?>> getAlternateKeys() {
      Key<?> key = bindingSelection.getDirectKey();
      TypeLiteral<?> typeLiteral = key.getTypeLiteral();
      return ImmutableSet.of(
          (Key<?>) key.ofType(javaOptionalOfProvider(typeLiteral)),
          (Key<?>) key.ofType(javaOptionalOfJavaxProvider(typeLiteral)));
    }
  }

  /** Provides the binding for java.util.Optional<Provider<T>>. */
  private static final class JavaOptionalProviderProvider<T>
      extends RealOptionalBinderProviderWithDependencies<T, java.util.Optional<Provider<T>>> {
    private java.util.Optional<Provider<T>> value;

    JavaOptionalProviderProvider(BindingSelection<T> bindingSelection) {
      super(bindingSelection);
    }

    @Override
    void doInitialize() {
      if (bindingSelection.getBinding() == null) {
        value = java.util.Optional.empty();
      } else {
        value = java.util.Optional.of(bindingSelection.getBinding().getProvider());
      }
    }

    @Override
    protected java.util.Optional<Provider<T>> doProvision(
        InternalContext context, Dependency<?> dependency) {
      return value;
    }

    @Override
    public Set<Dependency<?>> getDependencies() {
      return bindingSelection.providerDependencies();
    }
  }

  /** Provides the binding for T, conditionally installed by calling setBinding/setDefault. */
  private static final class RealDirectTypeProvider<T>
      extends RealOptionalBinderProviderWithDependencies<T, T> {
    private Key<? extends T> targetKey;

    private Object targetSource;

    private InternalFactory<? extends T> targetFactory;

    RealDirectTypeProvider(BindingSelection<T> bindingSelection) {
      super(bindingSelection);
    }

    @Override
    void doInitialize() {
      BindingImpl<T> targetBinding = bindingSelection.getBinding();
      // we only install this factory if they call setBinding()/setDefault() so we know that
      // targetBinding will be non-null.
      this.targetKey = targetBinding.getKey();
      this.targetSource = targetBinding.getSource();
      this.targetFactory = targetBinding.getInternalFactory();
    }

    @Override
    protected T doProvision(InternalContext context, Dependency<?> dependency)
        throws InternalProvisionException {
      // This is what linked bindings do (see FactoryProxy), and we are pretty similar.
      context.pushState(targetKey, targetSource);
      try {
        return targetFactory.get(context, dependency, true);
      } catch (InternalProvisionException ipe) {
        throw ipe.addSource(targetKey);
        } finally {
          context.popState();
      }
    }

    @Override
    public Set<Dependency<?>> getDependencies() {
      return bindingSelection.dependencies;
    }
  }

  /** Provides the binding for Optional<Provider<T>>. */
  private static final class RealOptionalProviderProvider<T>
      extends RealOptionalBinderProviderWithDependencies<T, Optional<Provider<T>>> {
    private Optional<Provider<T>> value;

    RealOptionalProviderProvider(BindingSelection<T> bindingSelection) {
      super(bindingSelection);
    }

    @Override
    void doInitialize() {
      if (bindingSelection.getBinding() == null) {
        value = Optional.absent();
      } else {
        value = Optional.of(bindingSelection.getBinding().getProvider());
      }
    }

    @Override
    protected Optional<Provider<T>> doProvision(InternalContext context, Dependency<?> dependency) {
      return value;
    }

    @Override
    public Set<Dependency<?>> getDependencies() {
      return bindingSelection.providerDependencies();
    }
  }

  /** Provides the binding for Optional<T>. */
  private static final class RealOptionalKeyProvider<T>
      extends RealOptionalBinderProviderWithDependencies<T, Optional<T>>
      implements ProviderWithExtensionVisitor<Optional<T>>, OptionalBinderBinding<Optional<T>> {

    private final Key<Optional<T>> optionalKey;

    // These are assigned to non-null values during initialization if and only if we have a binding
    // to delegate to.
    private Dependency<?> targetDependency;
    private InternalFactory<? extends T> delegate;

    RealOptionalKeyProvider(BindingSelection<T> bindingSelection, Key<Optional<T>> optionalKey) {
      super(bindingSelection);
      this.optionalKey = optionalKey;
    }

    @Override
    void doInitialize() {
      if (bindingSelection.getBinding() != null) {
        delegate = bindingSelection.getBinding().getInternalFactory();
        targetDependency = bindingSelection.getDependency();
      }
    }

    @Override
    protected Optional<T> doProvision(InternalContext context, Dependency<?> currentDependency)
        throws InternalProvisionException {
      InternalFactory<? extends T> local = delegate;
      if (local == null) {
        return Optional.absent();
      }
      Dependency<?> localDependency = targetDependency;
      T result;
      Dependency previous = context.pushDependency(localDependency, getSource());
      try {
        // currentDependency is Optional<? super T>, so we really just need to set the target
        // dependency to ? super T, but we are currently setting it to T.  We could hypothetically
        // make it easier for our delegate to generate proxies by modifying the dependency, but that
        // would also require us to rewrite the key on each call.  So for now we don't do it.
        result = local.get(context, localDependency, false);
      } catch (InternalProvisionException ipe) {
        throw ipe.addSource(localDependency);
        } finally {
          context.popStateAndSetDependency(previous);
      }
      return Optional.fromNullable(result);
    }

    @Override
    public Set<Dependency<?>> getDependencies() {
      return bindingSelection.dependencies();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <B, R> R acceptExtensionVisitor(
        BindingTargetVisitor<B, R> visitor, ProviderInstanceBinding<? extends B> binding) {
      if (visitor instanceof MultibindingsTargetVisitor) {
        return ((MultibindingsTargetVisitor<Optional<T>, R>) visitor).visit(this);
      } else {
        return visitor.visit(binding);
      }
    }

    @Override
    public Key<Optional<T>> getKey() {
      return optionalKey;
    }

    @Override
    public Set<Key<?>> getAlternateKeys() {
      Key<?> key = bindingSelection.getDirectKey();
      TypeLiteral<?> typeLiteral = key.getTypeLiteral();
      return ImmutableSet.of(
          (Key<?>) key.ofType(optionalOfProvider(typeLiteral)),
          (Key<?>) key.ofType(optionalOfJavaxProvider(typeLiteral)));
    }

    @Override
    public Binding<?> getActualBinding() {
      return bindingSelection.getActualBinding();
    }

    @Override
    public Binding<?> getDefaultBinding() {
      return bindingSelection.getDefaultBinding();
    }

    @Override
    public boolean containsElement(Element element) {
      return bindingSelection.containsElement(element);
    }
  }

  /**
   * A helper object that implements the core logic for deciding what the implementation of the
   * binding will be.
   *
   * <p>This also implements the main OptionalBinderBinding logic.
   */
  private static final class BindingSelection<T> {
    private static final ImmutableSet<Dependency<?>> MODULE_DEPENDENCIES =
        ImmutableSet.<Dependency<?>>of(Dependency.get(Key.get(Injector.class)));

    /*@Nullable */ BindingImpl<T> actualBinding;
    /*@Nullable */ BindingImpl<T> defaultBinding;
    /*@Nullable */ BindingImpl<T> binding;
    private boolean initialized;
    private final Key<T> key;

    // Until the injector initializes us, we don't know what our dependencies are,
    // so initialize to the whole Injector (like Multibinder, and MapBinder indirectly).
    private ImmutableSet<Dependency<?>> dependencies = MODULE_DEPENDENCIES;
    private ImmutableSet<Dependency<?>> providerDependencies = MODULE_DEPENDENCIES;

    /** lazily allocated, by {@link #getBindingName}. */
    private String bindingName;

    /** lazily allocated, by {@link #getKeyForDefaultBinding}. */
    private Key<T> defaultBindingKey;

    /** lazily allocated, by {@link #getKeyForActualBinding}. */
    private Key<T> actualBindingKey;

    BindingSelection(Key<T> key) {
      this.key = key;
    }

    void checkNotInitialized() {
      checkConfiguration(!initialized, "already initialized");
    }

    void initialize(InjectorImpl injector, Errors errors) {
      // Every one of our providers will call this method, so only execute the logic once.
      if (initialized) {
        return;
      }
      actualBinding = injector.getExistingBinding(getKeyForActualBinding());
      defaultBinding = injector.getExistingBinding(getKeyForDefaultBinding());
      // We should never create Jit bindings, but we can use them if some other binding created it.
      BindingImpl<T> userBinding = injector.getExistingBinding(key);
      if (actualBinding != null) {
        // TODO(sameb): Consider exposing an option that will allow
        // ACTUAL to fallback to DEFAULT if ACTUAL's provider returns null.
        // Right now, an ACTUAL binding can convert from present -> absent
        // if it's bound to a provider that returns null.
        binding = actualBinding;
      } else if (defaultBinding != null) {
        binding = defaultBinding;
      } else if (userBinding != null) {
        // If neither the actual or default is set, then we fallback
        // to the value bound to the type itself and consider that the
        // "actual binding" for the SPI.
        binding = userBinding;
        actualBinding = userBinding;
      }
      if (binding != null) {
        dependencies = ImmutableSet.<Dependency<?>>of(Dependency.get(binding.getKey()));
        providerDependencies =
            ImmutableSet.<Dependency<?>>of(Dependency.get(providerOf(binding.getKey())));
      } else {
        dependencies = ImmutableSet.of();
        providerDependencies = ImmutableSet.of();
      }
      checkBindingIsNotRecursive(errors);
      initialized = true;
    }

    private void checkBindingIsNotRecursive(Errors errors) {
      if (binding instanceof LinkedBindingImpl) {
        LinkedBindingImpl<T> linkedBindingImpl = (LinkedBindingImpl<T>) binding;
        if (linkedBindingImpl.getLinkedKey().equals(key)) {
          // TODO: b/168656899 check for transitive recursive binding
          errors.recursiveBinding(key, linkedBindingImpl.getLinkedKey());
        }
      }
    }

    Key<T> getKeyForDefaultBinding() {
      if (defaultBindingKey == null) {
        defaultBindingKey = key.withAnnotation(new DefaultImpl(getBindingName()));
      }
      return defaultBindingKey;
    }

    Key<T> getKeyForActualBinding() {
      if (actualBindingKey == null) {
        actualBindingKey = key.withAnnotation(new ActualImpl(getBindingName()));
      }
      return actualBindingKey;
    }

    Key<T> getDirectKey() {
      return key;
    }

    private String getBindingName() {
      // Lazily allocated, most instantiations will never need this because they are deduped during
      // module installation.
      if (bindingName == null) {
        bindingName = Annotations.nameOf(key);
      }
      return bindingName;
    }

    BindingImpl<T> getBinding() {
      return binding;
    }

    // Provide default implementations for most of the OptionalBinderBinding interface
    BindingImpl<T> getDefaultBinding() {
      return defaultBinding;
    }

    BindingImpl<T> getActualBinding() {
      return actualBinding;
    }

    ImmutableSet<Dependency<?>> providerDependencies() {
      return providerDependencies;
    }

    ImmutableSet<Dependency<?>> dependencies() {
      return dependencies;
    }

    /**
     * Returns the Dependency for the target binding, throws NoSuchElementException if no target
     * exists.
     *
     * <p>Calls to this method should typically be guarded by checking if {@link #getBinding()}
     * returns {@code null}.
     */
    Dependency<?> getDependency() {
      return Iterables.getOnlyElement(dependencies);
    }

    /** Implementation of {@link OptionalBinderBinding#containsElement}. */
    boolean containsElement(Element element) {
      // All of our bindings are ProviderInstanceBindings whose providers extend
      // RealOptionalBinderProviderWithDependencies and have 'this' as its binding selection.
      if (element instanceof ProviderInstanceBinding) {
        javax.inject.Provider<?> providerInstance =
            ((ProviderInstanceBinding<?>) element).getUserSuppliedProvider();
        if (providerInstance instanceof RealOptionalBinderProviderWithDependencies) {
          return ((RealOptionalBinderProviderWithDependencies<?, ?>) providerInstance)
              .bindingSelection.equals(this);
        }
      }
      if (element instanceof Binding) {
        Key<?> elementKey = ((Binding) element).getKey();
        // if it isn't one of the things we bound directly it might be an actual or default key
        return elementKey.equals(getKeyForActualBinding())
            || elementKey.equals(getKeyForDefaultBinding());
      }
      return false; // cannot match;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof BindingSelection && ((BindingSelection) o).key.equals(key);
    }

    @Override
    public int hashCode() {
      return key.hashCode();
    }
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof RealOptionalBinder
        && ((RealOptionalBinder<?>) o).bindingSelection.equals(bindingSelection);
  }

  @Override
  public int hashCode() {
    return bindingSelection.hashCode();
  }

  /** A base class for ProviderWithDependencies that need equality based on a specific object. */
  private abstract static class RealOptionalBinderProviderWithDependencies<T, P>
      extends InternalProviderInstanceBindingImpl.Factory<P> {
    protected final BindingSelection<T> bindingSelection;

    RealOptionalBinderProviderWithDependencies(BindingSelection<T> bindingSelection) {
      // We need delayed initialization so we can detect jit bindings created by other bindings
      // while not also creating jit bindings ourselves.  This ensures we only pick up user bindings
      // if the binding would have existed in the injector statically.
      super(InitializationTiming.DELAYED);
      this.bindingSelection = bindingSelection;
    }

    @Override
    final void initialize(InjectorImpl injector, Errors errors) throws ErrorsException {
      bindingSelection.initialize(injector, errors);
      doInitialize();
    }

    /**
     * Initialize the factory. BindingSelection is guaranteed to be initialized at this point and
     * this will be called prior to any provisioning.
     */
    abstract void doInitialize();

    @Override
    public boolean equals(Object obj) {
      return obj != null
          && this.getClass() == obj.getClass()
          && bindingSelection.equals(
              ((RealOptionalBinderProviderWithDependencies<?, ?>) obj).bindingSelection);
    }

    @Override
    public int hashCode() {
      return bindingSelection.hashCode();
    }
  }

  static class DefaultImpl extends BaseAnnotation implements Default {
    public DefaultImpl(String value) {
      super(Default.class, value);
    }
  }

  static class ActualImpl extends BaseAnnotation implements Actual {
    public ActualImpl(String value) {
      super(Actual.class, value);
    }
  }

  abstract static class BaseAnnotation implements Serializable, Annotation {

    private final String value;
    private final Class<? extends Annotation> clazz;

    BaseAnnotation(Class<? extends Annotation> clazz, String value) {
      this.clazz = checkNotNull(clazz, "clazz");
      this.value = checkNotNull(value, "value");
    }

    public String value() {
      return this.value;
    }

    @Override
    public int hashCode() {
      // This is specified in java.lang.Annotation.
      return (127 * "value".hashCode()) ^ value.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      // We check against each annotation type instead of BaseAnnotation
      // so that we can compare against generated annotation implementations.
      if (o instanceof Actual && clazz == Actual.class) {
        Actual other = (Actual) o;
        return value.equals(other.value());
      } else if (o instanceof Default && clazz == Default.class) {
        Default other = (Default) o;
        return value.equals(other.value());
      }
      return false;
    }

    @Override
    public String toString() {
      return "@" + clazz.getName() + (value.isEmpty() ? "" : "(value=" + value + ")");
    }

    @Override
    public Class<? extends Annotation> annotationType() {
      return clazz;
    }

    private static final long serialVersionUID = 0;
  }
}
