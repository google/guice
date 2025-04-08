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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.truth.Truth.assertThat;
import static java.lang.invoke.MethodType.methodType;
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
import java.util.stream.IntStream;
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
    assertThat(provider).isInstanceOf(InternalMethodHandles.MethodHandleProvider.class);
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
          MethodHandleResult makeHandle(LinkageContext context, boolean linked) {

            checkArgument(!linked);
            return makeCachable(
                MethodHandles.dropArguments(
                    MethodHandles.insertArguments(
                        MethodHandles.throwException(
                            Object.class, InternalProvisionException.class),
                        0,
                        InternalProvisionException.create(
                            ErrorId.OPTIONAL_CONSTRUCTOR, "Hello World")),
                    0,
                    InternalContext.class,
                    Dependency.class));
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

      @Override
      MethodHandleResult makeHandle(LinkageContext context, boolean linked) {
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
          MethodHandleResult makeHandle(LinkageContext context, boolean linked) {
            return makeCachable(InternalMethodHandles.constantFactoryGetHandle("Hello world"));
          }
        };
    Provider<String> provider = InternalMethodHandles.makeScopedProvider(factory, injector);
    assertThat(provider).isInstanceOf(ProviderToInternalFactoryAdapter.class);

    assertThat(provider.get()).isEqualTo("Hello world");
  }

  @Test
  public void testConstantFactoryGetHandle() throws Throwable {
    var handle = InternalMethodHandles.constantFactoryGetHandle("Hello World");
    assertThat((Object) handle.invokeExact((InternalContext) null, (Dependency<?>) null))
        .isEqualTo("Hello World");
  }

  @Test
  public void testInitialiableFactoryGetHandle() throws Throwable {
    var handle = InternalMethodHandles.initializableFactoryGetHandle(ctx -> "Hello World");
    assertThat((Object) handle.invokeExact((InternalContext) null, (Dependency<?>) null))
        .isEqualTo("Hello World");
  }

  @Test
  public void testInitialiableFactoryGetHandle_onlyCalledOnce() throws Throwable {
    AtomicInteger callCount = new AtomicInteger(0);
    var handle =
        InternalMethodHandles.initializableFactoryGetHandle(
            ctx -> {
              callCount.incrementAndGet();
              return "Hello World";
            });
    assertThat((Object) handle.invokeExact((InternalContext) null, (Dependency<?>) null))
        .isEqualTo("Hello World");
    assertThat(callCount.get()).isEqualTo(1);

    assertThat((Object) handle.invokeExact((InternalContext) null, (Dependency<?>) null))
        .isEqualTo("Hello World");
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
            });
    assertThrows(
        InternalProvisionException.class,
        () -> {
          var unused = (Object) handle.invokeExact((InternalContext) null, (Dependency<?>) null);
        });
    assertThat(callCount.get()).isEqualTo(1);

    assertThrows(
        InternalProvisionException.class,
        () -> {
          var unused = (Object) handle.invokeExact((InternalContext) null, (Dependency<?>) null);
        });
    assertThat(callCount.get()).isEqualTo(2);
    assertThat((Object) handle.invokeExact((InternalContext) null, (Dependency<?>) null))
        .isEqualTo("Hello World");
    assertThat(callCount.get()).isEqualTo(3);
    assertThat((Object) handle.invokeExact((InternalContext) null, (Dependency<?>) null))
        .isEqualTo("Hello World");
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
            InternalMethodHandles.constantFactoryGetHandle("Hello World"), "source");
    assertThat((Object) handle.invokeExact((InternalContext) null, nonNullStringDep))
        .isEqualTo("Hello World");
    var nullHandle =
        InternalMethodHandles.nullCheckResult(
            InternalMethodHandles.constantFactoryGetHandle(null), "source");
    var e =
        assertThrows(
            InternalProvisionException.class,
            () -> {
              var unused =
                  (Object) nullHandle.invokeExact((InternalContext) null, nonNullStringDep);
            });
  }

  @Test
  public void buildImmutableSetFactory_empty() throws Throwable {
    MethodHandle handle = InternalMethodHandles.buildImmutableSetFactory(ImmutableList.of());
    assertThat(handle.type()).isEqualTo(InternalMethodHandles.ELEMENT_FACTORY_TYPE);
    assertThat(handle.invoke(null)).isEqualTo(ImmutableSet.of());
  }

  @Test
  public void buildImmutableSetFactory_singleton() throws Throwable {
    MethodHandle handle =
        InternalMethodHandles.buildImmutableSetFactory(
            ImmutableList.of(InternalMethodHandles.constantElementFactoryGetHandle("a")));
    assertThat(handle.type()).isEqualTo(InternalMethodHandles.ELEMENT_FACTORY_TYPE);
    assertThat(handle.invoke(null)).isEqualTo(ImmutableSet.of("a"));
  }

  @Test
  public void buildImmutableSetFactory_manyMaintainsOrder() throws Throwable {
    // check through the MAX_BINDABLE_ARITY
    for (int size = 2; size < 256; size++) {
      MethodHandle handle =
          InternalMethodHandles.buildImmutableSetFactory(
              IntStream.range(0, size)
                  .mapToObj(i -> InternalMethodHandles.constantElementFactoryGetHandle("a" + i))
                  .collect(toImmutableList()));
      assertThat(handle.type()).isEqualTo(InternalMethodHandles.ELEMENT_FACTORY_TYPE);
      assertThat((ImmutableSet) handle.invoke(null))
          .containsExactlyElementsIn(
              IntStream.range(0, size).mapToObj(i -> "a" + i).collect(toImmutableList()))
          .inOrder();
    }
  }

  @Test
  public void buildImmutableMap_empty() throws Throwable {
    MethodHandle handle = InternalMethodHandles.buildImmutableMapFactory(ImmutableList.of());
    assertThat(handle.type()).isEqualTo(InternalMethodHandles.ELEMENT_FACTORY_TYPE);
    assertThat(handle.invoke(null)).isEqualTo(ImmutableMap.of());
  }

  @Test
  public void buildImmutableMap_singleton() throws Throwable {
    MethodHandle handle =
        InternalMethodHandles.buildImmutableMapFactory(
            ImmutableList.of(
                Map.entry("a", InternalMethodHandles.constantElementFactoryGetHandle("a"))));
    assertThat(handle.type()).isEqualTo(InternalMethodHandles.ELEMENT_FACTORY_TYPE);
    assertThat(handle.invoke(null)).isEqualTo(ImmutableMap.of("a", "a"));
  }

  @Test
  public void buildImmutableMap_manyMaintainsOrder() throws Throwable {
    // check through the MAX_BINDABLE_ARITY
    for (int size = 2; size < 256; size++) {
      var entries =
          IntStream.range(0, size)
              .mapToObj(
                  i ->
                      Map.entry(
                          "a" + i, InternalMethodHandles.constantElementFactoryGetHandle("a" + i)))
              .collect(toImmutableList());
      MethodHandle handle = InternalMethodHandles.buildImmutableMapFactory(entries);
      assertThat(handle.type()).isEqualTo(InternalMethodHandles.ELEMENT_FACTORY_TYPE);
      var expected =
          IntStream.range(0, size).boxed().collect(toImmutableMap(i -> "a" + i, i -> "a" + i));
      assertThat(handle.invoke(null)).isEqualTo(expected);
    }
  }

  @Test
  public void buildImmutableMap_rejectsDuplicateKeys() throws Throwable {
    MethodHandle handle =
        InternalMethodHandles.buildImmutableMapFactory(
            ImmutableList.of(
                Map.entry("a", InternalMethodHandles.constantElementFactoryGetHandle("a")),
                Map.entry("a", InternalMethodHandles.constantElementFactoryGetHandle("b"))));
    assertThat(handle.type()).isEqualTo(InternalMethodHandles.ELEMENT_FACTORY_TYPE);
    var e = assertThrows(IllegalArgumentException.class, () -> handle.invoke(null));
    assertThat(e).hasMessageThat().contains("Multiple entries with same key");
  }

  @Test
  public void buildObjectArrayFactory_empty() throws Throwable {
    MethodHandle handle = InternalMethodHandles.buildObjectArrayFactory(ImmutableList.of());
    assertThat(handle.type()).isEqualTo(methodType(Object[].class, InternalContext.class));
    assertThat((Object[]) handle.invokeExact((InternalContext) null)).hasLength(0);
    assertThat((Object[]) handle.invokeExact((InternalContext) null))
        .isSameInstanceAs((Object[]) handle.invokeExact((InternalContext) null));
  }

  @Test
  public void buildObjectArrayFactory_small() throws Throwable {
    MethodHandle handle =
        InternalMethodHandles.buildObjectArrayFactory(
            ImmutableList.of(
                InternalMethodHandles.constantElementFactoryGetHandle("a"),
                InternalMethodHandles.constantElementFactoryGetHandle("b")));
    assertThat(handle.type()).isEqualTo(methodType(Object[].class, InternalContext.class));
    assertThat(ImmutableList.copyOf((Object[]) handle.invokeExact((InternalContext) null)))
        .containsExactly("a", "b")
        .inOrder();
    assertThat((Object[]) handle.invokeExact((InternalContext) null))
        .isNotSameInstanceAs((Object[]) handle.invokeExact((InternalContext) null));
  }

  @Test
  public void buildObjectArrayFactory_large() throws Throwable {
    MethodHandle handle =
        InternalMethodHandles.buildObjectArrayFactory(
            IntStream.range(0, 100_000)
                .mapToObj(i -> InternalMethodHandles.constantElementFactoryGetHandle("i" + i))
                .collect(toImmutableList()));
    assertThat(handle.type()).isEqualTo(methodType(Object[].class, InternalContext.class));
    assertThat((Object[]) handle.invokeExact((InternalContext) null)).hasLength(100_000);
    assertThat((Object[]) handle.invokeExact((InternalContext) null))
        .isNotSameInstanceAs((Object[]) handle.invokeExact((InternalContext) null));
  }
}
