/*
 * Copyright (C) 2025 Google Inc.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.inject.Asserts;
import com.google.inject.Guice;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.spi.Dependency;
import java.lang.ref.WeakReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class InternalMethodHandlesTest {

  @Test
  public void providerMaker_makesAProvider() {
    InjectorImpl injector = (InjectorImpl) Guice.createInjector();
    InternalFactory<String> factory = ConstantFactory.create("Hello World", "[a source]");
    Provider<String> provider =
        InternalMethodHandles.makeProvider(
            factory, injector, Dependency.get(Key.get(String.class)));

    assertThat(provider.get()).isEqualTo("Hello World");
    assertThat(provider).isInstanceOf(InternalMethodHandles.GeneratedProvider.class);
  }

  @Test
  public void providerMaker_makesAProvider_throwsExceptions() {
    InjectorImpl injector = (InjectorImpl) Guice.createInjector();
    var dep = Dependency.get(Key.get(String.class));
    InternalFactory<String> factory =
        new InternalFactory<String>() {
          @Override
          @SuppressWarnings("ReferenceEquality")
          public String get(InternalContext context, Dependency<?> dependency, boolean linked)
              throws InternalProvisionException {
            checkNotNull(context);
            // Identity is intentional here.
            checkArgument(dep == dependency);
            checkArgument(!linked);
            throw InternalProvisionException.create(ErrorId.OPTIONAL_CONSTRUCTOR, "Hello World");
          }
        };
    Provider<String> provider = InternalMethodHandles.makeProvider(factory, injector, dep);

    var pe = assertThrows(ProvisionException.class, provider::get);
    assertThat(pe).hasMessageThat().contains("Hello World");
  }

  @Test
  public void providerMaker_doesntPinTheInjector() {
    InjectorImpl injector = (InjectorImpl) Guice.createInjector();
    var dep = Dependency.get(Key.get(String.class));
    InternalFactory<String> factory = makeFactoryCapturingInjector(injector);
    var injectorRef = new WeakReference<>(injector);
    var depRef = new WeakReference<>(dep);
    var factoryRef = new WeakReference<>(factory);

    var unused = InternalMethodHandles.makeProvider(factory, injector, dep);
    unused = null;
    factory = null;
    injector = null;
    dep = null;
    Asserts.awaitClear(injectorRef);
    Asserts.awaitClear(depRef);
    Asserts.awaitClear(factoryRef);
  }

  private static InternalFactory<String> makeFactoryCapturingInjector(InjectorImpl injector) {
    return new InternalFactory<String>() {
      // This is a reference to the injector that is captured by the factory.
      @SuppressWarnings("unused")
      final InjectorImpl injectorCapture = injector;

      @Override
      public String get(InternalContext context, Dependency<?> dependency, boolean linked) {
        throw new AssertionError();
      }
    };
  }
}
