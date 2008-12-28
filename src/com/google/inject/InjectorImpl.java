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

import com.google.common.base.Nullable;
import static com.google.common.base.Preconditions.checkState;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.inject.internal.Annotations;
import static com.google.inject.internal.Annotations.findScopeAnnotation;
import com.google.inject.internal.BindingImpl;
import com.google.inject.internal.Classes;
import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;
import com.google.inject.internal.FailableCache;
import com.google.inject.internal.InstanceBindingImpl;
import com.google.inject.internal.InternalContext;
import com.google.inject.internal.InternalFactory;
import com.google.inject.internal.LinkedBindingImpl;
import com.google.inject.internal.LinkedProviderBindingImpl;
import com.google.inject.internal.MatcherAndConverter;
import com.google.inject.internal.Scoping;
import com.google.inject.internal.SourceProvider;
import com.google.inject.internal.ToStringBuilder;
import com.google.inject.spi.BindingTargetVisitor;
import com.google.inject.spi.ConvertedConstantBinding;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.ProviderBinding;
import com.google.inject.util.Providers;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default {@link Injector} implementation.
 *
 * @author crazybob@google.com (Bob Lee)
 * @see InjectorBuilder
 */
class InjectorImpl implements Injector {
  final State state;
  final InjectorImpl parent;
  final BindingsMultimap bindingsMultimap = new BindingsMultimap();
  final Initializer initializer;
  ConstructionProxyFactory constructionProxyFactory;

  /** Just-in-time binding cache. Guarded by state.lock() */
  final Map<Key<?>, BindingImpl<?>> jitBindings = Maps.newHashMap();

  InjectorImpl(@Nullable InjectorImpl parent, State state, Initializer initializer) {
    this.parent = parent;
    this.state = state;
    this.initializer = initializer;

    if (parent != null) {
      localContext = parent.localContext;
    } else {
      localContext = new ThreadLocal<InternalContext[]>() {
        protected InternalContext[] initialValue() {
          return new InternalContext[1];
        }
      };
    }
  }

  /** Indexes bindings by type. */
  void index() {
    for (Binding<?> binding : state.getExplicitBindingsThisLevel().values()) {
      index(binding);
    }
  }

  <T> void index(Binding<T> binding) {
    bindingsMultimap.put(binding.getKey().getTypeLiteral(), binding);
  }

  public <T> List<Binding<T>> findBindingsByType(TypeLiteral<T> type) {
    return bindingsMultimap.getAll(type);
  }

  /** Returns the binding for {@code key} */
  public <T> BindingImpl<T> getBinding(Key<T> key) {
    Errors errors = new Errors(key);
    try {
      BindingImpl<T> result = getBindingOrThrow(key, errors);
      errors.throwConfigurationExceptionIfErrorsExist();
      return result;
    } catch (ErrorsException e) {
      throw new ConfigurationException(errors.merge(e.getErrors()).getMessages());
    }
  }

  /**
   * Gets a binding implementation.  First, it check to see if the parent has a binding.  If the
   * parent has a binding and the binding is scoped, it will use that binding.  Otherwise, this
   * checks for an explicit binding. If no explicit binding is found, it looks for a just-in-time
   * binding.
   */
  public <T> BindingImpl<T> getBindingOrThrow(Key<T> key, Errors errors)
      throws ErrorsException {
    // Check explicit bindings, i.e. bindings created by modules.
    BindingImpl<T> binding = state.getExplicitBinding(key);
    if (binding != null) {
      return binding;
    }

    // Look for an on-demand binding.
    return getJustInTimeBinding(key, errors);
  }

  public <T> Binding<T> getBinding(Class<T> type) {
    return getBinding(Key.get(type));
  }

  public Injector getParent() {
    return parent;
  }

  public Injector createChildInjector(Iterable<? extends Module> modules) {
    return new InjectorBuilder()
        .parentInjector(this)
        .addModules(modules)
        .build();
  }

  public Injector createChildInjector(Module... modules) {
    return createChildInjector(ImmutableList.of(modules));
  }

