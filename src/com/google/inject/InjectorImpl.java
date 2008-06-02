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

import com.google.inject.internal.*;
import com.google.inject.spi.*;
import com.google.inject.util.Providers;
import net.sf.cglib.reflect.FastClass;
import net.sf.cglib.reflect.FastMethod;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;

/**
 * Default {@link Injector} implementation.
 *
 * @author crazybob@google.com (Bob Lee)
 * @see InjectorBuilder
 */
class InjectorImpl implements Injector {

  final Injector parentInjector;
  final Map<Key<?>, BindingImpl<?>> explicitBindings
      = new HashMap<Key<?>, BindingImpl<?>>();
  final BindingsMultimap bindingsMultimap = new BindingsMultimap();
  final Map<Class<? extends Annotation>, Scope> scopes
      = new HashMap<Class<? extends Annotation>, Scope>();
  final List<MatcherAndConverter<?>> converters
      = new ArrayList<MatcherAndConverter<?>>();
  final Map<Key<?>, BindingImpl<?>> parentBindings
      = new HashMap<Key<?>, BindingImpl<?>>();
  final Map<Object, Void> outstandingInjections
      = new IdentityHashMap<Object, Void>();

  final ErrorHandler errorHandler;
  Reflection reflection;

  InjectorImpl(Injector parentInjector, ErrorHandler errorHandler) {
    this.parentInjector = parentInjector;
    this.errorHandler = errorHandler;
  }

  void validateOustandingInjections() {
    for (Object toInject : outstandingInjections.keySet()) {
      injectors.get(toInject.getClass());
    }
  }

  /**
   * Performs creation-time injections on all objects that require it. Whenever
   * fulfilling an injection depends on another object that requires injection,
   * we use {@link InternalContext#ensureMemberInjected} to inject that member
   * first.
   *
   * <p>If the two objects are codependent (directly or transitively), ordering
   * of injection is arbitrary.
   */
  void fulfillOutstandingInjections() {
    callInContext(new ContextualCallable<Void>() {
      public Void call(InternalContext context) {
        // loop over a defensive copy, since ensureMemberInjected() mutates the
        // outstandingInjections set
        for (Object toInject : new ArrayList<Object>(outstandingInjections.keySet())) {
          context.ensureMemberInjected(toInject);
        }
        return null;
      }
    });

    if (!outstandingInjections.isEmpty()) {
      throw new IllegalStateException("failed to satisfy " + outstandingInjections);
    }
  }

  /**
   * Indexes bindings by type.
   */
  void index() {
    for (BindingImpl<?> binding : explicitBindings.values()) {
      index(binding);
    }
  }

  <T> void index(BindingImpl<T> binding) {
    bindingsMultimap.put(binding.getKey().getTypeLiteral(), binding);
  }

  // not test-covered
  public <T> List<Binding<T>> findBindingsByType(TypeLiteral<T> type) {
    return Collections.<Binding<T>>unmodifiableList(
        bindingsMultimap.getAll(type));
  }

  // not test-covered
  <T> List<String> getNamesOfBindingAnnotations(TypeLiteral<T> type) {
    List<String> names = new ArrayList<String>();
    for (Binding<T> binding : findBindingsByType(type)) {
      Key<T> key = binding.getKey();
      if (!key.hasAnnotationType()) {
        names.add("[no annotation]");
      } else {
        names.add(key.getAnnotationName());
      }
    }
    return names;
  }

  /**
   * This is only used during Injector building.
   */
  void withDefaultSource(Object defaultSource, Runnable runnable) {
    SourceProviders.withDefault(defaultSource, runnable);
  }

  /**
   * Returns the binding for {@code key}, or {@code null} if that binding
   * cannot be resolved.
   */
  public <T> BindingImpl<T> getBinding(Key<T> key) {
    try {
      return getBindingOrThrow(key);
    } catch(ResolveFailedException e) {
      return null;
    }
  }

  /**
   * Gets a binding implementation.  First, it check to see if the parent has
   * a binding.  If the parent has a binding and the binding is scoped, it
   * will use that binding.  Otherwise, this checks for an explicit binding.
   * If no explicit binding is found, it looks for a just-in-time binding.
   */
  public <T> BindingImpl<T> getBindingOrThrow(Key<T> key) throws ResolveFailedException {
    if (parentInjector != null) {
      BindingImpl<T> bindingImpl = getParentBinding(key);
      if (bindingImpl != null) {
        return bindingImpl;
      }
    }

    // Check explicit bindings, i.e. bindings created by modules.
    BindingImpl<T> binding = getExplicitBindingImpl(key);
    if (binding != null) {
      return binding;
    }

    // Look for an on-demand binding.
    return getJitBindingImpl(key);
  }


