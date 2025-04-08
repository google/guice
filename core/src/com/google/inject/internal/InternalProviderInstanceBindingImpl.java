package com.google.inject.internal;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.HasDependencies;
import com.google.inject.spi.InjectionPoint;
import java.lang.invoke.MethodHandle;

/**
 * A {@link ProviderInstanceBindingImpl} for implementing 'native' guice extensions.
 *
 * <p>Beyond the normal binding contract that is mostly handled by our baseclass, this also
 * implements {@link DelayedInitialize} in order to initialize factory state.
 */
final class InternalProviderInstanceBindingImpl<T> extends ProviderInstanceBindingImpl<T>
    implements DelayedInitialize {
  enum InitializationTiming {
    /** This factory can be initialized eagerly. This should be the case for most things. */
    EAGER,

    /**
     * Initialization of this factory should be delayed until after all other static initialization
     * completes. This will be useful for factories that need to call {@link
     * InjectorImpl#getExistingBinding(Key)} to not create jit bindings, but also want to be able to
     * conditionally consume jit bindings created by other other bindings.
     */
    DELAYED;
  }

  private final Factory<T> originalFactory;

  InternalProviderInstanceBindingImpl(
      InjectorImpl injector,
      Key<T> key,
      Object source,
      Factory<T> originalFactory,
      InternalFactory<? extends T> scopedFactory,
      Scoping scoping) {
    super(
        injector,
        key,
        source,
        scopedFactory,
        scoping,
        // Pass the original factory as the provider instance. A number of checks rely on being able
        // to downcast this provider to access the original factory.
        originalFactory,
        ImmutableSet.<InjectionPoint>of());
    this.originalFactory = originalFactory;
  }

  InitializationTiming getInitializationTiming() {
    return originalFactory.initializationTiming;
  }

  @Override
  public void initialize(final InjectorImpl injector, final Errors errors) throws ErrorsException {
    originalFactory.source = getSource();
    originalFactory.provisionCallback = injector.provisionListenerStore.get(this);
    originalFactory.initialize(injector, errors);
    checkState(
        injector == originalFactory.injector,
        "Factory should have already been bound to this injector.");
    checkState(
        injector == this.getInjector(),
        "Binding should be initialized on the same injector that created it.");
    originalFactory.dependency = Dependency.get(getKey());
  }

  /** A base factory implementation. */
  abstract static class Factory<T> extends InternalFactory<T>
      implements Provider<T>, HasDependencies, ProvisionListenerStackCallback.ProvisionCallback<T> {
    private final InitializationTiming initializationTiming;
    private Object source;
    private InjectorImpl injector;
    private Dependency<?> dependency;
    ProvisionListenerStackCallback<T> provisionCallback;

    Factory(InitializationTiming initializationTiming) {
      this.initializationTiming = initializationTiming;
    }

    /**
     * Exclusively binds this factory to the given injector.
     *
     * <p>This is needed since the implementations of this class are used to construct bindings via
     * `bind(key).toProvider(factory-instance)` and the BindingProcessor tests for this type to find
     * the 'native factory' implementation. This works well but is a bit ambiguous since users can
     * also bind these providers to _other_ injectors which can create some confusion as to how
     * 'initialization' works.
     *
     * <p>Thus, the binding processor uses this method to make an 'exclusive' claim on the factory
     * and prevent other injectors from also binding to it. We synchronize this method but not all
     * read/writes to `injector` since binding initialization is a single threaded process, we just
     * need to prevent multiple injectors from racing on the claim.
     */
    synchronized boolean bindToInjector(InjectorImpl injector) {
      if (this.injector == null) {
        this.injector = injector;
        return true;
      }
      return false;
    }

    /**
     * The binding source.
     *
     * <p>May be useful for augmenting runtime error messages.
     *
     * <p>Note: this will return {@code null} until {@link #initialize(InjectorImpl, Errors)} has
     * already been called.
     */
    final Object getSource() {
      return source;
    }

    /**
     * A callback that allows for implementations to fetch dependencies on other bindings.
     *
     * <p>Will be called exactly once, prior to any call to {@link #doProvision}.
     */
    abstract void initialize(InjectorImpl injector, Errors errors) throws ErrorsException;

    @Override
    public final T get() {
      var localInjector = injector;
      var localDependency = dependency;
      // Check both of these to ensure that we have been 'bound' via `bindToinjector` and
      // `initialize` has been called on our Binding.
      if (localInjector == null || localDependency == null) {
        throw new IllegalStateException(
            "This Provider cannot be used until the Injector has been created and this binding has"
                + " been initialized.");
      }
      // This is an inlined version of InternalFactory.makeDefaultProvider
      try (InternalContext context = localInjector.enterContext()) {
        return get(context, localDependency, false);
      } catch (InternalProvisionException e) {
        throw e.addSource(localDependency).toProvisionException();
      }
    }

    @Override
    public T get(final InternalContext context, final Dependency<?> dependency, boolean linked)
        throws InternalProvisionException {
      if (provisionCallback == null) {
        return doProvision(context, dependency);
      } else {
        return provisionCallback.provision(context, dependency, this);
      }
    }

    @Override
    MethodHandleResult makeHandle(LinkageContext context, boolean linked) {
      return makeCachable(
          InternalMethodHandles.invokeThroughProvisionCallback(
              doGetHandle(context), provisionCallback));
    }

    // Implements ProvisionCallback<T>
    @Override
    public final T call(InternalContext context, Dependency<?> dependency)
        throws InternalProvisionException {
      return doProvision(context, dependency);
    }

    /**
     * Creates an object to be injected.
     *
     * @throws com.google.inject.internal.InternalProvisionException if a value cannot be provided
     * @return instance to be injected
     */
    protected abstract T doProvision(InternalContext context, Dependency<?> dependency)
        throws InternalProvisionException;

    /** Creates a method handle that constructs the object to be injected. */
    protected abstract MethodHandle doGetHandle(LinkageContext context);
  }
}
