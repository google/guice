/**
 * Copyright (C) 2008 Google Inc.
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

package com.google.inject.injectioncontroller;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.visitable.intercepting.ProvisionInterceptor;

import java.util.HashMap;
import java.util.Map;

/**
 * Allows bound objects to be overridden at runtime.
 *
 * <p>To use, create your Injector using {@code InterceptingInjectorBuilder},
 * and include the module from {@link #getModule} to bind the controller's
 * injection interceptor. Configure the {@code InjectionInterceptorBuilder}
 * to intercept each type that you would like to control.
 *
 * <p>Whenever the injector needs an instance of a controlled type, the
 * injection controller will override the binding if an alternate value for
 * that type as been set.
 *
 * <pre>
 * InjectionController injectionController = new InjectionController();
 *
 * Injector injector = new InterceptingInjectorBuilder()
 *     .bindModules(new MyApplicationModule(), injectionController.getModule());
 *     .intercept(PersistenceEngine.class)
 *     .intercept(DeliveryRequestService.class)
 *     .build();
 *
 * injectionController.set(PersistenceEngine.class, new MockPersistenceEngine());
 * </pre>
 *
 * @author jessewilson@google.com (Jesse Wilson)
 * @author jmourits@google.com (Jerome Mourits)
 */
public class InjectionController {

  // TODO(jessewilson): make instances of this bindable in test scope

  private final Map<Key<?>, Object> map = new HashMap<Key<?>, Object>();

  private final ProvisionInterceptor provisionInterceptor = new ProvisionInterceptor() {
    @SuppressWarnings({"unchecked"})
    public <T> T intercept(Key<T> key, Provider<? extends T> delegate) {
      T mockT = (T) map.get(key);
      return (mockT == null)
          // This always happens in production
          ? delegate.get()
          // This will happen when running tests that "control" a <T>'s injection
          : mockT;
    }
  };

  /**
   * Returns the injection interceptor for binding
   */
  public ProvisionInterceptor getInjectionInterceptor() {
    return provisionInterceptor;
  }

  /**
   * Returns a module that binds the injection interceptor.
   */
  public final Module getModule() {
    return new AbstractModule() {
      protected void configure() {
        bind(ProvisionInterceptor.class)
            .toInstance(provisionInterceptor);
      }
    };
  }

  /**
   * <em>Never</em> call this method from production code, only from tests.
   *
   * <p>Setting a class into the {@link InjectionController} will allow for
   * controllable providers to alter their injection.
   */
  public <T> InjectionController set(Key<T> key, T instance) {
    if (instance == null) {
      if (!map.containsKey(key)) {
        throw new IllegalStateException(key + " was not being doubled.");
      }
      map.remove(key);

    } else {
      if (map.containsKey(key)) {
        throw new IllegalStateException(key + " was already being doubled.");
      }
      map.put(key, instance);
    }

    return this;
  }

  public <T> InjectionController set(Class<T> clazz, T instance) {
    return set(Key.get(clazz), instance);
  }

  /**
   * @VisibleForTesting
   */
  int size() {
    return map.size();
  }
}