  /**
   * Checks the parent injector for a scoped binding, and if available, creates
   * an appropriate binding local to this injector and remembers it.
   */
  @SuppressWarnings("unchecked")
  private <T> BindingImpl<T> getParentBinding(Key<T> key) {
    BindingImpl<T> bindingImpl;
    synchronized(parentBindings) {
      // null values will mean that the parent doesn't have this binding
      if (!parentBindings.containsKey(key)) {
        Binding<T> binding = null;
        try {
          binding = parentInjector.getBinding(key);
        } catch (ConfigurationException e) {
          // if this happens, the parent can't create this key, and we ignore it
        }
        if (binding != null
            && binding.getScope() != null
            && !binding.getScope().equals(Scopes.NO_SCOPE)) {
          bindingImpl = new ProviderInstanceBindingImpl(
              this,
              key,
              binding.getSource(),
              new InternalFactoryToProviderAdapter(binding.getProvider(),
                  binding.getSource()),
              Scopes.NO_SCOPE,
              binding.getProvider(),
              LoadStrategy.LAZY);
        } else {
          bindingImpl = null;
        }
        parentBindings.put(key, bindingImpl);
      } else {
        bindingImpl = (BindingImpl<T>) parentBindings.get(key);
      }
    }
    return bindingImpl;
  }

  public <T> Binding<T> getBinding(Class<T> type) {
    return getBinding(Key.get(type));
  }

  /**
   * Gets a binding which was specified explicitly in a module.
   */
  @SuppressWarnings("unchecked")
  <T> BindingImpl<T> getExplicitBindingImpl(Key<T> key) {
    return (BindingImpl<T>) explicitBindings.get(key);
  }

  /**
   * Gets a just-in-time binding. This could be an injectable class (including
   * those with @ImplementedBy), an automatically converted constant, a
   * Provider<X> binding, etc.
   */
  @SuppressWarnings("unchecked")
  <T> BindingImpl<T> getJitBindingImpl(Key<T> key) throws ResolveFailedException {
    synchronized (jitBindings) {
      // Support null values.
      if (!jitBindings.containsKey(key)) {
        BindingImpl<T> binding = createBindingJustInTime(key);
        jitBindings.put(key, binding);
        return binding;
      } else {
        return (BindingImpl<T>) jitBindings.get(key);
      }
    }
  }

  /** Just-in-time binding cache. */
  final Map<Key<?>, BindingImpl<?>> jitBindings
      = new HashMap<Key<?>, BindingImpl<?>>();

  /**
   * Returns true if the key type is Provider<?> (but not a subclass of
   * Provider<?>).
   */
  static boolean isProvider(Key<?> key) {
    return key.getTypeLiteral().getRawType().equals(Provider.class);
  }

  /**
   * Creates a synthetic binding to Provider<T>, i.e. a binding to the provider
   * from Binding<T>.
   */
  private <T> BindingImpl<Provider<T>> createProviderBinding(
      Key<Provider<T>> key, LoadStrategy loadStrategy) throws ResolveFailedException {
    Type providerType = key.getTypeLiteral().getType();

    // If the Provider has no type parameter (raw Provider)...
    if (!(providerType instanceof ParameterizedType)) {
      throw new ResolveFailedException(ErrorMessages.CANNOT_INJECT_RAW_PROVIDER);
    }

    Type entryType
        = ((ParameterizedType) providerType).getActualTypeArguments()[0];

    // This cast is safe. 
    @SuppressWarnings("unchecked")
    Key<T> providedKey = (Key<T>) key.ofType(entryType);

    return new ProviderBindingImpl<T>(this, key, getBindingOrThrow(providedKey), loadStrategy);
  }

  void handleMissingBinding(Object source, Key<?> key) {
    List<String> otherNames = getNamesOfBindingAnnotations(key.getTypeLiteral());

    if (source instanceof Member) {
      source = StackTraceElements.forMember((Member) source);
    }

    if (otherNames.isEmpty()) {
      errorHandler.handle(source, ErrorMessages.MISSING_BINDING, key);
    }
    else {
      errorHandler.handle(source,
          ErrorMessages.MISSING_BINDING_BUT_OTHERS_EXIST, key, otherNames);
    }
  }

