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

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.lang.reflect.Modifier.FINAL;

import com.google.inject.TypeLiteral;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Accumulates methods with the same name and number of parameters. This helps focus the search for
 * bridge delegates that involve type-erasure of generic parameter types, since the parameter count
 * will be the same for the bridge method and its delegate.
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
class MethodPartition {

  /** Reverse order of declaration; super-methods appear later in the list. */
  private final List<Method> candidates = new ArrayList<>();

  /** Each partition starts off with at least two methods. */
  public MethodPartition(Method first, Method second) {
    candidates.add(first);
    candidates.add(second);
  }

  /** Add a new method to this partition for resolution. */
  public void addCandidate(Method method) {
    candidates.add(method);
  }

  /**
   * Resolve and collect instance methods into the given list; one per method-signature. Methods
   * declared in sub-classes are preferred over those in super-classes with the same signature.
   */
  public void collectInstanceMethods(List<Method> instanceMethods) {
    Set<String> visited = new HashSet<>();
    for (Method candidate : candidates) {
      if (visited.add(parametersKey(candidate))) {
        instanceMethods.add(candidate);
      }
    }
  }

  /**
   * Resolve and collect enhanceable methods into the given list; one per method-signature. Methods
   * declared in sub-classes are preferred over those in super-classes with the same signature.
   * (Unless it's a bridge method, in which case we prefer to report the non-bridge method from the
   * super-class as a convenience to AOP method matchers that always ignore synthetic methods.)
   *
   * <p>At the same time we use generic type resolution to match resolved bridge methods to the
   * methods they delegate to (this avoids the need to crack open the original class resource for
   * in-depth analysis by ASM, especially since the class bytes might not be accessible.)
   */
  public void collectEnhanceableMethods(
      TypeLiteral<?> hostType,
      List<Method> enhanceableMethods,
      Map<Method, Method> bridgeDelegates) {

    Map<String, Method> leafMethods = new HashMap<>();
    Map<String, Method> bridgeTargets = new HashMap<>();

    // Capture the first method found under each parameter key, these are called 'leaf' methods

    for (Method candidate : candidates) {
      String parametersKey = parametersKey(candidate);
      Method existingLeafMethod = leafMethods.putIfAbsent(parametersKey, candidate);
      if (existingLeafMethod == null) {
        if (candidate.isBridge()) {
          // Record that we've started looking for the bridge's delegate
          bridgeTargets.put(parametersKey, null);
        }
      } else if (existingLeafMethod.isBridge() && !candidate.isBridge()) {
        // Found potential bridge delegate with exactly the same paramaters (visibility bridge)
        bridgeTargets.putIfAbsent(parametersKey, candidate);
      }
    }

    // Report any non-bridge leaf methods that are not final and can be enhanced

    for (Entry<String, Method> methodEntry : leafMethods.entrySet()) {
      Method method = methodEntry.getValue();
      if ((method.getModifiers() & FINAL) != 0) {
        bridgeTargets.remove(methodEntry.getKey());
      } else if (!method.isBridge()) {
        enhanceableMethods.add(method);
      }
    }

    // This leaves bridge methods

    for (Entry<String, Method> targetEntry : bridgeTargets.entrySet()) {
      Method bridgeMethod = leafMethods.get(targetEntry.getKey());
      Method superTarget = targetEntry.getValue();

      // some AOP matchers skip all synthetic methods, so we give them something to match
      // against by reporting the first non-bridge super-method with the same parameters
      Method enhanceableMethod = firstNonNull(superTarget, bridgeMethod);
      enhanceableMethods.add(enhanceableMethod);

      // scan all methods looking for the bridge delegate by comparing generic parameters
      // (these are the kind of bridge methods that were added to account for type-erasure)
      for (Method candidate : candidates) {
        if (!candidate.isBridge()) {
          if (candidate == superTarget) {
            break; // stop when we reach the super-method that has just been reported
          }
          // see if the bridge method matches the candidate resolved againt the host class
          if (resolvedParametersMatch(bridgeMethod, hostType, candidate)
              || (superTarget != null
                  // otherwise does the candidate match the resolved super-method
                  && resolvedParametersMatch(candidate, hostType, superTarget))) {

            bridgeDelegates.put(enhanceableMethod, candidate);
            break;
          }
        }
      }
    }
  }

  /** Each method is uniquely identified in the partition by its actual parameter types. */
  private static String parametersKey(Method method) {
    return Arrays.toString(method.getParameterTypes());
  }

  /** Compares a sub-method with a generic super-method by resolving it against the host class. */
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
