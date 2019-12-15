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

import static java.lang.reflect.Modifier.PRIVATE;
import static java.lang.reflect.Modifier.PROTECTED;
import static java.lang.reflect.Modifier.PUBLIC;
import static java.lang.reflect.Modifier.STATIC;

import com.google.inject.TypeLiteral;
import com.google.inject.internal.BytecodeGen.EnhancerTarget;
import com.google.inject.internal.InternalFlags;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves instance and enhanceable methods for fast-class and enhancer generation.
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
public class MethodResolver {

  private static final Method[] OBJECT_METHODS = getObjectMethods();

  private final Class<?> hostClass;

  // values may be single Methods (common case) or MethodPartitions
  private final Map<String, Object> partitions = new HashMap<>();

  public MethodResolver(Class<?> hostClass) {
    this.hostClass = hostClass;
    initializeMethodPartitions();
  }

  /** Returns all non-private instance methods in the host class; one per method-signature. */
  public List<Method> getInstanceMethods() {
    List<Method> instanceMethods = new ArrayList<>();
    for (Object partition : partitions.values()) {
      if (partition instanceof Method) {
        instanceMethods.add((Method) partition); // common case, just one method with that name
      } else {
        ((MethodPartition) partition).collectInstanceMethods(instanceMethods);
      }
    }
    return instanceMethods;
  }

  /** Preliminary enhancer information for the host class; such as which methods can be enhanced. */
  public EnhancerTarget buildEnhancerTarget() {
    List<Method> enhanceableMethods = new ArrayList<>();
    Map<Method, Method> bridgeDelegates = new HashMap<>();

    TypeLiteral<?> hostType = TypeLiteral.get(hostClass);
    for (Object partition : partitions.values()) {
      if (partition instanceof Method) {
        enhanceableMethods.add((Method) partition); // common case, just one method with that name
      } else {
        ((MethodPartition) partition)
            .collectEnhanceableMethods(hostType, enhanceableMethods, bridgeDelegates);
      }
    }

    return new EnhancerTargetImpl(hostClass, enhanceableMethods, bridgeDelegates);
  }

  /**
   * Partition all methods declared by the host class hierarchy. The general ordering is the same as
   * the JVM when it comes to resolving methods: those declared by sub-classes before super-classes,
   * finally any methods declared by interfaces.
   */
  private void initializeMethodPartitions() {
    Set<Class<?>> interfaces = new LinkedHashSet<>();

    for (Class<?> clazz = hostClass;
        clazz != Object.class && clazz != null;
        clazz = clazz.getSuperclass()) {

      // track for partitioning at the end
      collectInterfaces(clazz, interfaces);

      partitionMethods(clazz);
    }

    for (Method method : OBJECT_METHODS) {
      partitionMethod(method);
    }

    for (Class<?> intf : interfaces) {
      partitionMethods(intf);
    }
  }

  /** Collect all interfaces implemented by the given class. */
  private void collectInterfaces(Class<?> clazz, Set<Class<?>> interfaces) {
    for (Class<?> intf : clazz.getInterfaces()) {
      if (interfaces.add(intf)) {
        collectInterfaces(intf, interfaces);
      }
    }
  }

  /**
   * Partition any non-private, non-static methods as they all have the potential to be enhanced.
   *
   * <p>The mechanism configured by {@link InternalFlags#getCustomClassLoadingOption()} to define
   * the enhanced class will determine what is visible to the enhancer once we've resolved the
   * methods down to a flat list.
   */
  private void partitionMethods(Class<?> clazz) {
    for (Method method : clazz.getDeclaredMethods()) {
      if ((method.getModifiers() & (PRIVATE | STATIC)) == 0) {
        partitionMethod(method);
      }
    }
  }

  /** Methods are partitioned by name plus parameter count. */
  private void partitionMethod(Method method) {
    String partitionKey = method.getName() + '#' + method.getParameterCount();
    Object partition = partitions.get(partitionKey);
    if (partition == null) {
      // very common case: just one method with that name, store directly to reduce overhead
      partitions.put(partitionKey, method);
    } else if (partition instanceof Method) {
      // found a second method, inflate to a proper partition containing the two methods
      partitions.put(partitionKey, new MethodPartition((Method) partition, method));
    } else {
      // continue to add methods to the existing partition
      ((MethodPartition) partition).addCandidate(method);
    }
  }

  /** Visible Object instance methods; ie. public or protected and not static. */
  private static Method[] getObjectMethods() {
    List<Method> objectMethods = new ArrayList<>();
    for (Method method : Object.class.getDeclaredMethods()) {
      int flags = method.getModifiers() & (PUBLIC | PROTECTED | STATIC);
      if ((flags == PUBLIC || flags == PROTECTED) && !"finalize".equals(method.getName())) {
        objectMethods.add(method);
      }
    }
    return objectMethods.toArray(new Method[objectMethods.size()]);
  }
}
