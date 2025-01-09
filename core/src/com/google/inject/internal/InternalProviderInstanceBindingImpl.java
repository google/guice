package com.google.inject.internal;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.internal.ProvisionListenerStackCallback.ProvisionCallback;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.HasDependencies;
import com.google.inject.spi.InjectionPoint;

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
    // For these kinds of providers, the 'user supplied provider' is really 'guice supplied'
    // So make our user supplied provider just delegate to the guice supplied one.
    originalFactory.delegateProvider = getProvider();
    originalFactory.initialize(injector, errors);
  }

  /** A base factory implementation. */
  abstract static class Factory<T> implements InternalFactory<T>, Provider<T>, HasDependencies {
    private final InitializationTiming initializationTiming;
    private Object source;
    private Provider<T> delegateProvider;
    ProvisionListenerStackCallback<T> provisionCallback;

    Factory(InitializationTiming initializationTiming) {
      this.initializationTiming = initializationTiming;
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
      Provider<T> local = delegateProvider;
      if (local == null) {
        throw new IllegalStateException(
            "This Provider cannot be used until the Injector has been created.");
      }
      return local.get();
    }

    @Override
    public T get(final InternalContext context, final Dependency<?> dependency, boolean linked)
        throws InternalProvisionException {
      if (provisionCallback == null) {
        return doProvision(context, dependency);
      } else {
        return provisionCallback.provision(
            context,
            new ProvisionCallback<T>() {
              @Override
              public T call() throws InternalProvisionException {
                return doProvision(context, dependency);
              }
            });
      }
    }
    /**
     * Creates an object to be injected.
     *
     * @throws com.google.inject.internal.InternalProvisionException if a value cannot be provided
     * @return instance to be injected
     */
    protected abstract T doProvision(InternalContext context, Dependency<?> dependency)
        throws InternalProvisionException;
  }
}
