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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.google.inject.TypeLiteral;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.lang.reflect.Modifier.FINAL;

/**
 * TODO.
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
class MethodPartition {

  private final List<Method> candidates = new ArrayList<>();

  public void addCandidate(Method method) {
    candidates.add(method);
  }

  public void collectInstanceMethods(List<Method> instanceMethods) {
    Set<String> visited = new HashSet<>();
    for (Method candidate : candidates) {
      if (visited.add(parametersKey(candidate))) {
        instanceMethods.add(candidate);
      }
    }
  }

  public void collectEnhanceableMethods(
      TypeLiteral<?> hostType,
      List<Method> enhanceableMethods,
      Map<Method, Method> bridgeDelegates) {

    Map<String, Method> leafMethods = new HashMap<>();
    Map<String, Method> bridgeTargets = new HashMap<>();

    for (Method candidate : candidates) {
      String parametersKey = parametersKey(candidate);
      Method existingLeafMethod = leafMethods.putIfAbsent(parametersKey, candidate);
      if (existingLeafMethod == null) {
        if (candidate.isBridge()) {
          bridgeTargets.put(parametersKey, null);
        }
      } else if (existingLeafMethod.isBridge() && !candidate.isBridge()) {
        bridgeTargets.putIfAbsent(parametersKey, candidate);
      }
    }

    for (Entry<String, Method> methodEntry : leafMethods.entrySet()) {
      Method method = methodEntry.getValue();
      if ((method.getModifiers() & FINAL) != 0) {
        bridgeTargets.remove(methodEntry.getKey());
      } else if (!method.isBridge()) {
        enhanceableMethods.add(method);
      }
    }

    for (Entry<String, Method> targetEntry : bridgeTargets.entrySet()) {
      Method bridgeMethod = leafMethods.get(targetEntry.getKey());
      Method superTarget = targetEntry.getValue();

      Method enhanceableMethod = firstNonNull(superTarget, bridgeMethod);
      enhanceableMethods.add(enhanceableMethod);

      for (Method candidate : candidates) {
        if (!candidate.isBridge()) {
          if (candidate == superTarget) {
            break;
          }
          if (resolvedParametersMatch(bridgeMethod, hostType, candidate)
              || (superTarget != null
                  && resolvedParametersMatch(candidate, hostType, superTarget))) {

            bridgeDelegates.put(enhanceableMethod, candidate);
            break;
          }
        }
      }
    }
  }

  private static String parametersKey(Method method) {
    return Arrays.toString(method.getParameterTypes());
  }

  private static boolean resolvedParametersMatch(
      Method subMethod, TypeLiteral<?> host, Method superMethod) {
    Class<?>[] parameterTypes = subMethod.getParameterTypes();
    List<TypeLiteral<?>> resolvedTypes = host.getParameterTypes(superMethod);
    for (int i = 0; i < parameterTypes.length; i++) {
      if (parameterTypes[i] != resolvedTypes.get(i).getRawType()) {
        return false;
      }
    }
    return true;
  }
}
