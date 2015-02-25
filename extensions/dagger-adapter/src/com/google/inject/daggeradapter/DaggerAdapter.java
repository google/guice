/**
 * Copyright (C) 2015 Google Inc.
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
package com.google.inject.daggeradapter;

import com.google.common.base.Objects;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.internal.ProviderMethodsModule;
import com.google.inject.spi.ModuleAnnotatedMethodScanner;

import java.util.Arrays;

/**
 * A utility to adapt classes annotated with {@link @dagger.Module} such that their
 * {@link @dagger.Provides} methods can be properly invoked by Guice to perform their
 * provision operations.
 *
 * <p>Simple example: <pre>{@code
 *   Guice.createInjector(...other modules..., DaggerAdapter.from(new SomeDaggerAdapter()));
 * }</pre>
 *
 * <p>Some notes on usage and compatibility.
 *   <ul>
 *     <li>Dagger provider methods have a "SET_VALUES" provision mode not supported by Guice.
 *     <li>MapBindings are not yet implemented (pending).
 *     <li>Be careful about stateful modules. In contrast to Dagger (where components are
 *         expected to be recreated on-demand with new Module instances), Guice typically
 *         has a single injector with a long lifetime, so your module instance will be used
 *         throughout the lifetime of the entire app.
 *     <li>Dagger 1.x uses {@link @Singleton} for all scopes, including shorter-lived scopes
 *         like per-request or per-activity.  Using modules written with Dagger 1.x usage
 *         in mind may result in mis-scoped objects.
 *     <li>Dagger 2.x supports custom scope annotations, but for use in Guice, a custom scope
 *         implementation must be registered in order to support the custom lifetime of that
 *         annotation.
 *   </ul>
 *
 * @author cgruber@google.com (Christian Gruber)
 */
public final class DaggerAdapter {
  /**
   * Returns a guice module from a dagger module.
   *
   * <p>Note: At present, it does not honor {@code @Module(includes=...)} directives.
   */
  public static Module from(Object... daggerModuleObjects) {
    // TODO(cgruber): Gather injects=, dedupe, factor out instances, instantiate the rest, and go.
    return new DaggerCompatibilityModule(daggerModuleObjects);
  }

  /**
   * A Module that adapts Dagger {@code @Module}-annotated types to contribute configuration
   * to an {@link com.google.inject.Injector} using a dagger-specific
   * {@link ModuleAnnotatedMethodScanner}.
   */
  private static final class DaggerCompatibilityModule implements Module {
    private final Object[] daggerModuleObjects;

    private DaggerCompatibilityModule(Object... daggerModuleObjects) {
      this.daggerModuleObjects = daggerModuleObjects;
    }

    @Override public void configure(Binder binder) {
      for (Object module : daggerModuleObjects) {
        binder.install(ProviderMethodsModule.forModule(module, DaggerMethodScanner.INSTANCE));
      }
    }

    @Override public String toString() {
      return Objects.toStringHelper(this)
          .add("modules", Arrays.asList(daggerModuleObjects))
          .toString();
    }
  }

  private DaggerAdapter() {}
}
