package com.google.inject.internal;

import static com.google.common.base.Preconditions.checkState;
import static com.google.inject.internal.Element.Type.MULTIBINDER;
import static com.google.inject.internal.Errors.checkConfiguration;
import static com.google.inject.internal.Errors.checkNotNull;
import static com.google.inject.internal.InternalMethodHandles.castReturnToObject;
import static com.google.inject.name.Names.named;
import static java.lang.invoke.MethodType.methodType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.Keep;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.internal.InternalProviderInstanceBindingImpl.InitializationTiming;
import com.google.inject.multibindings.MultibinderBinding;
import com.google.inject.multibindings.MultibindingsTargetVisitor;
import com.google.inject.spi.BindingTargetVisitor;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.Message;
import com.google.inject.spi.ProviderInstanceBinding;
import com.google.inject.spi.ProviderWithExtensionVisitor;
import com.google.inject.util.Types;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The actual multibinder plays several roles:
 *
 * <p>As a Multibinder, it acts as a factory for LinkedBindingBuilders for each of the set's
 * elements. Each binding is given an annotation that identifies it as a part of this set.
 *
 * <p>As a Module, it installs the binding to the set itself. As a module, this implements equals()
 * and hashcode() in order to trick Guice into executing its configure() method only once. That
 * makes it so that multiple multibinders can be created for the same target collection, but only
 * one is bound. Since the list of bindings is retrieved from the injector itself (and not the
 * multibinder), each multibinder has access to all contributions from all multibinders.
 *
 * <p>As a Provider, this constructs the set instances.
 *
 * <p>We use a subclass to hide 'implements Module, Provider' from the public API.
 */
public final class RealMultibinder<T> implements Module {

  /** Implementation of newSetBinder. */
  public static <T> RealMultibinder<T> newRealSetBinder(Binder binder, Key<T> key) {
    RealMultibinder<T> result = new RealMultibinder<>(binder, key);
    binder.install(result);
    return result;
  }

  @SuppressWarnings("unchecked") // wrapping a T in a Set safely returns a Set<T>
  static <T> TypeLiteral<Set<T>> setOf(TypeLiteral<T> elementType) {
    Type type = Types.setOf(elementType.getType());
    return (TypeLiteral<Set<T>>) TypeLiteral.get(type);
  }

  @SuppressWarnings("unchecked")
  static <T> TypeLiteral<Collection<Provider<T>>> collectionOfProvidersOf(
      TypeLiteral<T> elementType) {
    Type providerType = Types.providerOf(elementType.getType());
    Type type = Types.collectionOf(providerType);
    return (TypeLiteral<Collection<Provider<T>>>) TypeLiteral.get(type);
  }

  @SuppressWarnings("unchecked")
  static <T> TypeLiteral<Collection<jakarta.inject.Provider<T>>> collectionOfJakartaProvidersOf(
      TypeLiteral<T> elementType) {
    Type providerType =
        Types.newParameterizedType(jakarta.inject.Provider.class, elementType.getType());
    Type type = Types.collectionOf(providerType);
    return (TypeLiteral<Collection<jakarta.inject.Provider<T>>>) TypeLiteral.get(type);
  }

  @SuppressWarnings("unchecked")
  static <T> TypeLiteral<Set<? extends T>> setOfExtendsOf(TypeLiteral<T> elementType) {
    Type extendsType = Types.subtypeOf(elementType.getType());
    Type setOfExtendsType = Types.setOf(extendsType);
    return (TypeLiteral<Set<? extends T>>) TypeLiteral.get(setOfExtendsType);
  }

  private final BindingSelection<T> bindingSelection;
  private final Binder binder;

  RealMultibinder(Binder binder, Key<T> key) {
    this.binder = checkNotNull(binder, "binder");
    this.bindingSelection = new BindingSelection<>(key);
  }

