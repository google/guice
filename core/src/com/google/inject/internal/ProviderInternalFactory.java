/*
 * Copyright (C) 2011 Google Inc.
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
import static com.google.inject.internal.InternalMethodHandles.findVirtualOrDie;
import static java.lang.invoke.MethodType.methodType;

import com.google.inject.Key;
import com.google.inject.internal.InjectorImpl.InjectorOptions;
import com.google.inject.spi.Dependency;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import javax.annotation.Nullable;
import jakarta.inject.Provider;

/**
 * Base class for InternalFactories that are used by Providers, to handle circular dependencies.
 *
 * @author sameb@google.com (Sam Berlin)
 */
abstract class ProviderInternalFactory<T> implements InternalFactory<T> {
  protected static final Dependency<?> PROVIDER_DEPENDENCY =
      Dependency.get(new Key<jakarta.inject.Provider<?>>() {});
  protected final Object source;
  private final int circularFactoryId;

  ProviderInternalFactory(Object source, int circularFactoryId) {
    this.source = checkNotNull(source, "source");
    this.circularFactoryId = circularFactoryId;
  }

  protected T circularGet(
      final Provider<? extends T> provider,
      InternalContext context,
      final Dependency<?> dependency,
      @Nullable ProvisionListenerStackCallback<T> provisionCallback)
      throws InternalProvisionException {
    @SuppressWarnings("unchecked")
    T result = (T) context.tryStartConstruction(circularFactoryId, dependency);
    if (result != null) {
      // We have a circular reference between bindings. Return a proxy.
      return result;
    }
    // Optimization: Don't go through the callback stack if no one's listening.
    if (provisionCallback == null) {
      return provision(provider, context, dependency);
    } else {
      return provisionCallback.provision(
          context, dependency, (ctx, dep) -> provision(provider, ctx, dep));
    }
  }

  /**
   * Returns the method handle for the object to be injected.
   *
   * <p>The signature of the method handle is {@code (InternalContext) -> T} where `T` is the type
   * of the object to be injected as determined by the {@link Dependency}.
   *
   * <p>Call this method directly when the {@code providerHandle} doesn't itself have provision
   * callbacks or failure modes. This is the case for constant provider bindings at least.
   */
  protected MethodHandle circularGetHandleImmediate(
      MethodHandle providerHandle,
      InjectorOptions options,
      Dependency<?> dependency,
      @Nullable ProvisionListenerStackCallback<T> provisionCallback) {

    var invokeProvider = provisionHandle(providerHandle, dependency);

    // Apply the provision callback if needed
    invokeProvider =
        InternalMethodHandles.invokeThroughProvisionCallback(
            invokeProvider, dependency, provisionCallback);
    // Start construction and possibly return a proxy.
    invokeProvider =
        InternalMethodHandles.tryStartConstruction(
            options, invokeProvider, dependency, circularFactoryId);
    return invokeProvider;
  }

  /**
   * Return s method handle for the object to be injected.
   *
   * <p>This takes care to construct the provider (via {@code providerHandle}) and then call get on
   * it, such that provision listeners get called in the correct order.
   */
  protected MethodHandle circularGetHandle(
      MethodHandle providerHandle,
      InjectorOptions options,
      Dependency<?> dependency,
      @Nullable ProvisionListenerStackCallback<T> provisionCallback) {
    // The combinators below assume this type.
    providerHandle =
        providerHandle.asType(providerHandle.type().changeReturnType(jakarta.inject.Provider.class));
    // TODO: lukes - This is annoying, but various tests assert that we invoke the provision
    // listener for the Provider and then for the thing being provided, but if we just called
    // `circularGetHandleImmediate` then the order would be reversed since the provision callback
    // would close over the provider handle.  That approach is actually faster and simpler but doing
    // it would change provision callbacks and exception traces.  Per the docs on ProvisionListener
    // having the listener around both the object being provisioned _and_ its dependencies is
    // expected.  It just isn't what ProviderInternalFactory.get currently does. Once MethodHandles
    // are more rolled out this could be revisited but for now we avoid changing listener ordering
    // for compatibility.

    // We cannot just pass the bound provider handle to circularGet since that will change
    // evaluation order. Instead we want to pass the provider object into the circularGet method.
    // We do this by creating a placeholder method handle that drops the InternalContext argument
    // and returns a Provider.
    var providerPlaceholder =
        MethodHandles.dropArguments(
            MethodHandles.identity(jakarta.inject.Provider.class), 0, InternalContext.class);
    // (InternalContext, Provider) ->T
    var invokeProvider =
        circularGetHandleImmediate(providerPlaceholder, options, dependency, provisionCallback);
    // To call `fold` we need to permute the arguments so that the provider is the first argument.
    // (Provider, InternalContext) ->T
    invokeProvider =
        MethodHandles.permuteArguments(
            invokeProvider,
            methodType(
                invokeProvider.type().returnType(),
                jakarta.inject.Provider.class,
                InternalContext.class),
            new int[] {1, 0});
    // Basically invoke the `providerHandle` and then pass the provider to `invokeProvider`
    var createProviderAndPass = MethodHandles.foldArguments(invokeProvider, 0, providerHandle);
    // Ensure we are respecting our type.
    return createProviderAndPass.asType(InternalMethodHandles.makeFactoryType(dependency));
  }

  private static final MethodHandle PROVIDER_GET_HANDLE =
      findVirtualOrDie(jakarta.inject.Provider.class, "get", methodType(Object.class));

  /**
   * Returns a method handle that calls {@code providerHandle.get()} and catches any
   * RuntimeException as an InternalProvisionException.
   *
   * <p>Subclasses should override this to add more validation checks.
   */
  protected MethodHandle provisionHandle(MethodHandle providerHandle, Dependency<?> dependency) {
    providerHandle =
        providerHandle.asType(providerHandle.type().changeReturnType(jakarta.inject.Provider.class));
    // Call Provider.get() and catch any RuntimeException as an InternalProvisionException.
    var invokeProvider =
        InternalMethodHandles.catchErrorInProviderAndRethrowWithSource(
            MethodHandles.filterReturnValue(providerHandle, PROVIDER_GET_HANDLE), source);
    // Wrap in a try..finally.. that calls `finishConstruction` on the context.
    invokeProvider = InternalMethodHandles.finishConstruction(invokeProvider, circularFactoryId);

    // null check the result using the dependency.
    invokeProvider = InternalMethodHandles.nullCheckResult(invokeProvider, source, dependency);
    return invokeProvider;
  }

  /**
   * Provisions a new instance. Subclasses should override this to catch exceptions and rethrow as
   * ErrorsExceptions.
   */
  protected T provision(
      Provider<? extends T> provider, InternalContext context, Dependency<?> dependency)
      throws InternalProvisionException {
    T t = null;
    try {
      t = provider.get();
    } catch (RuntimeException e) {
      throw InternalProvisionException.errorInProvider(e).addSource(source);
    } finally {
      context.finishConstruction(circularFactoryId, t);
    }
    if (t == null && !dependency.isNullable()) {
      InternalProvisionException.onNullInjectedIntoNonNullableDependency(source, dependency);
    }
    return t;
  }
}
