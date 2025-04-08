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

import static com.google.common.truth.Truth.assertThat;
import static com.google.inject.internal.InternalMethodHandles.castReturnToObject;
import static java.lang.invoke.MethodType.methodType;
import static org.junit.Assert.assertThrows;

import com.google.errorprone.annotations.Keep;
import com.google.inject.Key;
import com.google.inject.spi.Dependency;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LinkageContextTest {
  private static final Dependency<?> DEP = Dependency.get(Key.get(String.class));

  private static InternalFactory<String> makeFactory(Supplier<MethodHandle> handle) {
    return new InternalFactory<String>() {

      @Override
      String get(InternalContext context, Dependency<?> dependency, boolean linked)
          throws InternalProvisionException {
        throw new UnsupportedOperationException("Unimplemented method 'get'");
      }

      @Override
      MethodHandleResult makeHandle(LinkageContext context, boolean linked) {
        return makeCachable(handle.get());
      }
    };
  }

  @Test
  public void testMakeHandle_returnsHandle() throws Throwable {
    LinkageContext context = new LinkageContext();
    var result =
        (Object)
            context
                .makeHandle(
                    makeFactory(
                        () -> InternalMethodHandles.constantFactoryGetHandle("Hello World")),
                    false)
                .methodHandle
                .invokeExact((InternalContext) null, (Dependency<?>) null);
    assertThat(result).isEqualTo("Hello World");
  }

  @Keep
  static String incrementAndReturn(
      InternalContext ignored, Dependency<?> ignored2, String s, int[] callCount) {
    callCount[0]++;
    return s;
  }

  private static final MethodHandle INCREMENT_AND_RETURN_HANDLE =
      InternalMethodHandles.findStaticOrDie(
          LinkageContextTest.class,
          "incrementAndReturn",
          methodType(
              String.class, InternalContext.class, Dependency.class, String.class, int[].class));

  // Demonstrate that a recursive call links ultimately to the same method handle of the initial
  // call.
  @Test
  public void testMakeHandle_resolvesCycles() throws Throwable {
    LinkageContext context = new LinkageContext();
    int[] callCount = new int[1];
    MethodHandle[] recursiveHandle = new MethodHandle[1];
    AtomicReference<InternalFactory<String>> factoryReference = new AtomicReference<>();
    var factory =
        makeFactory(
            () -> {
              if (recursiveHandle[0] != null) {
                throw new AssertionError();
              }
              recursiveHandle[0] = context.makeHandle(factoryReference.get(), false).methodHandle;
              return castReturnToObject(
                  MethodHandles.insertArguments(
                      INCREMENT_AND_RETURN_HANDLE, 2, "Hello World", callCount));
            });
    factoryReference.set(factory);
    MethodHandle handle = context.makeHandle(factory, false).methodHandle;
    assertThat((Object) handle.invokeExact((InternalContext) null, (Dependency<?>) null))
        .isEqualTo("Hello World");
    assertThat(callCount[0]).isEqualTo(1);
    assertThat((Object) handle.invokeExact((InternalContext) null, (Dependency<?>) null))
        .isEqualTo("Hello World");
    assertThat(callCount[0]).isEqualTo(2);

    // The recursive handle is linked to the same instance, just indirectly.
    assertThat(
            (Object) recursiveHandle[0].invokeExact((InternalContext) null, (Dependency<?>) null))
        .isEqualTo("Hello World");
    assertThat(callCount[0]).isEqualTo(3);
  }

  @Keep
  static String detectsCycle(InternalContext ctx, Dependency<?> ignored, int[] callCount)
      throws InternalProvisionException {
    callCount[0]++;
    var unused = ctx.tryStartConstruction(1, DEP);
    return "Hello World";
  }

  private static final MethodHandle DETECTS_CYCLE_HANDLE =
      InternalMethodHandles.findStaticOrDie(
          LinkageContextTest.class,
          "detectsCycle",
          methodType(String.class, InternalContext.class, Dependency.class, int[].class));

  @Test
  public void testMakeHandle_isRecursive() throws Throwable {
    LinkageContext context = new LinkageContext();
    int[] callCount = new int[1];
    AtomicReference<InternalFactory<String>> factoryReference = new AtomicReference<>();
    var factory =
        makeFactory(
            () -> {
              var recursiveHandle = context.makeHandle(factoryReference.get(), false).methodHandle;

              // This calls `detectsCycle` and then the recursive handle.
              return castReturnToObject(
                  MethodHandles.foldArguments(
                      recursiveHandle,
                      InternalMethodHandles.dropReturn(
                          MethodHandles.insertArguments(DETECTS_CYCLE_HANDLE, 2, callCount))));
            });
    factoryReference.set(factory);
    MethodHandle handle = context.makeHandle(factory, false).methodHandle;
    var ipe =
        assertThrows(
            InternalProvisionException.class,
            () -> {
              var unused =
                  (Object)
                      handle.invokeExact(
                          InternalContext.create(
                              /* disableCircularProxies= */ true, new Object[] {null}),
                          (Dependency<?>) null);
            });
    // It throws on the second call, so we should have called it twice.
    assertThat(callCount[0]).isEqualTo(2);
  }
}
