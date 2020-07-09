/*
 * Copyright (C) 2020 Google Inc.
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
import static com.google.inject.internal.aop.ClassBuilding.visitMembers;
import static com.google.inject.internal.aop.ClassDefining.hasPackageAccess;
import static java.lang.reflect.Modifier.FINAL;

import com.google.common.collect.ImmutableMap;
import com.google.inject.internal.BytecodeGen;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Builder of enhancers that provide method interception via bytecode generation.
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
final class EnhancerBuilderImpl implements BytecodeGen.EnhancerBuilder {

  /** Lazy association between classes and their generated enhancers. */
  private static final ClassValue<
          Map<BitSet, Function<String, BiFunction<Object, Object[], Object>>>>
      ENHANCERS =
          new ClassValue<Map<BitSet, Function<String, BiFunction<Object, Object[], Object>>>>() {
            @Override
            protected Map<BitSet, Function<String, BiFunction<Object, Object[], Object>>>
                computeValue(Class<?> hostClass) {
              return new HashMap<>();
            }
          };

  private final Class<?> hostClass;

  private final Method[] enhanceableMethods;

  private final Map<Method, Method> bridgeDelegates;

  EnhancerBuilderImpl(
      Class<?> hostClass,
      Collection<Method> enhanceableMethods,
      Map<Method, Method> bridgeDelegates) {

    this.hostClass = hostClass;
    this.enhanceableMethods = enhanceableMethods.toArray(new Method[0]);
    this.bridgeDelegates = ImmutableMap.copyOf(bridgeDelegates);
  }

  @Override
  public Method[] getEnhanceableMethods() {
    return enhanceableMethods;
  }

  @Override
  public Function<String, BiFunction<Object, Object[], Object>> buildEnhancer(
      BitSet methodIndices) {
    if ((hostClass.getModifiers() & FINAL) != 0) {
      throw new IllegalArgumentException("Cannot subclass final " + hostClass);
    }

    Map<BitSet, Function<String, BiFunction<Object, Object[], Object>>> enhancers =
        ENHANCERS.get(hostClass);
    synchronized (enhancers) {
      return enhancers.computeIfAbsent(methodIndices, this::doBuildEnhancer);
    }
  }

  private Function<String, BiFunction<Object, Object[], Object>> doBuildEnhancer(
      BitSet methodIndices) {
    NavigableMap<String, Executable> glueMap = new TreeMap<>();

    visitMembers(
        hostClass.getDeclaredConstructors(),
        hasPackageAccess(),
        ctor -> glueMap.put(signature(ctor), ctor));

    for (int methodIndex = methodIndices.nextSetBit(0);
        methodIndex >= 0;
        methodIndex = methodIndices.nextSetBit(methodIndex + 1)) {
      Method method = enhanceableMethods[methodIndex];
      glueMap.put(signature(method), method);
    }

    return new Enhancer(hostClass, bridgeDelegates).glue(glueMap);
  }
}
