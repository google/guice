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

import static java.lang.reflect.Modifier.FINAL;

import com.google.inject.TypeLiteral;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Accumulates methods with the same name and number of parameters. This helps focus the search for
 * bridge delegates that involve type-erasure of generic parameter types, since the parameter count
 * will be the same for the bridge method and its delegate.
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
final class MethodPartition {

  /** Reverse order of declaration; super-methods appear later in the list. */
  private final List<Method> candidates = new ArrayList<>();

  /** Each partition starts off with at least two methods. */
  public MethodPartition(Method first, Method second) {
    candidates.add(first);
    candidates.add(second);
  }

  /** Add a new method to this partition for resolution. */
  public MethodPartition addCandidate(Method method) {
    candidates.add(method);
    return this;
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
      Consumer<Method> methodVisitor,
      Map<Method, Method> bridgeDelegates) {

    Map<String, Method> leafMethods = new HashMap<>();
    Map<String, Method> bridgeTargets = new HashMap<>();

    // First resolve the 'leaf' methods; these represent the latest declaration of each method in
    // the class hierarchy (ie. ignoring super-class declarations with the same parameter types)

    for (Method candidate : candidates) {
      String parametersKey = parametersKey(candidate.getParameterTypes());
      Method existingLeafMethod = leafMethods.putIfAbsent(parametersKey, candidate);
      if (existingLeafMethod == null) {
        if (candidate.isBridge()) {
          // Record that we've started looking for the bridge's delegate
          bridgeTargets.put(parametersKey, null);
        }
      } else if (existingLeafMethod.isBridge() && !candidate.isBridge()) {
        // Found potential bridge delegate with identical parameters
        bridgeTargets.putIfAbsent(parametersKey, candidate);
      }
    }

    // Discard any 'final' methods, as they cannot be enhanced, and report non-bridge leaf methods

    for (Map.Entry<String, Method> methodEntry : leafMethods.entrySet()) {
      Method method = methodEntry.getValue();
      if ((method.getModifiers() & FINAL) != 0) {
        bridgeTargets.remove(methodEntry.getKey());
      } else if (!method.isBridge()) {
        methodVisitor.accept(method);
      }
    }

    // This leaves bridge methods which need further resolution; specifically around finding the
    // real bridge delegate so we can call it using invokevirtual from our enhanced method rather
    // than relying on super-class invocation to the original bridge method (as this would bypass
    // interception if the delegate method was itself intercepted by a different interceptor!)

    for (Map.Entry<String, Method> targetEntry : bridgeTargets.entrySet()) {
      Method originalBridge = leafMethods.get(targetEntry.getKey());
      Method superTarget = targetEntry.getValue();

      Method enhanceableMethod = originalBridge;

      // scan all methods looking for the bridge delegate by comparing generic parameters
      // (these are the kind of bridge methods that were added to account for type-erasure)
      for (Method candidate : candidates) {
        if (!candidate.isBridge()) {
          @SuppressWarnings("ReferenceEquality")
          boolean sameMethod = candidate == superTarget;
          if (sameMethod) {

            // some AOP matchers skip all synthetic methods, so if bridge delegation is not involved
            // and we have a non-bridge super-method with identical parameters and it is not from an
            // interface then use that as the enhanceable method instead of the original bridge
            if (!superTarget.getDeclaringClass().isInterface()) {
              enhanceableMethod = superTarget;
            }

            break; // we've reached the non-bridge super-method so default to super-class invocation
          }
          // compare bridge method against resolved candidate
          if (resolvedParametersMatch(originalBridge, hostType, candidate)
              || (superTarget != null
                  // compare candidate against resolved super-method
                  && resolvedParametersMatch(candidate, hostType, superTarget))) {

            // found a target that differs by raw parameter types but matches the bridge after
            // generic resolution; record this delegation so we can call it with invokevirtual
            bridgeDelegates.put(originalBridge, candidate);
            break;
          }
        }
      }

      // sanity check: only report method if we can enhance it
      if ((enhanceableMethod.getModifiers() & FINAL) == 0) {
        methodVisitor.accept(enhanceableMethod);
      }
    }
  }

  /** Each method is uniquely identified in the partition by its actual parameter types. */
  private static String parametersKey(Class<?>[] parameterTypes) {
    StringBuilder key = new StringBuilder();
    for (int i = 0, len = parameterTypes.length; i < len; i++) {
      if (i > 0) {
        key.append(',');
      }
      key.append(parameterTypes[i].getName());
    }
    return key.toString();
  }

  /** Compares a sub-method with a generic super-method by resolving it against the host class. */
  private static boolean resolvedParametersMatch(
      Method subMethod, TypeLiteral<?> host, Method superMethod) {
    Class<?>[] parameterTypes = subMethod.getParameterTypes();
    List<TypeLiteral<?>> resolvedTypes = host.getParameterTypes(superMethod);
    for (int i = 0, len = parameterTypes.length; i < len; i++) {
      if (parameterTypes[i] != resolvedTypes.get(i).getRawType()) {
        return false;
      }
    }
    return true;
  }
}
