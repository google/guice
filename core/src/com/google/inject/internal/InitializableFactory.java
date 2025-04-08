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
import com.google.errorprone.annotations.concurrent.LazyInit;
import com.google.inject.Provider;
import com.google.inject.spi.Dependency;

/**
 * @author crazybob@google.com (Bob Lee)
 */
final class InitializableFactory<T> extends InternalFactory<T> {

  private final Initializable<T> initializable;
  // Cache the values here so we can optimize the behavior of Provider instances.
  // We do not use a lock but rather a volatile field to safely publish values.  This means we
  // might compute the value multiple times, but we rely on internal synchronization inside the
  // singleton scope to ensure that we only compute the value once.
  // See https://github.com/google/guice/issues/1802 for more details.
  @LazyInit private volatile T value;

  public InitializableFactory(Initializable<T> initializable) {
    this.initializable = initializable;
  }

  @Override
  public T get(InternalContext context, Dependency<?> dependency, boolean linked)
      throws InternalProvisionException {
    // NOTE: we do not need to check nullishness here because Initializables never contain nulls.
    return initializable.get(context);
  }

  @Override
  MethodHandleResult makeHandle(LinkageContext context, boolean linked) {
    return makeCachable(InternalMethodHandles.initializableFactoryGetHandle(initializable));
  }

  @Override
  public Provider<T> makeProvider(InjectorImpl injector, Dependency<?> dependency) {

    var value = this.value;
    if (value != null) {
      return InternalFactory.makeProviderFor(value, this);
    }

    return new Provider<T>() {
      @Override
      public T get() {
        // Avoid calling `enterContext` when we can.
        var value = InitializableFactory.this.value;
        if (value != null) {
          return value;
        }
        try (InternalContext context = injector.enterContext()) {
          value = initializable.get(context);
          InitializableFactory.this.value = value;
          return value;
        } catch (InternalProvisionException e) {
          throw e.addSource(dependency).toProvisionException();
        }
      }

      @Override
      public String toString() {
        return InitializableFactory.this.toString();
      }
    };
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(InitializableFactory.class)
        .add("value", initializable)
        .toString();
  }
}
