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

import static com.google.common.base.Preconditions.checkState;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MutableCallSite;
import java.util.IdentityHashMap;
import java.util.function.Supplier;

/**
 * Linkage context allows circular factories to bind to themselves recursively when needed.
 *
 * <p>Guice can resolve cycles at provisioning time via the `InternalContext` protocol, this
 * provides similar functionality at 'linkage-time' which is when we are building MethodHandles. If
 * an InternalFactory delegates partially to another InternalFactory, then it is possible to link
 * back to itself. This class provides a way to detect that and resolve the cycle by returning a
 * lazily resolved MethodHandle that points back to instance generated by the first invocation.
 */
final class LinkageContext {

  private static final Object CONSTRUCTING = new Object();
  // Values are either `CONSTRUCTING` or a `MutableCallSite` instance.
  private final IdentityHashMap<InternalFactory<?>, Object> linkingFactories =
      new IdentityHashMap<>();

  /**
   * Creates a MethodHandle for a 'circular' factory in a way that resolve cycles.
   *
   * <p>If constructing a MethodHandle requires invoking other InternalFactories, then it is
   * possible that there will be a cycle. This method will detect that and resolve it by returning a
   * handle that calls the originally constructed handle.
   *
   * <p>In theory this can lead to us generating a MethodHandle that will always throw a
   * StackOverFlowError, but the callers should call this in places where there is runtime cycle
   * detection. Finally, we could also potentially resolve the cycle with a MethodHandle that always
   * throws and `InternalProvisionException`, but it isn't clear that that is sufficient and would
   * only be reasonable in places that we cannot resovle cycles with proxies, and those cases have
   * already been well optimized, so the only actual win would be if we could eliminate all runtime
   * cycle detection but it is not clear that that is even feasible.
   *
   * @param source the factory that is calling this method
   * @param factory a supplier of the method handle to be invoked
   * @return a method handle that will invoke the given factory, resolving cycles as needed, using
   *     the {@link InternalFactory#FACTORY_TYPE} signature.
   */
  MethodHandle makeHandle(InternalFactory<?> source, Supplier<MethodHandle> factory) {
    var previous = linkingFactories.putIfAbsent(source, CONSTRUCTING);
    if (previous == CONSTRUCTING) {
      // We are the first to 're-enter' this factory, so we need to create a MutableCallSite that
      // will be used to resolve the cycle.
      previous = new MutableCallSite(InternalMethodHandles.FACTORY_TYPE);
      linkingFactories.put(source, previous);
    }
    if (previous instanceof MutableCallSite) {
      // We are re-entering the same factory, so we can just return the dynamic invoker and rely on
      // the original invocation to finish the construction and call setTarget to finalize the
      // callsite.
      return ((MutableCallSite) previous).dynamicInvoker();
    } else {
      checkState(previous == null, "Unexpected previous value: %s", previous);
    }
    MethodHandle handle = factory.get();
    previous = linkingFactories.remove(source);
    checkState(previous != null, "construction state was cleared already?");
    if (previous != CONSTRUCTING) {
      var callSite = (MutableCallSite) previous;
      callSite.setTarget(handle);
      MutableCallSite.syncAll(new MutableCallSite[] {callSite});
    }
    return handle;
  }
}