  @SuppressWarnings({"unchecked", "rawtypes"}) // we use raw Key to link bindings together.
  @Override
  public void configure(Binder binder) {
    checkConfiguration(!bindingSelection.isInitialized(), "Multibinder was already initialized");

    // Bind the setKey to the provider wrapped w/ extension support.
    binder
        .bind(bindingSelection.getSetKey())
        .toProvider(new RealMultibinderProvider<>(bindingSelection));
    binder.bind(bindingSelection.getSetOfExtendsKey()).to(bindingSelection.getSetKey());

    binder
        .bind(bindingSelection.getCollectionOfProvidersKey())
        .toProvider(new RealMultibinderCollectionOfProvidersProvider<T>(bindingSelection));

    // The collection this exposes is internally an ImmutableList, so it's OK to massage
    // the guice Provider to jakarta Provider in the value (since the guice Provider implements
    // jakarta Provider).
    binder
        .bind(bindingSelection.getCollectionOfJakartaProvidersKey())
        .to((Key) bindingSelection.getCollectionOfProvidersKey());
  }

  public void permitDuplicates() {
    binder.install(new PermitDuplicatesModule(bindingSelection.getPermitDuplicatesKey()));
  }

  /** Adds a new entry to the set and returns the key for it. */
  Key<T> getKeyForNewItem() {
    checkConfiguration(!bindingSelection.isInitialized(), "Multibinder was already initialized");
    return Key.get(
        bindingSelection.getElementTypeLiteral(),
        new RealElement(bindingSelection.getSetName(), MULTIBINDER, ""));
  }

  public LinkedBindingBuilder<T> addBinding() {
    return binder.bind(getKeyForNewItem());
  }

  // These methods are used by RealMapBinder

  Key<Set<T>> getSetKey() {
    return bindingSelection.getSetKey();
  }

  TypeLiteral<T> getElementTypeLiteral() {
    return bindingSelection.getElementTypeLiteral();
  }

  String getSetName() {
    return bindingSelection.getSetName();
  }

  boolean permitsDuplicates(Injector injector) {
    return bindingSelection.permitsDuplicates(injector);
  }

  boolean containsElement(com.google.inject.spi.Element element) {
    return bindingSelection.containsElement(element);
  }

  /**
   * Base implement of {@link InternalProviderInstanceBindingImpl.Factory} that works based on a
   * {@link BindingSelection}, allowing provider instances for various bindings to be implemented
   * with less duplication.
   */
  private abstract static class BaseFactory<ValueT, ProvidedT>
      extends InternalProviderInstanceBindingImpl.Factory<ProvidedT> {
    final BindingSelection<ValueT> bindingSelection;
    private boolean initialized = false;

    BaseFactory(BindingSelection<ValueT> bindingSelection) {
      // While Multibinders only depend on bindings created in modules so we could theoretically
      // initialize eagerly, they also depend on
      // 1. findBindingsByType returning results
      // 2. being able to call BindingImpl.acceptTargetVisitor
      // neither of those is available during eager initialization, so we use DELAYED
      super(InitializationTiming.DELAYED);
      this.bindingSelection = bindingSelection;
    }

    @Override
    void initialize(InjectorImpl injector, Errors errors) throws ErrorsException {
      if (initialized) {
        return;
      }
      bindingSelection.initialize(injector, errors);
      doInitialize();
      initialized = true;
    }

    abstract void doInitialize();

    @Override
    public final Provider<ProvidedT> makeProvider(InjectorImpl injector, Dependency<?> dependency) {
      // The !initialized case typically means that an error was reported during initialization
      // so use a trivial implementation
      if (initialized) {
        return doMakeProvider(injector, dependency);
      }
      return super.makeProvider(injector, dependency);
    }

    protected abstract Provider<ProvidedT> doMakeProvider(
        InjectorImpl injector, Dependency<?> dependency);

    @Override
    public boolean equals(Object obj) {
      return getClass().isInstance(obj)
          && bindingSelection.equals(((BaseFactory<?, ?>) obj).bindingSelection);
    }

    @Override
    public int hashCode() {
      return bindingSelection.hashCode();
    }
  }