  /**
   * Returns a just-in-time binding for {@code key}, creating it if necessary.
   *
   * @throws ErrorsException if the binding could not be created.
   */
  private <T> BindingImpl<T> getJustInTimeBinding(Key<T> key, Errors errors)
      throws ErrorsException {
    synchronized (state.lock()) {
      // first try to find a JIT binding that we've already created
      for (InjectorImpl injector = this; injector != null; injector = injector.parent) {
        @SuppressWarnings("unchecked") // we only store bindings that match their key
        BindingImpl<T> binding = (BindingImpl<T>) injector.jitBindings.get(key);

        if (binding != null) {
          return binding;
        }
      }

      return createJustInTimeBindingRecursive(key, errors);
    }
  }

  /* Returns true if the key type is Provider<?> (but not a subclass of Provider<?>). */
  static boolean isProvider(Key<?> key) {
    return key.getTypeLiteral().getRawType().equals(Provider.class);
  }

  /** Creates a synthetic binding to Provider<T>, i.e. a binding to the provider from Binding<T>. */
  private <T> BindingImpl<Provider<T>> createProviderBinding(Key<Provider<T>> key, Errors errors)
      throws ErrorsException {
    Type providerType = key.getTypeLiteral().getType();

    // If the Provider has no type parameter (raw Provider)...
    if (!(providerType instanceof ParameterizedType)) {
      throw errors.cannotInjectRawProvider().toException();
    }

    Type entryType = ((ParameterizedType) providerType).getActualTypeArguments()[0];

    @SuppressWarnings("unchecked") // safe because T came from Key<Provider<T>>
    Key<T> providedKey = (Key<T>) key.ofType(entryType);

    BindingImpl<T> delegate = getBindingOrThrow(providedKey, errors);
    return new ProviderBindingImpl<T>(this, key, delegate);
  }

  static class ProviderBindingImpl<T> extends BindingImpl<Provider<T>>
      implements ProviderBinding<Provider<T>> {
    final BindingImpl<T> providedBinding;

    ProviderBindingImpl(InjectorImpl injector, Key<Provider<T>> key, Binding<T> providedBinding) {
      super(injector, key, providedBinding.getSource(), createInternalFactory(providedBinding),
          Scoping.UNSCOPED);
      this.providedBinding = (BindingImpl<T>) providedBinding;
    }

    static <T> InternalFactory<Provider<T>> createInternalFactory(Binding<T> providedBinding) {
      final Provider<T> provider = providedBinding.getProvider();
      return new InternalFactory<Provider<T>>() {
        public Provider<T> get(Errors errors, InternalContext context, Dependency dependency) {
          return provider;
        }
      };
    }

    public Key<? extends T> getProvidedKey() {
      return providedBinding.getKey();
    }

    public <V> V acceptTargetVisitor(BindingTargetVisitor<? super Provider<T>, V> visitor) {
      return visitor.visitProviderBinding(this);
    }
  }

  /**
   * Converts a constant string binding to the required type.
   *
   * @return the binding if it could be resolved, or null if the binding doesn't exist
   * @throws com.google.inject.internal.ErrorsException if there was an error resolving the binding
   */
  private <T> BindingImpl<T> convertConstantStringBinding(Key<T> key, Errors errors)
      throws ErrorsException {
    // Find a constant string binding.
    Key<String> stringKey = key.ofType(String.class);
    BindingImpl<String> stringBinding = state.getExplicitBinding(stringKey);
    if (stringBinding == null || !stringBinding.isConstant()) {
      return null;
    }

    String stringValue = stringBinding.getProvider().get();
    Object source = stringBinding.getSource();

    // Find a matching type converter.
    TypeLiteral<T> type = key.getTypeLiteral();
    MatcherAndConverter matchingConverter = state.getConverter(stringValue, type, errors, source);

    if (matchingConverter == null) {
      // No converter can handle the given type.
      return null;
    }

    // Try to convert the string. A failed conversion results in an error.
    try {
      @SuppressWarnings("unchecked") // This cast is safe because we double check below.
      T converted = (T) matchingConverter.getTypeConverter().convert(stringValue, type);

      if (converted == null) {
        throw errors.converterReturnedNull(stringValue, source, type, matchingConverter)
            .toException();
      }

      if (!type.getRawType().isInstance(converted)) {
        throw errors.conversionTypeError(stringValue, source, type, matchingConverter, converted)
            .toException();
      }

      return new ConvertedConstantBindingImpl<T>(this, key, converted, stringBinding);
    } catch (ErrorsException e) {
      throw e;
    } catch (Exception e) {
      throw errors.conversionError(stringValue, source, type, matchingConverter, e)
          .toException();
    }
  }