  static class ProviderBindingImpl<T> extends BindingImpl<Provider<T>>
      implements ProviderBinding<T> {

    final Binding<T> providedBinding;

    ProviderBindingImpl(InjectorImpl injector, Key<Provider<T>> key,
        Binding<T> providedBinding, LoadStrategy loadStrategy) {
      super(injector, key, SourceProviders.UNKNOWN_SOURCE,
          createInternalFactory(providedBinding), Scopes.NO_SCOPE, loadStrategy);
      this.providedBinding = providedBinding;
    }

    static <T> InternalFactory<Provider<T>> createInternalFactory(
        Binding<T> providedBinding) {
      final Provider<T> provider = providedBinding.getProvider();
      return new InternalFactory<Provider<T>>() {
        public Provider<T> get(InternalContext context,
            InjectionPoint injectionPoint) {
          return provider;
        }
      };
    }

    public void accept(BindingVisitor<? super Provider<T>> bindingVisitor) {
      bindingVisitor.visit(this);
    }

    public Binding<T> getTarget() {
      return providedBinding;
    }
  }

  <T> BindingImpl<T> invalidBinding(Class<T> clazz) {
    return invalidBinding(Key.get(clazz));
  }

  <T> BindingImpl<T> invalidBinding(Key<T> key) {
    return new InvalidBindingImpl<T>(
        this, key, SourceProviders.defaultSource());
  }

  /**
   * Converts a constant string binding to the required type.
   *
   * <p>If the required type is eligible for conversion and a constant string
   * binding is found but the actual conversion fails, an error is generated.
   *
   * <p>If the type is not eligible for conversion or a constant string
   * binding is not found, this method returns null.
   */
  private <T> BindingImpl<T> convertConstantStringBinding(Key<T> key)
      throws ResolveFailedException {
    // Find a constant string binding.
    Key<String> stringKey = key.ofType(String.class);
    BindingImpl<String> stringBinding = getExplicitBindingImpl(stringKey);
    if (stringBinding == null || !stringBinding.isConstant()) {
      // No constant string binding found.
      return null;
    }

    String stringValue = stringBinding.getProvider().get();

    // Find a matching type converter.
    TypeLiteral<T> type = key.getTypeLiteral();
    MatcherAndConverter<?> matchingConverter = null;
    for (MatcherAndConverter<?> converter : converters) {
      if (converter.getTypeMatcher().matches(type)) {
        if (matchingConverter != null) {
          throw new ResolveFailedException(ErrorMessages.AMBIGUOUS_TYPE_CONVERSION,
              stringValue, type, matchingConverter, converter);
        }

        matchingConverter = converter;
      }
    }

    if (matchingConverter == null) {
      // No converter can handle the given type.
      return null;
    }

    // Try to convert the string. A failed conversion results in an error.
    try {
      // This cast is safe because we double check below.
      @SuppressWarnings("unchecked")
      T converted = (T) matchingConverter.getTypeConverter()
          .convert(stringValue, key.getTypeLiteral());

      if (converted == null) {
        throw new ResolveFailedException(ErrorMessages.CONVERTER_RETURNED_NULL);
      }

      // We have to filter out primitive types because an Integer is not an
      // instance of int, and we provide converters for all the primitive types
      // and know that they work anyway.
      if (!type.getRawType().isInstance(converted)) {
        throw new ResolveFailedException(ErrorMessages.CONVERSION_TYPE_ERROR, converted, type);
      }

      return new ConvertedConstantBindingImpl<T>(
          this, key, converted, stringBinding);
    } catch (ResolveFailedException e) {
      throw e;
    } catch (Exception e) {
      throw new ResolveFailedException(ErrorMessages.CONVERSION_ERROR, stringValue,
          stringBinding.getSource(), type, matchingConverter, e.getMessage());
    }
  }

  private static class ConvertedConstantBindingImpl<T> extends BindingImpl<T>
      implements ConvertedConstantBinding<T> {

    final T value;
    final Provider<T> provider;
    final Binding<String> originalBinding;

    ConvertedConstantBindingImpl(InjectorImpl injector, Key<T> key, T value,
        Binding<String> originalBinding) {
      super(injector, key, SourceProviders.UNKNOWN_SOURCE,
          new ConstantFactory<T>(value), Scopes.NO_SCOPE, LoadStrategy.LAZY);
      this.value = value;
      this.provider = Providers.of(value);
      this.originalBinding = originalBinding;
    }

    @Override
    public Provider<T> getProvider() {
      return this.provider;
    }

    public void accept(BindingVisitor<? super T> bindingVisitor) {
      bindingVisitor.visit(this);
    }

    public T getValue() {
      return this.value;
    }

    public Binding<String> getOriginal() {
      return this.originalBinding;
    }

    @Override
    public String toString() {
      return new ToStringBuilder(ConvertedConstantBinding.class)
          .add("key", key)
          .add("value", value)
          .add("original", originalBinding)
          .toString();
    }
  }

  <T> BindingImpl<T> createBindingFromType(Class<T> type, LoadStrategy loadStrategy)
      throws ResolveFailedException {
    BindingImpl<T> binding = createUnitializedBinding(
        type, null, SourceProviders.defaultSource(), loadStrategy);
    initializeBinding(binding);
    return binding;
  }