  /**
   * Provider instance implementation that provides the actual set of values. This is parameterized
   * so it can be used to supply a {@code Set<T>} and {@code Set<? extends T>}, the latter being
   * useful for Kotlin support.
   */
  private static final class RealMultibinderProvider<T> extends BaseFactory<T, Set<T>>
      implements ProviderWithExtensionVisitor<Set<T>>, MultibinderBinding<Set<T>> {
    List<Binding<T>> bindings;
    SingleParameterInjector<T>[] injectors;
    boolean permitDuplicates;

    RealMultibinderProvider(BindingSelection<T> bindingSelection) {
      super(bindingSelection);
    }

    @Override
    public ImmutableSet<Dependency<?>> getDependencies() {
      return bindingSelection.getDependencies();
    }

    @Override
    protected void doInitialize() {
      bindings = bindingSelection.getBindings();
      injectors = bindingSelection.getParameterInjectors();
      permitDuplicates = bindingSelection.permitsDuplicates();
    }

    @Override
    protected ImmutableSet<T> doProvision(InternalContext context, Dependency<?> dependency)
        throws InternalProvisionException {
      SingleParameterInjector<T>[] localInjectors = injectors;
      if (localInjectors == null) {
        // if localInjectors == null, then we have no bindings so return the empty set.
        return ImmutableSet.of();
      }

      // If duplicates aren't permitted, we need to capture the original values in order to show a
      // meaningful error message to users (if duplicates were encountered).
      @SuppressWarnings("unchecked")
      T[] values = !permitDuplicates ? (T[]) new Object[localInjectors.length] : null;

      // Avoid ImmutableSet.copyOf(T[]), because it assumes there'll be duplicates in the input, but
      // in the usual case of permitDuplicates==false, we know the exact size must be
      // `localInjector.length` (otherwise we fail).  This uses `builderWithExpectedSize` to avoid
      // the overhead of copyOf or an unknown builder size. If permitDuplicates==true, this will
      // assume a potentially larger size (but never a smaller size), and `build` will then reduce
      // as necessary.
      ImmutableSet.Builder<T> setBuilder =
          ImmutableSet.<T>builderWithExpectedSize(localInjectors.length);
      for (int i = 0; i < localInjectors.length; i++) {
        SingleParameterInjector<T> parameterInjector = localInjectors[i];
        T newValue = parameterInjector.inject(context);
        if (newValue == null) {
          throw newNullEntryException(i);
        }
        if (!permitDuplicates) {
          values[i] = newValue;
        }
        setBuilder.add(newValue);
      }
      ImmutableSet<T> set = setBuilder.build();

      // There are fewer items in the set than the array.  Figure out which one got dropped.
      if (!permitDuplicates && set.size() < values.length) {
        throw newDuplicateValuesException(values);
      }
      return set;
    }

    @Override
    protected MethodHandle doGetHandle(LinkageContext context) {
      if (injectors == null) {
        return InternalMethodHandles.constantFactoryGetHandle(ImmutableSet.of());
      }
      // null check each element
      List<MethodHandle> elementHandles = new ArrayList<>(injectors.length);
      for (int i = 0; i < injectors.length; i++) {
        var element = injectors[i].getInjectHandle(context);
        elementHandles.add(
            MethodHandles.filterReturnValue(
                element,
                MethodHandles.insertArguments(
                    NULL_CHECK_RESULT_HANDLE, 1, bindings.get(i).getSource())));
      }
      // At size one permitDuplicates is irrelevant and we can bind to the SingletonImmutableSet
      // class directly.
      if (permitDuplicates || elementHandles.size() == 1) {
        // we just want to construct an ImmutableSet.Builder, and add everything to it.
        // This generates exactly the code a human would write.
        return MethodHandles.dropArguments(
            InternalMethodHandles.buildImmutableSetFactory(elementHandles), 1, Dependency.class);
      } else {
        // Duplicates are not permitted, so we need to check for duplicates by constructing all
        // elements and then checking the size of the set.
        // ()-> Object[]
        var elements = InternalMethodHandles.buildObjectArrayFactory(elementHandles);
        // (Object[]) -> ImmutableSet<T>
        var collector = MAKE_IMMUTABLE_SET_AND_CHECK_DUPLICATES_HANDLE.bindTo(this);
        // (InternalContext ctx) -> ImmutableSet<T>
        collector = MethodHandles.filterArguments(collector, 0, elements);
        // (InternalContext, Dependency) -> Object
        return MethodHandles.dropArguments(castReturnToObject(collector), 1, Dependency.class);
      }
    }

    /**
     * Recursive helper to populate an array.
     *
     * <p>REturns a handle of type (Object[], InternalContext) -> void
     */
    static MethodHandle populateArray(
        MethodHandle arrayElementSetter, int offset, List<MethodHandle> elementFactories) {
      var size = elementFactories.size();
      checkState(size != 0);
      if (size < 32) {
        var setter =
            MethodHandles.filterArguments(
                MethodHandles.insertArguments(arrayElementSetter, 1, offset),
                1,
                elementFactories.get(0));
        for (int i = 1; i < size; i++) {
          setter =
              MethodHandles.foldArguments(
                  MethodHandles.filterArguments(
                      MethodHandles.insertArguments(arrayElementSetter, 1, offset + i),
                      1,
                      elementFactories.get(i)),
                  setter);
        }
        return setter;
      }
      var left = elementFactories.subList(0, size / 2);
      var right = elementFactories.subList(size / 2, size);
      var leftHandle = populateArray(arrayElementSetter, offset, left);
      var rightHandle = populateArray(arrayElementSetter, offset + left.size(), right);
      return MethodHandles.foldArguments(rightHandle, leftHandle);
    }

    private static final MethodHandle MAKE_IMMUTABLE_SET_AND_CHECK_DUPLICATES_HANDLE =
        InternalMethodHandles.findVirtualOrDie(
            RealMultibinderProvider.class,
            "makeImmutableSetAndCheckDuplicates",
            methodType(ImmutableSet.class, Object[].class));

    @Keep
    ImmutableSet<T> makeImmutableSetAndCheckDuplicates(T[] elements)
        throws InternalProvisionException {
      // Avoid ImmutableSet.copyOf(T[]), because it assumes there'll be duplicates in the input, but
      // in the usual case of permitDuplicates==false, we know the exact size must be
      // `localInjector.length` (otherwise we fail).  This uses `builderWithExpectedSize` to avoid
      // the overhead of copyOf or an unknown builder size.
      var set = ImmutableSet.<T>builderWithExpectedSize(elements.length).add(elements).build();
      if (set.size() < elements.length) {
        throw newDuplicateValuesException(elements);
      }
      return set;
    }

    private static final MethodHandle NULL_CHECK_RESULT_HANDLE =
        InternalMethodHandles.findStaticOrDie(
            RealMultibinderProvider.class,
            "nullCheckResult",
            methodType(Object.class, Object.class, Object.class));

    @Keep
    static Object nullCheckResult(Object result, Object source) throws InternalProvisionException {
      if (result == null) {
        throw InternalProvisionException.create(
            ErrorId.NULL_ELEMENT_IN_SET,
            "Set injection failed due to null element bound at: %s",
            source);
      }
      return result;
    }

    @Override
    protected Provider<Set<T>> doMakeProvider(InjectorImpl injector, Dependency<?> dependency) {
      if (injectors == null) {
        return InternalFactory.makeProviderFor(ImmutableSet.of(), this);
      }
      return InternalFactory.makeDefaultProvider(this, injector, dependency);
    }

    private InternalProvisionException newNullEntryException(int i) {
      return InternalProvisionException.create(
          ErrorId.NULL_ELEMENT_IN_SET,
          "Set injection failed due to null element bound at: %s",
          bindings.get(i).getSource());
    }

    private InternalProvisionException newDuplicateValuesException(T[] values) {
      Message message =
          new Message(
              GuiceInternal.GUICE_INTERNAL,
              ErrorId.DUPLICATE_ELEMENT,
              new DuplicateElementError<T>(
                  bindingSelection.getSetKey(), bindings, values, ImmutableList.of(getSource())));
      return new InternalProvisionException(message);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <B, V> V acceptExtensionVisitor(
        BindingTargetVisitor<B, V> visitor, ProviderInstanceBinding<? extends B> binding) {
      if (visitor instanceof MultibindingsTargetVisitor) {
        return ((MultibindingsTargetVisitor<Set<T>, V>) visitor).visit(this);
      } else {
        return visitor.visit(binding);
      }
    }

    @Override
    public Key<Set<T>> getSetKey() {
      return bindingSelection.getSetKey();
    }

    @Override
    public ImmutableSet<Key<?>> getAlternateSetKeys() {
      return ImmutableSet.of(
          (Key<?>) bindingSelection.getCollectionOfProvidersKey(),
          (Key<?>) bindingSelection.getCollectionOfJakartaProvidersKey(),
          (Key<?>) bindingSelection.getSetOfExtendsKey());
    }

    @Override
    public TypeLiteral<?> getElementTypeLiteral() {
      return bindingSelection.getElementTypeLiteral();
    }

    @Override
    public List<Binding<?>> getElements() {
      return bindingSelection.getElements();
    }

    @Override
    public boolean permitsDuplicates() {
      return bindingSelection.permitsDuplicates();
    }

    @Override
    public boolean containsElement(com.google.inject.spi.Element element) {
      return bindingSelection.containsElement(element);
    }
  }

  /**
   * Implementation of BaseFactory that exposes a collection of providers of the values in the set.
   */
  private static final class RealMultibinderCollectionOfProvidersProvider<T>
      extends BaseFactory<T, Collection<Provider<T>>> {
    ImmutableList<Provider<T>> providers;

    RealMultibinderCollectionOfProvidersProvider(BindingSelection<T> bindingSelection) {
      super(bindingSelection);
    }

    @Override
    public ImmutableSet<Dependency<?>> getDependencies() {
      return bindingSelection.getProviderDependencies();
    }

    @Override
    protected void doInitialize() {
      ImmutableList.Builder<Provider<T>> providers = ImmutableList.builder();
      for (Binding<T> binding : bindingSelection.getBindings()) {
        providers.add(binding.getProvider());
      }
      this.providers = providers.build();
    }

    @Override
    protected ImmutableList<Provider<T>> doProvision(
        InternalContext context, Dependency<?> dependency) {
      return providers;
    }

    @Override
    protected Provider<Collection<Provider<T>>> doMakeProvider(
        InjectorImpl injector, Dependency<?> dependency) {
      return InternalFactory.makeProviderFor(providers, this);
    }

    @Override
    protected MethodHandle doGetHandle(LinkageContext contex) {
      return InternalMethodHandles.constantFactoryGetHandle(providers);
    }
  }

  private static final class BindingSelection<T> {
    // prior to initialization we declare just a dependency on the injector, but as soon as we are
    // initialized we swap to dependencies on the elements.
    private static final ImmutableSet<Dependency<?>> MODULE_DEPENDENCIES =
        ImmutableSet.<Dependency<?>>of(Dependency.get(Key.get(Injector.class)));
    private final TypeLiteral<T> elementType;
    private final Key<Set<T>> setKey;

    // these are all lazily allocated
    private String setName;
    private Key<Collection<Provider<T>>> collectionOfProvidersKey;
    private Key<Collection<jakarta.inject.Provider<T>>> collectionOfJakartaProvidersKey;
    private Key<Set<? extends T>> setOfExtendsKey;
    private Key<Boolean> permitDuplicatesKey;

    private boolean isInitialized;
    /* a binding for each element in the set. null until initialization, non-null afterwards */
    private ImmutableList<Binding<T>> bindings;

    // Starts out as Injector and gets set up properly after initialization
    private ImmutableSet<Dependency<?>> dependencies = MODULE_DEPENDENCIES;
    private ImmutableSet<Dependency<?>> providerDependencies = MODULE_DEPENDENCIES;

    /** whether duplicates are allowed. Possibly configured by a different instance */
    private boolean permitDuplicates;

    private SingleParameterInjector<T>[] parameterinjectors;

    BindingSelection(Key<T> key) {
      this.setKey = key.ofType(setOf(key.getTypeLiteral()));
      this.elementType = key.getTypeLiteral();
    }

    void initialize(InjectorImpl injector, Errors errors) throws ErrorsException {
      // This will be called multiple times, once by each Factory. We only want
      // to do the work to initialize everything once, so guard this code with
      // isInitialized.
      if (isInitialized) {
        return;
      }
      List<Binding<T>> bindings = Lists.newArrayList();
      Set<Indexer.IndexedBinding> index = Sets.newHashSet();
      Indexer indexer = new Indexer(injector);
      List<Dependency<?>> dependencies = Lists.newArrayList();
      List<Dependency<?>> providerDependencies = Lists.newArrayList();
      for (Binding<?> entry : injector.findBindingsByType(elementType)) {
        if (keyMatches(entry.getKey())) {
          @SuppressWarnings("unchecked") // protected by findBindingsByType()
          Binding<T> binding = (Binding<T>) entry;
          if (index.add(binding.acceptTargetVisitor(indexer))) {
            // TODO(lukes): most of these are linked bindings since user bindings are linked to
            // a user binding through the @Element annotation.  Since this is an implementation
            // detail we could 'dereference' the @Element if it is a LinkedBinding and avoid
            // provisioning through the FactoryProxy at runtime.
            // Ditto for OptionalBinder/MapBinder
            bindings.add(binding);
            Key<T> key = binding.getKey();
            // TODO(lukes): we should mark this as a non-nullable dependency since we don't accept
            // null.
            // Add a dependency on Key<T>
            dependencies.add(Dependency.get(key));
            // and add a dependency on Key<Provider<T>>
            providerDependencies.add(
                Dependency.get(key.ofType(Types.providerOf(key.getTypeLiteral().getType()))));
          }
        }
      }

      this.bindings = ImmutableList.copyOf(bindings);
      this.dependencies = ImmutableSet.copyOf(dependencies);
      this.providerDependencies = ImmutableSet.copyOf(providerDependencies);
      this.permitDuplicates = permitsDuplicates(injector);
      // This is safe because all our dependencies are assignable to T and we never assign to
      // elements of this array.
      @SuppressWarnings("unchecked")
      SingleParameterInjector<T>[] typed =
          (SingleParameterInjector<T>[]) injector.getParametersInjectors(dependencies, errors);
      this.parameterinjectors = typed;
      isInitialized = true;
    }

    boolean permitsDuplicates(Injector injector) {
      return injector.getBindings().containsKey(getPermitDuplicatesKey());
    }

    ImmutableList<Binding<T>> getBindings() {
      checkConfiguration(isInitialized(), "not initialized");
      return bindings;
    }

    SingleParameterInjector<T>[] getParameterInjectors() {
      checkConfiguration(isInitialized(), "not initialized");
      return parameterinjectors;
    }

    ImmutableSet<Dependency<?>> getDependencies() {
      return dependencies;
    }

    ImmutableSet<Dependency<?>> getProviderDependencies() {
      return providerDependencies;
    }

    String getSetName() {
      // lazily initialized since most selectors don't survive module installation.
      if (setName == null) {
        setName = Annotations.nameOf(setKey);
      }
      return setName;
    }

    Key<Boolean> getPermitDuplicatesKey() {
      Key<Boolean> local = permitDuplicatesKey;
      if (local == null) {
        local =
            permitDuplicatesKey = Key.get(Boolean.class, named(toString() + " permits duplicates"));
      }
      return local;
    }

    Key<Collection<Provider<T>>> getCollectionOfProvidersKey() {
      Key<Collection<Provider<T>>> local = collectionOfProvidersKey;
      if (local == null) {
        local = collectionOfProvidersKey = setKey.ofType(collectionOfProvidersOf(elementType));
      }
      return local;
    }

    Key<Collection<jakarta.inject.Provider<T>>> getCollectionOfJakartaProvidersKey() {
      Key<Collection<jakarta.inject.Provider<T>>> local = collectionOfJakartaProvidersKey;
      if (local == null) {
        local =
            collectionOfJakartaProvidersKey =
                setKey.ofType(collectionOfJakartaProvidersOf(elementType));
      }
      return local;
    }

    Key<Set<? extends T>> getSetOfExtendsKey() {
      Key<Set<? extends T>> local = setOfExtendsKey;
      if (local == null) {
        local = setOfExtendsKey = setKey.ofType(setOfExtendsOf(elementType));
      }
      return local;
    }

    boolean isInitialized() {
      return isInitialized;
    }

    // MultibinderBinding API methods

    TypeLiteral<T> getElementTypeLiteral() {
      return elementType;
    }

    Key<Set<T>> getSetKey() {
      return setKey;
    }

    @SuppressWarnings("unchecked")
    List<Binding<?>> getElements() {
      if (isInitialized()) {
        return (List<Binding<?>>) (List<?>) bindings; // safe because bindings is immutable.
      } else {
        throw new UnsupportedOperationException("getElements() not supported for module bindings");
      }
    }

    boolean permitsDuplicates() {
      if (isInitialized()) {
        return permitDuplicates;
      } else {
        throw new UnsupportedOperationException(
            "permitsDuplicates() not supported for module bindings");
      }
    }

    boolean containsElement(com.google.inject.spi.Element element) {
      if (element instanceof Binding) {
        Binding<?> binding = (Binding<?>) element;
        return keyMatches(binding.getKey())
            || binding.getKey().equals(getPermitDuplicatesKey())
            || binding.getKey().equals(setKey)
            || binding.getKey().equals(collectionOfProvidersKey)
            || binding.getKey().equals(collectionOfJakartaProvidersKey)
            || binding.getKey().equals(setOfExtendsKey);
      } else {
        return false;
      }
    }

    private boolean keyMatches(Key<?> key) {
      return key.getTypeLiteral().equals(elementType)
          && key.getAnnotation() instanceof Element
          && ((Element) key.getAnnotation()).setName().equals(getSetName())
          && ((Element) key.getAnnotation()).type() == MULTIBINDER;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof BindingSelection) {
        return setKey.equals(((BindingSelection<?>) obj).setKey);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return setKey.hashCode();
    }

    @Override
    public String toString() {
      return (getSetName().isEmpty() ? "" : getSetName() + " ")
          + "Multibinder<"
          + elementType
          + ">";
    }
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof RealMultibinder
        && ((RealMultibinder<?>) o).bindingSelection.equals(bindingSelection);
  }

  @Override
  public int hashCode() {
    return bindingSelection.hashCode();
  }

  /**
   * We install the permit duplicates configuration as its own binding, all by itself. This way, if
   * only one of a multibinder's users remember to call permitDuplicates(), they're still permitted.
   *
   * <p>This is like setting a global variable in the injector so that each instance of the
   * multibinder will have the same value for permitDuplicates, even if it is only set on one of
   * them.
   */
  private static class PermitDuplicatesModule extends AbstractModule {
    private final Key<Boolean> key;

    PermitDuplicatesModule(Key<Boolean> key) {
      this.key = key;
    }

    @Override
    protected void configure() {
      bind(key).toInstance(true);
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof PermitDuplicatesModule && ((PermitDuplicatesModule) o).key.equals(key);
    }

    @Override
    public int hashCode() {
      return getClass().hashCode() ^ key.hashCode();
    }
  }
}