  private static class ConvertedConstantBindingImpl<T>
      extends BindingImpl<T> implements ConvertedConstantBinding<T> {
    final T value;
    final Provider<T> provider;
    final Binding<String> originalBinding;

    ConvertedConstantBindingImpl(
        Injector injector, Key<T> key, T value, Binding<String> originalBinding) {
      super(injector, key, originalBinding.getSource(),
          new ConstantFactory<T>(Initializables.of(value)), Scoping.UNSCOPED);
      this.value = value;
      provider = Providers.of(value);
      this.originalBinding = originalBinding;
    }

    @Override public Provider<T> getProvider() {
      return provider;
    }

    public <V> V acceptTargetVisitor(BindingTargetVisitor<? super T, V> visitor) {
      return visitor.visitConvertedConstant(this);
    }

    public T getValue() {
      return value;
    }

    public Key<String> getSourceKey() {
      return originalBinding.getKey();
    }

    public Set<Dependency<?>> getDependencies() {
      return ImmutableSet.<Dependency<?>>of(Dependency.get(getSourceKey()));
    }

    @Override public String toString() {
      return new ToStringBuilder(ConvertedConstantBinding.class)
          .add("key", getKey())
          .add("value", value)
          .add("sourceKey", getSourceKey())
          .toString();
    }
  }

  <T> void initializeBinding(BindingImpl<T> binding, Errors errors) throws ErrorsException {
    // Put the partially constructed binding in the map a little early. This enables us to handle
    // circular dependencies. Example: FooImpl -> BarImpl -> FooImpl.
    // Note: We don't need to synchronize on state.lock() during injector creation.
    if (binding instanceof ClassBindingImpl<?>) {
      Key<T> key = binding.getKey();
      jitBindings.put(key, binding);
      boolean successful = false;
      try {
        // TODO: does this put the binding in JIT bindings?
        binding.initialize(this, errors);
        successful = true;
      } finally {
        if (!successful) {
          jitBindings.remove(key);
        }
      }
    }
  }

  /**
   * Creates a binding for an injectable type with the given scope. Looks for a scope on the type if
   * none is specified.
   */
  <T> BindingImpl<T> createUnitializedBinding(Key<T> key, Scoping scoping, Object source,
      Errors errors) throws ErrorsException {
    Class<?> rawType = key.getTypeLiteral().getRawType();

    // Don't try to inject arrays, or enums.
    if (rawType.isArray() || rawType.isEnum()) {
      throw errors.missingImplementation(key).toException();
    }

    // Handle TypeLiteral<T> by binding the inner type
    if (rawType == TypeLiteral.class) {
      @SuppressWarnings("unchecked") // we have to fudge the inner type as Object
      BindingImpl<T> binding = (BindingImpl<T>) createTypeLiteralBinding(
          (Key<TypeLiteral<Object>>) key, errors);
      return binding;
    }

    // Handle @ImplementedBy
    ImplementedBy implementedBy = rawType.getAnnotation(ImplementedBy.class);
    if (implementedBy != null) {
      Annotations.checkForMisplacedScopeAnnotations(rawType, source, errors);
      return createImplementedByBinding(key, scoping, implementedBy, errors);
    }

    // Handle @ProvidedBy.
    ProvidedBy providedBy = rawType.getAnnotation(ProvidedBy.class);
    if (providedBy != null) {
      Annotations.checkForMisplacedScopeAnnotations(rawType, source, errors);
      return createProvidedByBinding(key, scoping, providedBy, errors);
    }

    // We can't inject abstract classes.
    // TODO: Method interceptors could actually enable us to implement
    // abstract types. Should we remove this restriction?
    if (Modifier.isAbstract(rawType.getModifiers())) {
      throw errors.missingImplementation(key).toException();
    }

    // Error: Inner class.
    if (Classes.isInnerClass(rawType)) {
      throw errors.cannotInjectInnerClass(rawType).toException();
    }

    LateBoundConstructor<T> lateBoundConstructor = new LateBoundConstructor<T>();

    if (!scoping.isExplicitlyScoped()) {
      Class<? extends Annotation> scopeAnnotation = findScopeAnnotation(errors, rawType);
      if (scopeAnnotation != null) {
        scoping = Scopes.makeInjectable(Scoping.forAnnotation(scopeAnnotation),
            this, errors.withSource(rawType));
      }
    }

    InternalFactory<? extends T> scopedFactory
        = Scopes.scope(key, this, lateBoundConstructor, scoping);
    return new ClassBindingImpl<T>(
        this, key, source, scopedFactory, scoping, lateBoundConstructor);
  }

