/*
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

package com.google.inject.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.inject.Provider;
import com.google.inject.spi.Dependency;
import java.lang.invoke.MethodHandle;

/**
 * Creates objects which will be injected.
 *
 * @author crazybob@google.com (Bob Lee)
 */
interface InternalFactory<T> {

  /**
   * Creates an object to be injected.
   *
   * @param context of this injection
   * @param linked true if getting as a result of a linked binding
   * @throws com.google.inject.internal.InternalProvisionException if a value cannot be provided
   * @return instance that was created
   */
  T get(InternalContext context, Dependency<?> dependency, boolean linked)
      throws InternalProvisionException;

  /** Returns a provider for the object to be injected. */
  default Provider<T> makeProvider(InjectorImpl injector, Dependency<?> dependency) {
    return makeDefaultProvider(this, injector, dependency);
  }

  /**
   * Returns the method handle for the object to be injected.
   *
   * <p>The signature of the method handle is {@code (InternalContext) -> T} where `T` is the type
   * of the object to be injected as determined by the {@link Dependency}.
   *
   * @param context the linkage context
   * @param linked true if getting as a result of a linked binding
   * @return the method handle for the object to be injected
   */
  abstract MethodHandle getHandle(LinkageContext context, boolean linked);

  /**
   * Returns a provider for the object to be injected using the given factory.
   *
   * <p>This delegates to the {@link #get} method.
   */
  static <T> Provider<T> makeDefaultProvider(
      InternalFactory<T> factory, InjectorImpl injector, Dependency<?> dependency) {
    if (InternalFlags.getUseExperimentalMethodHandlesOption()) {
      return InternalMethodHandles.makeProvider(factory, injector, dependency);
    }
    return new DefaultProvider<>(factory, injector, dependency);
  }

  /** Returns a provider for the object to be injected, handling nullable values. */
  static <T> Provider<T> makeProviderForNullable(
      T instance, InternalFactory<T> factory, Dependency<?> dependency, Object source) {
    if (instance == null) {
      return makeProviderForNull(source, factory, dependency);
    }
    return makeProviderFor(instance, factory);
  }

  /** Returns a provider for the object to be injected, handling literal {@code null} values. */
  static <T> Provider<T> makeProviderForNull(
      Object source, InternalFactory<T> factory, Dependency<?> dependency) {
    if (dependency.isNullable()) {
      return new InstanceProvider<>(null, factory);
    }
    return new NullProvider<>(source, factory, dependency);
  }

  /** Returns a provider for the object to be injected, handling non-nullable values. */
  static <T> Provider<T> makeProviderFor(T instance, InternalFactory<T> factory) {
    checkNotNull(instance);
    return new InstanceProvider<>(instance, factory);
  }

  static class DefaultProvider<T> implements Provider<T> {
    private final InternalFactory<T> factory;
    private final InjectorImpl injector;
    private final Dependency<?> dependency;

    DefaultProvider(InternalFactory<T> factory, InjectorImpl injector, Dependency<?> dependency) {
      this.factory = factory;
      this.injector = injector;
      this.dependency = dependency;
    }

    @Override
    public T get() {
      InternalContext currentContext = injector.enterContext();
      try {
        return factory.get(currentContext, dependency, false);
      } catch (InternalProvisionException e) {
        throw e.addSource(dependency).toProvisionException();
      } finally {
        currentContext.close();
      }
    }

    @Override
    public String toString() {
      return factory.toString();
    }
  }

  static class NullProvider<T> implements Provider<T> {
    private final Object source;
    private final InternalFactory<T> factory;
    private final Dependency<?> dependency;

    NullProvider(Object source, InternalFactory<T> factory, Dependency<?> dependency) {
      this.source = source;
      this.factory = factory;
      this.dependency = dependency;
    }

    @Override
    public T get() {
      try {
        InternalProvisionException.onNullInjectedIntoNonNullableDependency(source, dependency);
        return null;
      } catch (InternalProvisionException e) {
        throw e.addSource(dependency).toProvisionException();
      }
    }

    @Override
    public String toString() {
      return factory.toString();
    }
  }

  static class InstanceProvider<T> implements Provider<T> {
    private final T instance;
    private final InternalFactory<T> factory;

    InstanceProvider(T instance, InternalFactory<T> factory) {
      this.instance = instance;
      this.factory = factory;
    }

    @Override
    public T get() {
      return instance;
    }

    @Override
    public String toString() {
      return factory.toString();
    }
  }
}