  <T> void initializeBinding(BindingImpl<T> binding) throws ResolveFailedException {
    // Put the partially constructed binding in the map a little early. This
    // enables us to handle circular dependencies.
    // Example: FooImpl -> BarImpl -> FooImpl.
    // Note: We don't need to synchronize on jitBindings during injector
    // creation.
    if (binding instanceof ClassBindingImpl<?>) {
      Key<T> key = binding.getKey();
      jitBindings.put(key, binding);
      boolean successful = false;
      try {
        binding.initialize(this);
        successful = true;
      } finally {
        if (!successful) {
          jitBindings.remove(key);
        }
      }
    }
  }

  /**
   * Creates a binding for an injectable type with the given scope. Looks for
   * a scope on the type if none is specified.
   */
  <T> BindingImpl<T> createUnitializedBinding(Class<T> type,
      Scope scope, Object source, LoadStrategy loadStrategy) throws ResolveFailedException {
    // Don't try to inject arrays, or enums.
    if (type.isArray() || type.isEnum()) {
      throw new ResolveFailedException(ErrorMessages.MISSING_BINDING, type);
    }

    // Handle @ImplementedBy
    ImplementedBy implementedBy = type.getAnnotation(ImplementedBy.class);
    if (implementedBy != null) {
      // TODO: Scope internal factory.
      return createImplementedByBinding(type, implementedBy, loadStrategy);
    }

    // Handle @ProvidedBy.
    ProvidedBy providedBy = type.getAnnotation(ProvidedBy.class);
    if (providedBy != null) {
      // TODO: Scope internal factory.
      return createProvidedByBinding(type, providedBy, loadStrategy);
    }

    // We can't inject abstract classes.
    // TODO: Method interceptors could actually enable us to implement
    // abstract types. Should we remove this restriction?
    if (Modifier.isAbstract(type.getModifiers())) {
      throw new ResolveFailedException(ErrorMessages.CANNOT_INJECT_ABSTRACT_TYPE, type);
    }

    // Error: Inner class.
    if (Classes.isInnerClass(type)) {
      throw new ResolveFailedException(ErrorMessages.CANNOT_INJECT_INNER_CLASS, type);
    }

    if (scope == null) {
      scope = Scopes.getScopeForType(type, scopes, errorHandler);
    }

    Key<T> key = Key.get(type);

    LateBoundConstructor<T> lateBoundConstructor = new LateBoundConstructor<T>();
    InternalFactory<? extends T> scopedFactory
        = Scopes.scope(key, this, lateBoundConstructor, scope);

    return new ClassBindingImpl<T>(this, key, source, scopedFactory, scope, lateBoundConstructor,
        loadStrategy);
  }

  static class LateBoundConstructor<T> implements InternalFactory<T> {

    ConstructorInjector<T> constructorInjector;

    void bind(InjectorImpl injector, Class<T> implementation)
        throws ResolveFailedException {
      this.constructorInjector = injector.getConstructor(implementation);
    }

    @SuppressWarnings("unchecked")
    public T get(InternalContext context, InjectionPoint<?> injectionPoint) {
      if (constructorInjector == null) {
        throw new IllegalStateException("Construct before bind, " + constructorInjector);
      }

      // This may not actually be safe because it could return a super type
      // of T (if that's all the client needs), but it should be OK in
      // practice thanks to the wonders of erasure.
      return (T) constructorInjector.construct(
          context, injectionPoint.getKey().getRawType());
    }
  }

  /**
   * Creates a binding for a type annotated with @ProvidedBy.
   */
  <T> BindingImpl<T> createProvidedByBinding(final Class<T> type,
      ProvidedBy providedBy, LoadStrategy loadStrategy) throws ResolveFailedException {
    final Class<? extends Provider<?>> providerType = providedBy.value();

    // Make sure it's not the same type. TODO: Can we check for deeper loops?
    if (providerType == type) {
      throw new ResolveFailedException(ErrorMessages.RECURSIVE_PROVIDER_TYPE);
    }

    // TODO: Make sure the provided type extends type. We at least check
    // the type at runtime below.

    // Assume the provider provides an appropriate type. We double check at
    // runtime.
    @SuppressWarnings("unchecked")
    Key<? extends Provider<T>> providerKey
        = (Key<? extends Provider<T>>) Key.get(providerType);
    final BindingImpl<? extends Provider<?>> providerBinding
        = getBindingOrThrow(providerKey);

    InternalFactory<T> internalFactory = new InternalFactory<T>() {
      public T get(InternalContext context, InjectionPoint injectionPoint) {
        Provider<?> provider
            = providerBinding.internalFactory.get(context, injectionPoint);
        Object o = provider.get();
        try {
          return type.cast(o);
        } catch (ClassCastException e) {
          errorHandler.handle(StackTraceElements.forType(type),
              ErrorMessages.SUBTYPE_NOT_PROVIDED, providerType, type);
          throw new AssertionError();
        }
      }
    };

    return new LinkedProviderBindingImpl<T>(this, Key.get(type),
        StackTraceElements.forType(type), internalFactory, Scopes.NO_SCOPE,
        providerKey, loadStrategy);
  }

