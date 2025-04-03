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

import com.google.common.base.MoreObjects;
import com.google.inject.Provider;
import com.google.inject.spi.Dependency;

/**
 * @author crazybob@google.com (Bob Lee)
 */
final class ConstantFactory<T> extends InternalFactory<T> {

  private static <T> InternalFactory<T> nullFactory(Object source) {
    return new InternalFactory<T>() {
      @Override
      public T get(InternalContext context, Dependency<?> dependency, boolean linked)
          throws InternalProvisionException {
        if (!dependency.isNullable()) {
          InternalProvisionException.onNullInjectedIntoNonNullableDependency(source, dependency);
        }
        return null;
      }

      @Override
      public Provider<T> makeProvider(InjectorImpl injector, Dependency<?> dependency) {
        return InternalFactory.makeProviderForNull(source, this, dependency);
      }

      @Override
      MethodHandleResult makeHandle(LinkageContext context, boolean linked) {
        var returnNull = InternalMethodHandles.constantFactoryGetHandle(null);
        return makeCachable(InternalMethodHandles.nullCheckResult(returnNull, source));
      }
    };
  }

  private final T instance;

  static <T> InternalFactory<T> create(T instance, Object source) {
    if (instance == null) {
      return nullFactory(source);
    }
    return new ConstantFactory<>(instance);
  }

  private ConstantFactory(T instance) {
    this.instance = instance;
  }

  @Override
  public T get(InternalContext context, Dependency<?> dependency, boolean linked) {
    return instance;
  }

  @Override
  public Provider<T> makeProvider(InjectorImpl injector, Dependency<?> dependency) {
    return InternalFactory.makeProviderFor(instance, this);
  }

  @Override
  MethodHandleResult makeHandle(LinkageContext context, boolean linked) {
    return makeCachable(InternalMethodHandles.constantFactoryGetHandle(instance));
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(ConstantFactory.class).add("value", instance).toString();
  }
}