  /**
   * Converts a binding for a {@code Key<TypeLiteral<T>>} to the value {@code TypeLiteral<T>}. It's
   * a bit awkward because we have to pull out the inner type in the type literal.
   */
  private <T> BindingImpl<TypeLiteral<T>> createTypeLiteralBinding(
      Key<TypeLiteral<T>> key, Errors errors) throws ErrorsException {
    Type typeLiteralType = key.getTypeLiteral().getType();
    if (!(typeLiteralType instanceof ParameterizedType)) {
      throw errors.cannotInjectRawTypeLiteral().toException();
    }

    ParameterizedType parameterizedType = (ParameterizedType) typeLiteralType;
    Type innerType = parameterizedType.getActualTypeArguments()[0];

    // this is unforunate. We don't support building TypeLiterals for type variable like 'T'. If
    // this proves problematic, we can probably fix TypeLiteral to support type variables
    if (!(innerType instanceof Class)
        && !(innerType instanceof GenericArrayType)
        && !(innerType instanceof ParameterizedType)) {
      throw errors.cannotInjectTypeLiteralOf(innerType).toException();
    }

    @SuppressWarnings("unchecked") // by definition, innerType == T, so this is safe
    TypeLiteral<T> value = (TypeLiteral<T>) TypeLiteral.get(innerType);
    InternalFactory<TypeLiteral<T>> factory = new ConstantFactory<TypeLiteral<T>>(
        Initializables.of(value));
    return new InstanceBindingImpl<TypeLiteral<T>>(this, key, SourceProvider.UNKNOWN_SOURCE,
        factory, ImmutableSet.<InjectionPoint>of(), value);
  }

  static class LateBoundConstructor<T> implements InternalFactory<T> {
    ConstructorInjector<T> constructorInjector;

    @SuppressWarnings("unchecked") // the constructor T is the same as the implementation T
    void bind(Injector injector, TypeLiteral<T> implementation, Errors errors)
        throws ErrorsException {
      InjectorImpl injectorImpl = (InjectorImpl) injector;
      constructorInjector
          = (ConstructorInjector<T>) injectorImpl.constructors.get(implementation, errors);
    }

    public Constructor<T> getConstructor() {
      checkState(constructorInjector != null, "Constructor is not ready");
      return constructorInjector.constructionProxy.getConstructor();
    }

    @SuppressWarnings("unchecked")
    public T get(Errors errors, InternalContext context, Dependency<?> dependency)
        throws ErrorsException {
      checkState(constructorInjector != null, "Construct before bind, " + constructorInjector);

      // This may not actually be safe because it could return a super type of T (if that's all the
      // client needs), but it should be OK in practice thanks to the wonders of erasure.
      return (T) constructorInjector.construct(
          errors, context, dependency.getKey().getRawType());
    }
  }

  /** Creates a binding for a type annotated with @ProvidedBy. */
  <T> BindingImpl<T> createProvidedByBinding(Key<T> key, Scoping scoping,
      ProvidedBy providedBy, Errors errors) throws ErrorsException {
    final Class<?> rawType = key.getTypeLiteral().getRawType();
    final Class<? extends Provider<?>> providerType = providedBy.value();

    // Make sure it's not the same type. TODO: Can we check for deeper loops?
    if (providerType == rawType) {
      throw errors.recursiveProviderType().toException();
    }

    // Assume the provider provides an appropriate type. We double check at runtime.
    @SuppressWarnings("unchecked")
    final Key<? extends Provider<T>> providerKey
        = (Key<? extends Provider<T>>) Key.get(providerType);
    final BindingImpl<? extends Provider<?>> providerBinding
        = getBindingOrThrow(providerKey, errors);

    InternalFactory<T> internalFactory = new InternalFactory<T>() {
      public T get(Errors errors, InternalContext context, Dependency dependency)
          throws ErrorsException {
        errors = errors.withSource(providerKey);
        Provider<?> provider = providerBinding.getInternalFactory().get(
            errors, context, dependency);
        try {
          Object o = provider.get();
          if (o != null && !rawType.isInstance(o)) {
            throw errors.subtypeNotProvided(providerType, rawType).toException();
          }
          @SuppressWarnings("unchecked") // protected by isInstance() check above
          T t = (T) o;
          return t;
        } catch (RuntimeException e) {
          throw errors.errorInProvider(e).toException();
        }
      }
    };

    return new LinkedProviderBindingImpl<T>(
        this,
        key,
        rawType /* source */,
        Scopes.<T>scope(key, this, internalFactory, scoping),
        scoping,
        providerKey);
  }