  /**
   * Creates a binding for a type annotated with @ImplementedBy.
   */
  <T> BindingImpl<T> createImplementedByBinding(Class<T> type,
      ImplementedBy implementedBy, LoadStrategy loadStrategy) throws ResolveFailedException {
    // TODO: Use scope annotation on type if present. Right now, we always
    // use NO_SCOPE.

    Class<?> implementationType = implementedBy.value();

    // Make sure it's not the same type. TODO: Can we check for deeper cycles?
    if (implementationType == type) {
      throw new ResolveFailedException(ErrorMessages.RECURSIVE_IMPLEMENTATION_TYPE);
    }

    // Make sure implementationType extends type.
    if (!type.isAssignableFrom(implementationType)) {
      throw new ResolveFailedException(ErrorMessages.NOT_A_SUBTYPE, implementationType, type);
    }

    // After the preceding check, this cast is safe.
    @SuppressWarnings("unchecked")
    Class<? extends T> subclass = (Class<? extends T>) implementationType;

    // Look up the target binding.
    final BindingImpl<? extends T> targetBinding
        = getBindingOrThrow(Key.get(subclass));

    InternalFactory<T> internalFactory = new InternalFactory<T>() {
      public T get(InternalContext context, InjectionPoint<?> injectionPoint) {
        return targetBinding.internalFactory.get(context, injectionPoint);
      }
    };

    return new LinkedBindingImpl<T>(this, Key.get(type), 
        StackTraceElements.forType(type), internalFactory, Scopes.NO_SCOPE,
        Key.get(subclass), loadStrategy);
  }

  <T> BindingImpl<T> createBindingJustInTime(Key<T> key) throws ResolveFailedException {
    // Handle cases where T is a Provider<?>.
    if (isProvider(key)) {
      // These casts are safe. We know T extends Provider<X> and that given
      // Key<Provider<X>>, createProviderBinding() will return
      // BindingImpl<Provider<X>>.
      @SuppressWarnings({ "UnnecessaryLocalVariable", "unchecked" })
      BindingImpl<T> binding
          = (BindingImpl<T>) createProviderBinding((Key) key, LoadStrategy.LAZY);
      return binding;
    }

    // Try to convert a constant string binding to the requested type.
    BindingImpl<T> convertedBinding = convertConstantStringBinding(key);
    if (convertedBinding != null) {
      return convertedBinding;
    }

    // If the key has an annotation...
    if (key.hasAnnotationType()) {
      // Look for a binding without annotation attributes or return null.
      if (key.hasAttributes()) {
        try {
          return getBindingOrThrow(key.withoutAttributes());
        } catch (ResolveFailedException ignored) {
          // throw with a more appropriate message below
        }
      }
      throw new ResolveFailedException(ErrorMessages.MISSING_BINDING, key);
    }

    // Create a binding based on the raw type.
    @SuppressWarnings("unchecked")
    Class<T> clazz = (Class<T>) key.getTypeLiteral().getRawType();
    return createBindingFromType(clazz, LoadStrategy.LAZY);
  }

  <T> InternalFactory<? extends T> getInternalFactory(Key<T> key)
      throws ResolveFailedException {
    return getBindingOrThrow(key).internalFactory;
  }

  /**
   * Field and method injectors.
   */
  final Map<Class<?>, List<SingleMemberInjector>> injectors
      = new ReferenceCache<Class<?>, List<SingleMemberInjector>>() {
    protected List<SingleMemberInjector> create(Class<?> key) {
      List<SingleMemberInjector> injectors
          = new ArrayList<SingleMemberInjector>();
      addInjectors(key, injectors);
      return injectors;
    }
  };

  /**
   * Recursively adds injectors for fields and methods from the given class to
   * the given list. Injects parent classes before sub classes.
   */
  void addInjectors(Class clazz, List<SingleMemberInjector> injectors) {
    if (clazz == Object.class) {
      return;
    }

    // Add injectors for superclass first.
    addInjectors(clazz.getSuperclass(), injectors);

    // TODO (crazybob): Filter out overridden members.
    addSingleInjectorsForFields(clazz.getDeclaredFields(), false, injectors);
    addSingleInjectorsForMethods(clazz.getDeclaredMethods(), false, injectors);
  }

