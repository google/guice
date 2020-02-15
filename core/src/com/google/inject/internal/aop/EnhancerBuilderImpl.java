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

import static com.google.inject.internal.aop.ClassBuilding.signature;

import com.google.inject.internal.BytecodeGen;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Builder of enhancers that provide method interception via bytecode generation.
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
final class EnhancerBuilderImpl implements BytecodeGen.EnhancerBuilder {

  private final Class<?> hostClass;

  private final Method[] enhanceableMethods;

  private final Map<Method, Method> bridgeDelegates;

  EnhancerBuilderImpl(
      Class<?> hostClass, List<Method> enhanceableMethods, Map<Method, Method> bridgeDelegates) {

    this.hostClass = hostClass;
    this.enhanceableMethods = enhanceableMethods.toArray(new Method[enhanceableMethods.size()]);
    this.bridgeDelegates = bridgeDelegates;
  }

  @Override
  public Method[] getEnhanceableMethods() {
    return enhanceableMethods;
  }

  @Override
  public Function<String, ?> buildEnhancer(Constructor<?> constructor, BitSet methodIndices) {

    Map<String, Method> enhancedMethodMap = new TreeMap<>();

    for (int methodIndex = methodIndices.nextSetBit(0);
        methodIndex >= 0;
        methodIndex = methodIndices.nextSetBit(methodIndex + 1)) {
      Method method = enhanceableMethods[methodIndex];
      enhancedMethodMap.put(signature(method), method);
    }

    // return new Enhancer(hostClass, constructor, enhancedMethodMap, bridgeDelegates);
    return signature -> null; // TODO: GLUE
  }
}
