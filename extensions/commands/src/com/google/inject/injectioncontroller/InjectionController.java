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
import com.google.inject.commands.intercepting.ProvisionInterceptor;
import com.google.inject.internal.Objects;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Allows bound objects to be substituted at runtime. To use:
 *
 * <ol><li>Create a module that binds {@link ProvisionInterceptor} to the
 * result of {@link #getProvisionInterceptor()} in the desired scope. Or use
 * {@link #createModule()} which binds it with no scope.</li>
 * <li>Create an {@code InterceptingInjectorBuilder} that installs your
 * application modules, plus the module from the previous step.</li>
 * <li>Configure the builder to intercept each type that you would like to
 * substitute.</li>
 * <li>Build the injector. Whenever the injector needs an instance of a
 * controlled type, the injection controller will disregard that binding if a
 * value for that type as been substituted.</li></ul>
 *
 * <pre>
 * InjectionController injectionController = new InjectionController();
 *
 * Injector injector = new InterceptingInjectorBuilder()
 *     .install(new MyApplicationModule(), injectionController.createModule());
 *     .intercept(PersistenceEngine.class)
 *     .intercept(DeliveryRequestService.class)
 *     .build();
 *
 * injectionController.substitute(PersistenceEngine.class, new MockPersistenceEngine());
 * </pre>
 *
 * @author jessewilson@google.com (Jesse Wilson)
 * @author jmourits@google.com (Jerome Mourits)
 */
public class InjectionController {
  private final Map<Key<?>, Object> mapWritable = new HashMap<Key<?>, Object>();
  private final Map<Key<?>, Object> map = Collections.unmodifiableMap(mapWritable);

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
  public ProvisionInterceptor getProvisionInterceptor() {
    return provisionInterceptor;
  }

  /**
   * Returns a module that binds the provision interceptor without a scope.
   */
  public final Module createModule() {
    return new AbstractModule() {
      protected void configure() {
        bind(ProvisionInterceptor.class)
            .toInstance(provisionInterceptor);
      }
    };
  }

  /**
   * Substitutes the injector's existing binding for {@code key} with
   * {@code instance}.
   */
  public <T> InjectionController substitute(Key<T> key, T instance) {
    Objects.nonNull(key, "key");

    if (map.containsKey(key)) {
      throw new IllegalStateException(key + " was already being doubled.");
    }

    mapWritable.put(key, instance);
    return this;
  }

  /**
   * Substitutes the injector's existing binding for {@code type} with
   * {@code instance}.
   */
  public <T> InjectionController substitute(Class<T> type, T instance) {
    return substitute(Key.get(type), instance);
  }

  /**
   * Restores the original binding for {@code key}.
   */
  public <T> InjectionController remove(Key<T> key) {
    Objects.nonNull(key, "key");

    if (!map.containsKey(key)) {
      throw new IllegalStateException(key + " was not being doubled.");
    }

    mapWritable.remove(key);
    return this;
  }

  /**
   * Restores the original binding for {@code type}.
   */
  public <T> InjectionController remove(Class<T> type) {
    return remove(Key.get(type));
  }

  /**
   * Returns an unmodifiable, mutable map with the substituted bindings.
   */
  public Map<Key<?>, Object> getSubstitutesMap() {
    return map;
  }
}