  void addSingleInjectorsForMethods(Method[] methods, boolean statics,
      List<SingleMemberInjector> injectors) {
    addInjectorsForMembers(Arrays.asList(methods), statics, injectors,
        new SingleInjectorFactory<Method>() {
          public SingleMemberInjector create(InjectorImpl injector,
              Method method) throws ResolveFailedException {
            return new SingleMethodInjector(injector, method);
          }
        });
  }

  void addSingleInjectorsForFields(Field[] fields, boolean statics,
      List<SingleMemberInjector> injectors) {
    addInjectorsForMembers(Arrays.asList(fields), statics, injectors,
        new SingleInjectorFactory<Field>() {
          public SingleMemberInjector create(InjectorImpl injector,
              Field field) throws ResolveFailedException {
            return new SingleFieldInjector(injector, field);
          }
        });
  }

  <M extends Member & AnnotatedElement> void addInjectorsForMembers(
      List<M> members, boolean statics, List<SingleMemberInjector> injectors,
      SingleInjectorFactory<M> injectorFactory) {
    for (M member : members) {
      if (isStatic(member) == statics) {
        Inject inject = member.getAnnotation(Inject.class);
        if (inject != null) {
          try {
            injectors.add(injectorFactory.create(this, member));
          }
          catch (ResolveFailedException e) {
            if (!inject.optional()) {
              // TODO: Report errors for more than one parameter per member.
              errorHandler.handle(member, e.getMessage());
            }
          }
        }
      }
    }
  }

  Map<Key<?>, BindingImpl<?>> internalBindings() {
    return explicitBindings;
  }

  // not test-covered
  public Map<Key<?>, Binding<?>> getBindings() {
    return Collections.<Key<?>, Binding<?>>unmodifiableMap(explicitBindings);
  }

  interface SingleInjectorFactory<M extends Member & AnnotatedElement> {
    SingleMemberInjector create(InjectorImpl injector, M member)
        throws ResolveFailedException;
  }

  private boolean isStatic(Member member) {
    return Modifier.isStatic(member.getModifiers());
  }

  private static class BindingsMultimap {
    private final Map<TypeLiteral<?>, List<? extends BindingImpl<?>>> map
        = new HashMap<TypeLiteral<?>, List<? extends BindingImpl<?>>>();

    public <T> void put(TypeLiteral<T> type, BindingImpl<T> binding) {
      List<BindingImpl<T>> bindingsForThisType = getFromMap(type);
      if (bindingsForThisType == null) {
        bindingsForThisType = new ArrayList<BindingImpl<T>>();
        // We only put matching entries into the map
        map.put(type, bindingsForThisType);
      }
      bindingsForThisType.add(binding);
    }

    public <T> List<BindingImpl<T>> getAll(TypeLiteral<T> type) {
      List<BindingImpl<T>> list = getFromMap(type);
      return list == null ? Collections.<BindingImpl<T>>emptyList() : list;
    }

    // safe because we only put matching entries into the map
    @SuppressWarnings("unchecked")
    private <T> List<BindingImpl<T>> getFromMap(TypeLiteral<T> type) {
      return (List<BindingImpl<T>>) map.get(type);
    }
  }

  class SingleFieldInjector implements SingleMemberInjector {

    final Field field;
    final InternalFactory<?> factory;
    final InjectionPoint<?> injectionPoint;

    public SingleFieldInjector(final InjectorImpl injector, Field field)
        throws ResolveFailedException {
      this.field = field;

      // Ewwwww...
      field.setAccessible(true);

      final Key<?> key = Keys.get(
          field.getGenericType(), field, field.getAnnotations(), errorHandler);
      factory = new ResolvingCallable<InternalFactory<?>>() {
          public InternalFactory<?> call() throws ResolveFailedException {
            return injector.getInternalFactory(key);
          }
        }.runWithDefaultSource(StackTraceElements.forMember(field));

      this.injectionPoint = InjectionPoint.newInstance(field,
          Nullability.forAnnotations(field.getAnnotations()), key, injector);
    }

    public Collection<Dependency<?>> getDependencies() {
      return Collections.<Dependency<?>>singleton(injectionPoint);
    }

    public void inject(InternalContext context, Object o) {
      context.setInjectionPoint(injectionPoint);
      try {
        Object value = factory.get(context, injectionPoint);
        field.set(o, value);
      }
      catch (IllegalAccessException e) {
        throw new AssertionError(e);
      }
      catch (ConfigurationException e) {
        throw e;
      }
      catch (ProvisionException provisionException) {
        provisionException.addContext(injectionPoint);
        throw provisionException;
      }
      catch (RuntimeException runtimeException) {
        throw new ProvisionException(runtimeException,
            ErrorMessages.ERROR_INJECTING_FIELD);
      }
      finally {
        context.setInjectionPoint(null);
      }
    }
  }

