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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.google.inject.TypeLiteral;
import com.google.inject.internal.BytecodeGen.EnhancerTarget;

import static java.lang.reflect.Modifier.PRIVATE;
import static java.lang.reflect.Modifier.PROTECTED;
import static java.lang.reflect.Modifier.PUBLIC;
import static java.lang.reflect.Modifier.STATIC;

/**
 * TODO.
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
public class MethodResolver {

  private static final Method[] OBJECT_METHODS = getObjectMethods();

  private static final Function<Object, MethodPartition> NEW_PARTITION =
      key -> new MethodPartition();

  private final Class<?> host;

  private final Map<String, MethodPartition> partitions = new HashMap<>();

  public MethodResolver(Class<?> host) {
    this.host = host;
    initializeMethodPartitions();
  }

  public List<Method> getInstanceMethods() {
    List<Method> instanceMethods = new ArrayList<>();
    for (MethodPartition partition : partitions.values()) {
      partition.collectInstanceMethods(instanceMethods);
    }
    return instanceMethods;
  }

  public EnhancerTarget buildEnhancerTarget() {
    List<Method> enhanceableMethods = new ArrayList<>();
    Map<Method, Method> bridgeDelegates = new HashMap<>();

    TypeLiteral<?> hostType = TypeLiteral.get(host);
    for (MethodPartition partition : partitions.values()) {
      partition.collectEnhanceableMethods(hostType, enhanceableMethods, bridgeDelegates);
    }

    return new EnhancerTargetImpl(host, enhanceableMethods, bridgeDelegates);
  }

  private void initializeMethodPartitions() {
    Set<Class<?>> interfaces = new LinkedHashSet<>();
    for (Class<?> clazz = host;
        clazz != Object.class && clazz != null;
        clazz = clazz.getSuperclass()) {

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

  private void collectInterfaces(Class<?> clazz, Set<Class<?>> interfaces) {
    for (Class<?> intf : clazz.getInterfaces()) {
      if (interfaces.add(intf)) {
        collectInterfaces(intf, interfaces);
      }
    }
  }

  private void partitionMethods(Class<?> clazz) {
    for (Method method : clazz.getDeclaredMethods()) {
      if ((method.getModifiers() & (PRIVATE | STATIC)) == 0) {
        partitionMethod(method);
      }
    }
  }

  private void partitionMethod(Method method) {
    String partitionKey = method.getName() + '#' + method.getParameterCount();
    partitions.computeIfAbsent(partitionKey, NEW_PARTITION).addCandidate(method);
  }

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