  /** Creates a binding for a type annotated with @ImplementedBy. */
  <T> BindingImpl<T> createImplementedByBinding(Key<T> key, Scoping scoping,
      ImplementedBy implementedBy, Errors errors)
      throws ErrorsException {
    Class<?> rawType = key.getTypeLiteral().getRawType();
    Class<?> implementationType = implementedBy.value();

    // Make sure it's not the same type. TODO: Can we check for deeper cycles?
    if (implementationType == rawType) {
      throw errors.recursiveImplementationType().toException();
    }

    // Make sure implementationType extends type.
    if (!rawType.isAssignableFrom(implementationType)) {
      throw errors.notASubtype(implementationType, rawType).toException();
    }

    @SuppressWarnings("unchecked") // After the preceding check, this cast is safe.
    Class<? extends T> subclass = (Class<? extends T>) implementationType;

    // Look up the target binding.
    final Key<? extends T> targetKey = Key.get(subclass);
    final BindingImpl<? extends T> targetBinding = getBindingOrThrow(targetKey, errors);

    InternalFactory<T> internalFactory = new InternalFactory<T>() {
      public T get(Errors errors, InternalContext context, Dependency<?> dependency)
          throws ErrorsException {
        return targetBinding.getInternalFactory().get(
            errors.withSource(targetKey), context, dependency);
      }
    };

    return new LinkedBindingImpl<T>(
        this,
        key,
        rawType /* source */,
        Scopes.<T>scope(key, this, internalFactory, scoping),
        scoping,
        targetKey);
  }

  /**
   * Attempts to create a just-in-time binding for {@code key} in the root injector, falling back to
   * other ancestor injectors until this injector is tried.
   */
  private <T> BindingImpl<T> createJustInTimeBindingRecursive(Key<T> key, Errors errors)
      throws ErrorsException {
    // ask the parent to create the JIT binding
    if (parent != null) {
      try {
        return parent.createJustInTimeBindingRecursive(key, new Errors());
      } catch (ErrorsException ignored) {
      }
    }

    if (state.isBlacklisted(key)) {
      throw errors.childBindingAlreadySet(key).toException();
    }

    BindingImpl<T> binding = createJustInTimeBinding(key, errors);
    state.parent().blacklist(key);
    jitBindings.put(key, binding);
    return binding;
  }

  /**
   * Returns a new just-in-time binding created by resolving {@code key}. The strategies used to
   * create just-in-time bindings are:
   * <ol>
   *   <li>Internalizing Providers. If the requested binding is for {@code Provider<T>}, we delegate
   *     to the binding for {@code T}.
   *   <li>Converting constants.
   *   <li>ImplementedBy and ProvidedBy annotations. Only for unannotated keys.
   *   <li>The constructor of the raw type. Only for unannotated keys.
   * </ol>
   *
   * @throws com.google.inject.internal.ErrorsException if the binding cannot be created.
   */
  <T> BindingImpl<T> createJustInTimeBinding(Key<T> key, Errors errors) throws ErrorsException {
    if (state.isBlacklisted(key)) {
      throw errors.childBindingAlreadySet(key).toException();
    }

    // Handle cases where T is a Provider<?>.
    if (isProvider(key)) {
      // These casts are safe. We know T extends Provider<X> and that given Key<Provider<X>>,
      // createProviderBinding() will return BindingImpl<Provider<X>>.
      @SuppressWarnings("unchecked")
      BindingImpl binding = createProviderBinding((Key) key, errors);
      return binding;
    }

    // Try to convert a constant string binding to the requested type.
    BindingImpl<T> convertedBinding = convertConstantStringBinding(key, errors);
    if (convertedBinding != null) {
      return convertedBinding;
    }

    // If the key has an annotation...
    if (key.hasAnnotationType()) {
      // Look for a binding without annotation attributes or return null.
      if (key.hasAttributes()) {
        try {
          Errors ignored = new Errors();
          return getBindingOrThrow(key.withoutAttributes(), ignored);
        } catch (ErrorsException ignored) {
          // throw with a more appropriate message below
        }
      }
      throw errors.missingImplementation(key).toException();
    }

    Object source = key.getTypeLiteral().getRawType();
    BindingImpl<T> binding = createUnitializedBinding(key, Scoping.UNSCOPED, source, errors);
    initializeBinding(binding, errors);
    return binding;
  }