  /**
   * Gets parameter injectors.
   *
   * @param member to which the parameters belong
   * @return injections
   */
  SingleParameterInjector<?>[] getParametersInjectors(Member member,
      List<Parameter<?>> parameters)
      throws ResolveFailedException {
    SingleParameterInjector<?>[] parameterInjectors
        = new SingleParameterInjector<?>[parameters.size()];
    int index = 0;
    for (Parameter<?> parameter : parameters) {
      parameterInjectors[index] = createParameterInjector(parameter, member);
      index++;
    }
    return parameterInjectors;
  }

  <T> SingleParameterInjector<T> createParameterInjector(
      final Parameter<T> parameter, Member member)
      throws ResolveFailedException {
    InternalFactory<? extends T> factory
        = new ResolvingCallable<InternalFactory<? extends T>>() {
          public InternalFactory<? extends T> call() throws ResolveFailedException {
            return getInternalFactory(parameter.getKey());
          }
    }.runWithDefaultSource(StackTraceElements.forMember(member));

    InjectionPoint<T> injectionPoint = InjectionPoint.newInstance(
        member, parameter.getIndex(), parameter.getNullability(), parameter.getKey(), this);
    return new SingleParameterInjector<T>(injectionPoint, factory);
  }

  static class SingleMethodInjector implements SingleMemberInjector {

    final MethodInvoker methodInvoker;
    final SingleParameterInjector<?>[] parameterInjectors;

    public SingleMethodInjector(InjectorImpl injector, final Method method)
        throws ResolveFailedException {
      // We can't use FastMethod if the method is private.
      if (Modifier.isPrivate(method.getModifiers())
          || Modifier.isProtected(method.getModifiers())) {
        method.setAccessible(true);
        this.methodInvoker = new MethodInvoker() {
          public Object invoke(Object target, Object... parameters) throws
              IllegalAccessException, InvocationTargetException {
            return method.invoke(target, parameters);
          }
        };
      }
      else {
        FastClass fastClass = GuiceFastClass.create(method.getDeclaringClass());
        final FastMethod fastMethod = fastClass.getMethod(method);

        this.methodInvoker = new MethodInvoker() {
          public Object invoke(Object target, Object... parameters)
          throws IllegalAccessException, InvocationTargetException {
            return fastMethod.invoke(target, parameters);
          }
        };
      }

      Type[] parameterTypes = method.getGenericParameterTypes();
      parameterInjectors = parameterTypes.length > 0
          ? injector.getParametersInjectors(method,
              Parameter.forMethod(injector.errorHandler, method))
          : null;
    }

    public void inject(InternalContext context, Object o) {
      try {
        methodInvoker.invoke(o, getParameters(context, parameterInjectors));
      }
      catch (IllegalAccessException e) {
        throw new AssertionError(e);
      }
      catch (InvocationTargetException e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        throw new ProvisionException(cause,
            ErrorMessages.ERROR_INJECTING_METHOD);
      }
    }

    public Collection<Dependency<?>> getDependencies() {
      List<Dependency<?>> dependencies = new ArrayList<Dependency<?>>();
      for (SingleParameterInjector<?> parameterInjector : parameterInjectors) {
        dependencies.add(parameterInjector.injectionPoint);
      }
      return Collections.unmodifiableList(dependencies);
    }
  }

  /**
   * Invokes a method.
   */
  interface MethodInvoker {
    Object invoke(Object target, Object... parameters) throws
        IllegalAccessException, InvocationTargetException;
  }

  final Map<Class<?>, Object> constructors
      = new ReferenceCache<Class<?>, Object>() {
    @SuppressWarnings("unchecked")
    protected Object create(Class<?> implementation) {
      if (!Classes.isConcrete(implementation)) {
        return new ResolveFailedException(
            ErrorMessages.CANNOT_INJECT_ABSTRACT_TYPE, implementation);
      }
      if (Classes.isInnerClass(implementation)) {
        return new ResolveFailedException(ErrorMessages.CANNOT_INJECT_INNER_CLASS, implementation);
      }

      return new ConstructorInjector(InjectorImpl.this, implementation);
    }
  };

  static class SingleParameterInjector<T> {

    final InjectionPoint<T> injectionPoint;
    final InternalFactory<? extends T> factory;

    public SingleParameterInjector(InjectionPoint<T> injectionPoint,
        InternalFactory<? extends T> factory) {
      this.injectionPoint = injectionPoint;
      this.factory = factory;
    }

