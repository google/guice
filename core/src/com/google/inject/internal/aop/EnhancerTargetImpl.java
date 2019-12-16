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

import com.google.inject.internal.BytecodeGen.EnhancerTarget;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * {@link EnhancerTarget} that also tracks bridge delegation in the host class.
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
final class EnhancerTargetImpl implements EnhancerTarget {

  private final Class<?> hostClass;

  private final Method[] enhanceableMethods;

  private final Map<Method, Method> bridgeDelegates;

  EnhancerTargetImpl(
      Class<?> hostClass, List<Method> enhanceableMethods, Map<Method, Method> bridgeDelegates) {

    this.hostClass = hostClass;
    this.enhanceableMethods = enhanceableMethods.toArray(new Method[enhanceableMethods.size()]);
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

  /** Returns the delegate targeted by the bridge method; {@code null} if no delegate exists. */
  public Method getBridgeDelegate(Method bridgeMethod) {
    return bridgeDelegates.get(bridgeMethod);
  }
}
