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

import static java.lang.reflect.Modifier.FINAL;
import static java.lang.reflect.Modifier.PRIVATE;
import static java.lang.reflect.Modifier.PROTECTED;
import static java.lang.reflect.Modifier.PUBLIC;
import static java.lang.reflect.Modifier.STATIC;

import com.google.inject.TypeLiteral;
import com.google.inject.internal.BytecodeGen;
import com.google.inject.internal.InternalFlags;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Entry-point for resolving candidate methods for fast-class and enhancer generation.
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
public final class MethodResolving {

  private static final Method[] OBJECT_METHODS = getObjectMethods();

  /** Returns all non-private instance methods in the host class; one per method-signature. */
  public static List<Method> getInstanceMethods(Class<?> hostClass) {
    List<Method> instanceMethods = new ArrayList<>();

    for (Object partition : initializeMethodPartitions(hostClass)) {
      if (partition instanceof Method) {
        // common case, partition is just one method
        instanceMethods.add((Method) partition);
      } else {
        ((MethodPartition) partition).collectInstanceMethods(instanceMethods);
      }
    }

    return instanceMethods;
  }

  /** Preliminary enhancer information for the host class; such as which methods can be enhanced. */
  public static BytecodeGen.EnhancerTarget buildEnhancerTarget(Class<?> hostClass) {
    List<Method> enhanceableMethods = new ArrayList<>();

    Map<Method, Method> originalBridges = new HashMap<>();
    Map<Method, Method> bridgeDelegates = new HashMap<>();

    TypeLiteral<?> hostType = TypeLiteral.get(hostClass);
    for (Object partition : initializeMethodPartitions(hostClass)) {
      if (partition instanceof Method) {
        // common case, partition is just one method; exclude if it turns out to be final
        Method method = (Method) partition;
        if ((method.getModifiers() & FINAL) == 0) {
          enhanceableMethods.add(method);
        }
      } else {
        ((MethodPartition) partition)
            .collectEnhanceableMethods(
                hostType, enhanceableMethods, originalBridges, bridgeDelegates);
      }
    }

    return new EnhancerTargetImpl(hostClass, enhanceableMethods, originalBridges, bridgeDelegates);
  }

  /**
   * Partition all methods declared by the host class hierarchy. The general ordering is the same as
   * the JVM when it comes to resolving methods: those declared by sub-classes before super-classes,
   * and finally any methods declared by interfaces.
   *
   * @return Collection of single Methods or MethodPartitions
   */
  private static Collection<?> initializeMethodPartitions(Class<?> hostClass) {
    Map<String, Object> partitions = new HashMap<>();
    Set<Class<?>> interfaces = new LinkedHashSet<>();

    for (Class<?> clazz = hostClass;
        clazz != Object.class && clazz != null;
        clazz = clazz.getSuperclass()) {

      // track for partitioning at the end
      collectInterfaces(clazz, interfaces);

      partitionMethods(clazz, partitions);
    }

    for (Method method : OBJECT_METHODS) {
      partitionMethod(method, partitions);
    }

    for (Class<?> intf : interfaces) {
      partitionMethods(intf, partitions);
    }

    return partitions.values();
  }

  /** Collect all interfaces implemented by the given class. */
  private static void collectInterfaces(Class<?> clazz, Set<Class<?>> interfaces) {
    for (Class<?> intf : clazz.getInterfaces()) {
      if (interfaces.add(intf)) {
        collectInterfaces(intf, interfaces);
      }
    }
  }

  /**
   * Partition any methods that aren't private or static. We include package-private methods because
   * they may be enhanceable depending on the {@link InternalFlags#getCustomClassLoadingOption()}.
   * We also retain final methods in the partition; they can't be enhanced but we might want them
   * for fast-class generation when we want to call setters, etc.
   */
  private static void partitionMethods(Class<?> clazz, Map<String, Object> partitions) {
    for (Method method : clazz.getDeclaredMethods()) {
      if ((method.getModifiers() & (PRIVATE | STATIC)) == 0 && !banned(method)) {
        partitionMethod(method, partitions);
      }
    }
  }

  /**
   * Methods are partitioned by name and parameter count. This helps focus the search for bridge
   * delegates that involve type-erasure of generic parameter types, since the parameter count will
   * be the same for the bridge method and its delegate.
   */
  private static void partitionMethod(Method method, Map<String, Object> partitions) {
    String partitionKey = method.getName() + '#' + method.getParameterCount();
    // common case: assume only one method with that key, store method directly to reduce overhead
    Object existingPartition = partitions.putIfAbsent(partitionKey, method);
    if (existingPartition instanceof Method) {
      // this is the second matching method, inflate to MethodPartition containing the two methods
      partitions.put(partitionKey, new MethodPartition((Method) existingPartition, method));
    } else if (existingPartition instanceof MethodPartition) {
      // continue to add methods to the existing MethodPartition
      ((MethodPartition) existingPartition).addCandidate(method);
    }
  }

  /** Cache common enhanceable Object methods; ie. public or protected and not static or final. */
  private static Method[] getObjectMethods() {
    List<Method> objectMethods = new ArrayList<>();
    for (Method method : Object.class.getDeclaredMethods()) {
      int flags = method.getModifiers() & (PUBLIC | PROTECTED | STATIC | FINAL);
      if ((flags == PUBLIC || flags == PROTECTED) && !banned(method)) {
        objectMethods.add(method);
      }
    }
    return objectMethods.toArray(new Method[objectMethods.size()]);
  }

  /** Deliberately ban certain methods from being enhanced, such as {@link Object#finalize}. */
  private static boolean banned(Method method) {
    return method.getParameterCount() == 0 && "finalize".equals(method.getName());
  }
}