    T inject(InternalContext context) {
      context.setInjectionPoint(injectionPoint);
      try {
        return factory.get(context, injectionPoint);
      }
      catch (ConfigurationException e) {
        throw e;
      }
      catch (ProvisionException provisionException) {
        provisionException.addContext(injectionPoint);
        throw provisionException;
      }
      catch (RuntimeException runtimeException) {
        throw new ProvisionException(runtimeException,
            ErrorMessages.ERROR_INJECTING_METHOD);
      }
      finally {
        context.setInjectionPoint(injectionPoint);
      }
    }
  }

  /**
   * Iterates over parameter injectors and creates an array of parameter
   * values.
   */
  static Object[] getParameters(InternalContext context,
      SingleParameterInjector[] parameterInjectors) {
    if (parameterInjectors == null) {
      return null;
    }

    Object[] parameters = new Object[parameterInjectors.length];
    for (int i = 0; i < parameters.length; i++) {
      parameters[i] = parameterInjectors[i].inject(context);
    }
    return parameters;
  }

  void injectMembers(Object o, InternalContext context) {
    if (o == null) {
      return;
    }

    List<SingleMemberInjector> injectorsForClass = injectors.get(o.getClass());
    for (SingleMemberInjector injector : injectorsForClass) {
      injector.inject(context, o);
    }
  }

  // Not test-covered
  public void injectMembers(final Object o) {
    callInContext(new ContextualCallable<Void>() {
      public Void call(InternalContext context) {
        injectMembers(o, context);
        return null;
      }
    });
  }

  public <T> Provider<T> getProvider(Class<T> type) {
    return getProvider(Key.get(type));
  }

  <T> Provider<T> getProviderOrThrow(final Key<T> key) throws ResolveFailedException {
    final InternalFactory<? extends T> factory = getInternalFactory(key);

    return new Provider<T>() {
      public T get() {
        return callInContext(new ContextualCallable<T>() {
          public T call(InternalContext context) {
            InjectionPoint<T> injectionPoint
                = InjectionPoint.newInstance(key, InjectorImpl.this);
            context.setInjectionPoint(injectionPoint);
            try {
              return factory.get(context, injectionPoint);
            }
            catch(ProvisionException provisionException) {
              provisionException.addContext(injectionPoint);
              throw provisionException;
            }
            finally {
              context.setInjectionPoint(null);
            }
          }
        });
      }

      public String toString() {
        return factory.toString();
      }
    };
  }

  public <T> Provider<T> getProvider(final Key<T> key) {
    try {
      return getProviderOrThrow(key);
    } catch (ResolveFailedException e) {
      throw new ConfigurationException(
          "Missing binding to " + ErrorMessages.convert(key) + ": " + e.getMessage());
    }
  }

  public <T> T getInstance(Key<T> key) {
    return getProvider(key).get();
  }

  public <T> T getInstance(Class<T> type) {
    return getProvider(type).get();
  }

  final ThreadLocal<InternalContext[]> localContext
      = new ThreadLocal<InternalContext[]>() {
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
      }
      finally {
        // Only remove the context if this call created it.
        reference[0] = null;
      }
    }
    else {
      // Someone else will clean up this context.
      return callable.call(reference[0]);
    }
  }

  /**
   * Gets a constructor function for a given implementation class.
   */
  @SuppressWarnings("unchecked")
  <T> ConstructorInjector<T> getConstructor(Class<T> implementation)
      throws ResolveFailedException {
    Object o = constructors.get(implementation);
    if (o instanceof ResolveFailedException) {
      throw (ResolveFailedException) o;
    } else if (o instanceof ConstructorInjector<?>) {
      return (ConstructorInjector<T>) o;
    } else {
      throw new AssertionError();
    }
  }

  /**
   * Injects a field or method in a given object.
   */
  public interface SingleMemberInjector {
    void inject(InternalContext context, Object o);
    Collection<Dependency<?>> getDependencies();
  }

  List<Dependency<?>> getModifiableFieldAndMethodDependenciesFor(
      Class<?> clazz) {
    List<SingleMemberInjector> injectors = this.injectors.get(clazz);
    List<Dependency<?>> dependencies = new ArrayList<Dependency<?>>();
    for (SingleMemberInjector singleMemberInjector : injectors) {
      dependencies.addAll(singleMemberInjector.getDependencies());
    }
    return dependencies;
  }

  Collection<Dependency<?>> getFieldAndMethodDependenciesFor(Class<?> clazz) {
    return Collections.unmodifiableList(
        getModifiableFieldAndMethodDependenciesFor(clazz));
  }

  public String toString() {
    return new ToStringBuilder(Injector.class)
        .add("bindings", explicitBindings)
        .toString();
  }
}