  <T> InternalFactory<? extends T> getInternalFactory(Key<T> key, Errors errors)
      throws ErrorsException {
    return getBindingOrThrow(key, errors).getInternalFactory();
  }

  /** Cached field and method injectors for a type. */
  final FailableCache<TypeLiteral<?>, ImmutableList<SingleMemberInjector>> injectors
      = new FailableCache<TypeLiteral<?>, ImmutableList<SingleMemberInjector>>() {
    protected ImmutableList<SingleMemberInjector> create(TypeLiteral<?> type, Errors errors)
        throws ErrorsException {
      int numErrorsBefore = errors.size();
      Set<InjectionPoint> injectionPoints;
      try {
        injectionPoints = InjectionPoint.forInstanceMethodsAndFields(type);
      } catch (ConfigurationException e) {
        errors.merge(e.getErrorMessages());
        injectionPoints = e.getPartialValue();
      }
      ImmutableList<SingleMemberInjector> injectors = getInjectors(injectionPoints, errors);
      errors.throwIfNewErrors(numErrorsBefore);
      return injectors;
    }
  };

  /** Returns the injectors for the specified injection points. */
  ImmutableList<SingleMemberInjector> getInjectors(
      Set<InjectionPoint> injectionPoints, Errors errors) {
    List<SingleMemberInjector> injectors = Lists.newArrayList();
    for (InjectionPoint injectionPoint : injectionPoints) {
      try {
        Errors errorsForMember = injectionPoint.isOptional()
            ? new Errors(injectionPoint)
            : errors.withSource(injectionPoint);
        SingleMemberInjector injector = injectionPoint.getMember() instanceof Field
            ? new SingleFieldInjector(this, injectionPoint, errorsForMember)
            : new SingleMethodInjector(this, injectionPoint, errorsForMember);
        injectors.add(injector);
      } catch (ErrorsException ignoredForNow) {
        // ignored for now
      }
    }
    return ImmutableList.copyOf(injectors);
  }

  // not test-covered
  public Map<Key<?>, Binding<?>> getBindings() {
    return state.getExplicitBindingsThisLevel();
  }

  interface SingleInjectorFactory<M extends Member & AnnotatedElement> {
    SingleMemberInjector create(InjectorImpl injector, M member, Errors errors)
        throws ErrorsException;
  }

  private static class BindingsMultimap {
    final Multimap<TypeLiteral<?>, Binding<?>> multimap = Multimaps.newArrayListMultimap();

    <T> void put(TypeLiteral<T> type, Binding<T> binding) {
      multimap.put(type, binding);
    }


    @SuppressWarnings("unchecked") // safe because we only put matching entries into the map
    <T> List<Binding<T>> getAll(TypeLiteral<T> type) {
      return Collections.<Binding<T>>unmodifiableList((List) multimap.get(type));
    }
  }

  /**
   * Returns parameter injectors, or {@code null} if there are no parameters.
   */
  ImmutableList<SingleParameterInjector<?>> getParametersInjectors(
      List<Dependency<?>> parameters, Errors errors) throws ErrorsException {
    if (parameters.isEmpty()) {
      return null;
    }

    int numErrorsBefore = errors.size();
    SingleParameterInjector<?>[] result = new SingleParameterInjector<?>[parameters.size()];
    int i = 0;
    for (Dependency<?> parameter : parameters) {
      try {
        result[i++] = createParameterInjector(parameter, errors.withSource(parameter));
      } catch (ErrorsException rethrownBelow) {
        // rethrown below
      }
    }

    errors.throwIfNewErrors(numErrorsBefore);
    return ImmutableList.of(result);
  }

  <T> SingleParameterInjector<T> createParameterInjector(final Dependency<T> dependency,
      final Errors errors) throws ErrorsException {
    InternalFactory<? extends T> factory = getInternalFactory(dependency.getKey(), errors);
    return new SingleParameterInjector<T>(dependency, factory);
  }

