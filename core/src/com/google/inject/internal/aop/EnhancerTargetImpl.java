/*
 * Copyright (C) 2019 Google Inc.
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

package com.google.inject.internal.aop;

import com.google.inject.internal.BytecodeGen;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * {@link EnhancerTarget} that also tracks bridge delegation in the host class.
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
final class EnhancerTargetImpl implements BytecodeGen.EnhancerTarget {

  private final Class<?> hostClass;

  private final Method[] enhanceableMethods;

  private final Map<Method, Method> originalBridges;

  private final Map<Method, Method> bridgeDelegates;

  EnhancerTargetImpl(
      Class<?> hostClass,
      List<Method> enhanceableMethods,
      Map<Method, Method> originalBridges,
      Map<Method, Method> bridgeDelegates) {

    this.hostClass = hostClass;
    this.enhanceableMethods = enhanceableMethods.toArray(new Method[enhanceableMethods.size()]);
    this.originalBridges = originalBridges;
    this.bridgeDelegates = bridgeDelegates;
  }

  @Override
  public Class<?> getHostClass() {
    return hostClass;
  }

  @Override
  public Method[] getEnhanceableMethods() {
    return enhanceableMethods;
  }

  /** Returns the original bridge for an enhanceable method; {@code null} if there's no bridge. */
  public Method getOriginalBridge(Method enhanceableMethod) {
    return originalBridges.get(enhanceableMethod);
  }

  /** Returns the bridge delegate for an enhanceable method; {@code null} if there's no delegate. */
  public Method getBridgeDelegate(Method enhanceableMethod) {
    return bridgeDelegates.get(enhanceableMethod);
  }
}
