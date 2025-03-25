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
import static com.google.common.truth.Truth.assertThat;
import static com.google.inject.name.Names.named;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.inject.Asserts;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.InjectionPoint;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
          public String get(InternalContext context, Dependency<?> dependency, boolean linked)
              throws InternalProvisionException {
            throw new AssertionError();
          }

          @Override
          @SuppressWarnings("ReferenceEquality")
          public MethodHandle getHandle(
              LinkageContext context, Dependency<?> dependency, boolean linked) {

            // Identity is intentional here.
            checkArgument(dep == dependency);
            checkArgument(!linked);
            return MethodHandles.dropArguments(
                MethodHandles.insertArguments(
                    MethodHandles.throwException(
                        dependency.getKey().getTypeLiteral().getRawType(),
                        InternalProvisionException.class),
                    0,
                    InternalProvisionException.create(ErrorId.OPTIONAL_CONSTRUCTOR, "Hello World")),
                0,
                InternalContext.class);
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

  @Test
  public void providerMaker_makesAScopedProvider() {
    InjectorImpl injector = (InjectorImpl) Guice.createInjector();
    InternalFactory<String> factory =
        new InternalFactory<String>() {
          @Override
          public String get(InternalContext context, Dependency<?> dependency, boolean linked)
              throws InternalProvisionException {
            throw new AssertionError();
          }

          @Override
          public MethodHandle getHandle(
              LinkageContext context, Dependency<?> dependency, boolean linked) {
            return InternalMethodHandles.constantFactoryGetHandle(
                dependency, dependency.toString());
          }
        };
    Provider<String> provider =
        InternalMethodHandles.makeScopedProvider(factory, injector, Key.get(String.class));

    // When no dependency is set, the implementation uses the key from its constructor.
    assertThat(provider.get()).isEqualTo("Key[type=java.lang.String, annotation=[none]]");
    try (InternalContext ctx = injector.enterContext()) {
      var dep = Dependency.get(Key.get(String.class, named("a")));
      ctx.setDependency(dep);
      assertThat(provider.get()).isEqualTo(dep.toString());

      dep = Dependency.get(Key.get(String.class, named("b")));
      ctx.setDependency(dep);
      assertThat(provider.get()).isEqualTo(dep.toString());
    }
  }

  @Test
  public void testConstantFactoryGetHandle() throws Throwable {
    var handle = InternalMethodHandles.constantFactoryGetHandle(String.class, "Hello World");
    assertThat((String) handle.invokeExact((InternalContext) null)).isEqualTo("Hello World");
  }

  @Test
  public void testInitialiableFactoryGetHandle() throws Throwable {
    var handle =
        InternalMethodHandles.initializableFactoryGetHandle(
            ctx -> "Hello World", Dependency.get(Key.get(String.class)));
    assertThat((String) handle.invokeExact((InternalContext) null)).isEqualTo("Hello World");
  }

  @Test
  public void testInitialiableFactoryGetHandle_onlyCalledOnce() throws Throwable {
    AtomicInteger callCount = new AtomicInteger(0);
    var handle =
        InternalMethodHandles.initializableFactoryGetHandle(
            ctx -> {
              callCount.incrementAndGet();
              return "Hello World";
            },
            Dependency.get(Key.get(String.class)));
    assertThat((String) handle.invokeExact((InternalContext) null)).isEqualTo("Hello World");
    assertThat(callCount.get()).isEqualTo(1);

    assertThat((String) handle.invokeExact((InternalContext) null)).isEqualTo("Hello World");
    assertThat(callCount.get()).isEqualTo(1);
  }

  @Test
  public void testInitialiableFactoryGetHandle_calledMultipleTimesWhenThrowing() throws Throwable {
    AtomicInteger callCount = new AtomicInteger(0);
    var handle =
        InternalMethodHandles.initializableFactoryGetHandle(
            ctx -> {
              callCount.incrementAndGet();
              if (callCount.get() < 3) {
                throw InternalProvisionException.cannotProxyClass(String.class);
              }
              return "Hello World";
            },
            Dependency.get(Key.get(String.class)));
    assertThrows(
        InternalProvisionException.class,
        () -> {
          var unused = (String) handle.invokeExact((InternalContext) null);
        });
    assertThat(callCount.get()).isEqualTo(1);

    assertThrows(
        InternalProvisionException.class,
        () -> {
          var unused = (String) handle.invokeExact((InternalContext) null);
        });
    assertThat(callCount.get()).isEqualTo(2);
    assertThat((String) handle.invokeExact((InternalContext) null)).isEqualTo("Hello World");
    assertThat(callCount.get()).isEqualTo(3);
    assertThat((String) handle.invokeExact((InternalContext) null)).isEqualTo("Hello World");
    assertThat(callCount.get()).isEqualTo(3);
  }

  private static final class TestClass {
    @Inject
    @SuppressWarnings("unused")
    TestClass(String s) {}
  }

  @Test
  public void testNullCheckResult() throws Throwable {
    var nonNullStringDep =
        Iterables.getOnlyElement(
            Dependency.forInjectionPoints(
                ImmutableSet.of(InjectionPoint.forConstructorOf(TestClass.class))));
    var handle =
        InternalMethodHandles.nullCheckResult(
            InternalMethodHandles.constantFactoryGetHandle(String.class, "Hello World"),
            "source",
            nonNullStringDep);
    assertThat((String) handle.invokeExact((InternalContext) null)).isEqualTo("Hello World");
    var nullHandle =
        InternalMethodHandles.nullCheckResult(
            InternalMethodHandles.constantFactoryGetHandle(String.class, null),
            "source",
            nonNullStringDep);
    var e =
        assertThrows(
            InternalProvisionException.class,
            () -> {
              var unused = (String) nullHandle.invokeExact((InternalContext) null);
            });
  }

  @Test
  public void buildImmutableSetFactory_empty() throws Throwable {
    MethodHandle handle = InternalMethodHandles.buildImmutableSetFactory(ImmutableList.of());
    assertThat(handle.type()).isEqualTo(InternalMethodHandles.makeFactoryType(ImmutableSet.class));
    assertThat(handle.invoke(null)).isEqualTo(ImmutableSet.of());
  }

  @Test
  public void buildImmutableSetFactory_singleton() throws Throwable {
    MethodHandle handle =
        InternalMethodHandles.buildImmutableSetFactory(
            ImmutableList.of(InternalMethodHandles.constantFactoryGetHandle(String.class, "a")));
    assertThat(handle.type()).isEqualTo(InternalMethodHandles.makeFactoryType(ImmutableSet.class));
    assertThat(handle.invoke(null)).isEqualTo(ImmutableSet.of("a"));
  }

  @Test
  public void buildImmutableSetFactory_manyMaintainsOrder() throws Throwable {
    MethodHandle handle =
        InternalMethodHandles.buildImmutableSetFactory(
            ImmutableList.of(
                InternalMethodHandles.constantFactoryGetHandle(String.class, "a"),
                InternalMethodHandles.constantFactoryGetHandle(String.class, "b"),
                InternalMethodHandles.constantFactoryGetHandle(String.class, "c")));
    assertThat(handle.type()).isEqualTo(InternalMethodHandles.makeFactoryType(ImmutableSet.class));
    assertThat((ImmutableSet) handle.invoke(null))
        .containsExactlyElementsIn(ImmutableSet.of("a", "b", "c"))
        .inOrder();
  }

  @Test
  public void buildImmutableMap_empty() throws Throwable {
    MethodHandle handle = InternalMethodHandles.buildImmutableMapFactory(ImmutableList.of());
    assertThat(handle.type()).isEqualTo(InternalMethodHandles.makeFactoryType(ImmutableMap.class));
    assertThat(handle.invoke(null)).isEqualTo(ImmutableMap.of());
  }

  @Test
  public void buildImmutableMap_singleton() throws Throwable {
    MethodHandle handle =
        InternalMethodHandles.buildImmutableMapFactory(
            ImmutableList.of(
                Map.entry("a", InternalMethodHandles.constantFactoryGetHandle(String.class, "a"))));
    assertThat(handle.type()).isEqualTo(InternalMethodHandles.makeFactoryType(ImmutableMap.class));
    assertThat(handle.invoke(null)).isEqualTo(ImmutableMap.of("a", "a"));
  }

  @Test
  public void buildImmutableMap_manyMaintainsOrder() throws Throwable {
    MethodHandle handle =
        InternalMethodHandles.buildImmutableMapFactory(
            ImmutableList.of(
                Map.entry("a", InternalMethodHandles.constantFactoryGetHandle(String.class, "a")),
                Map.entry("b", InternalMethodHandles.constantFactoryGetHandle(String.class, "b")),
                Map.entry("c", InternalMethodHandles.constantFactoryGetHandle(String.class, "c"))));
    assertThat(handle.type()).isEqualTo(InternalMethodHandles.makeFactoryType(ImmutableMap.class));
    assertThat(handle.invoke(null)).isEqualTo(ImmutableMap.of("a", "a", "b", "b", "c", "c"));
  }

  @Test
  public void buildImmutableMap_rejectsDuplicateKeys() throws Throwable {
    MethodHandle handle =
        InternalMethodHandles.buildImmutableMapFactory(
            ImmutableList.of(
                Map.entry("a", InternalMethodHandles.constantFactoryGetHandle(String.class, "a")),
                Map.entry("a", InternalMethodHandles.constantFactoryGetHandle(String.class, "b"))));
    assertThat(handle.type()).isEqualTo(InternalMethodHandles.makeFactoryType(ImmutableMap.class));
    var e = assertThrows(IllegalArgumentException.class, () -> handle.invoke(null));
    assertThat(e).hasMessageThat().contains("Multiple entries with same key");
  }
}