  /** Invokes a method. */
  interface MethodInvoker {
    Object invoke(Object target, Object... parameters)
        throws IllegalAccessException, InvocationTargetException;
  }

  /** Cached constructor injectors for each type */
  final FailableCache<TypeLiteral<?>, ConstructorInjector<?>> constructors
      = new FailableCache<TypeLiteral<?>, ConstructorInjector<?>>() {
    @SuppressWarnings("unchecked")
    protected ConstructorInjector<?> create(TypeLiteral<?> type, Errors errors)
        throws ErrorsException {
      return new ConstructorInjector(errors, InjectorImpl.this, type);
    }
  };

  void injectMembers(Errors errors, Object o, InternalContext context,
      List<SingleMemberInjector> injectors)
      throws ErrorsException {
    for (SingleMemberInjector injector : injectors) {
      injector.inject(errors, context, o);
    }
  }

  // Not test-covered
  public void injectMembers(final Object o) {
    Errors errors = new Errors(o.getClass());

    // configuration/validation stuff throws ConfigurationException
    List<SingleMemberInjector> injectors;
    try {
      injectors = this.injectors.get(TypeLiteral.get(o.getClass()), errors);
    } catch (ErrorsException e) {
      throw new ConfigurationException(errors.merge(e.getErrors()).getMessages());
    }

    // injection can throw ProvisionException
    try {
      injectMembersOrThrow(errors, o, injectors);
    } catch (ErrorsException e) {
      errors.merge(e.getErrors());
    }

    errors.throwProvisionExceptionIfErrorsExist();
  }

  public void injectMembersOrThrow(final Errors errors, final Object o,
      final List<SingleMemberInjector> injectors)
      throws ErrorsException {
    if (o == null) {
      return;
    }

    callInContext(new ContextualCallable<Void>() {
      public Void call(InternalContext context) throws ErrorsException {
        injectMembers(errors, o, context, injectors);
        return null;
      }
    });
  }

  public <T> Provider<T> getProvider(Class<T> type) {
    return getProvider(Key.get(type));
  }

  <T> Provider<T> getProviderOrThrow(final Key<T> key, Errors errors) throws ErrorsException {
    final InternalFactory<? extends T> factory = getInternalFactory(key, errors);
    final Dependency<T> dependency = Dependency.get(key);

    return new Provider<T>() {
      public T get() {
        final Errors errors = new Errors(dependency);
        try {
          T t = callInContext(new ContextualCallable<T>() {
            public T call(InternalContext context) throws ErrorsException {
              context.setDependency(dependency);
              try {
                return factory.get(errors, context, dependency);
              } finally {
                context.setDependency(null);
              }
            }
          });
          errors.throwIfNewErrors(0);
          return t;
        } catch (ErrorsException e) {
          throw new ProvisionException(errors.merge(e.getErrors()).getMessages());
        }
      }

      @Override public String toString() {
        return factory.toString();
      }
    };
  }

  public <T> Provider<T> getProvider(final Key<T> key) {
    Errors errors = new Errors(key);
    try {
      Provider<T> result = getProviderOrThrow(key, errors);
      errors.throwIfNewErrors(0);
      return result;
    } catch (ErrorsException e) {
      throw new ConfigurationException(errors.merge(e.getErrors()).getMessages());
    }
  }

  public <T> T getInstance(Key<T> key) {
    return getProvider(key).get();
  }

  public <T> T getInstance(Class<T> type) {
    return getProvider(type).get();
  }

  final ThreadLocal<InternalContext[]> localContext;

  /** Looks up thread local context. Creates (and removes) a new context if necessary. */
  <T> T callInContext(ContextualCallable<T> callable) throws ErrorsException {
    InternalContext[] reference = localContext.get();
    if (reference[0] == null) {
      reference[0] = new InternalContext();
      try {
        return callable.call(reference[0]);
      } finally {
        // Only remove the context if this call created it.
        localContext.remove();
      }
    }
    else {
      // Someone else will clean up this context.
      return callable.call(reference[0]);
    }
  }

  public String toString() {
    return new ToStringBuilder(Injector.class)
        .add("bindings", state.getExplicitBindingsThisLevel())
        .toString();
  }
}
