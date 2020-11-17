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

import static com.google.inject.internal.aop.ClassDefining.hasPackageAccess;
import static java.lang.reflect.Modifier.FINAL;
import static java.lang.reflect.Modifier.PRIVATE;
import static java.lang.reflect.Modifier.PROTECTED;
import static java.lang.reflect.Modifier.PUBLIC;
import static java.lang.reflect.Modifier.STATIC;

import com.google.inject.TypeLiteral;
import com.google.inject.internal.BytecodeGen;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Entry-point for building enhanced classes and 'fast-class' invocation.
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
public final class ClassBuilding {
  private ClassBuilding() {}

  private static final Method[] OVERRIDABLE_OBJECT_METHODS = getOverridableObjectMethods();

  /** Minimum signature needed to disambiguate constructors from the same host class. */
  public static String signature(Constructor<?> constructor) {
    return signature("<init>", constructor.getParameterTypes());
  }

  /** Minimum signature needed to disambiguate methods from the same host class. */
  public static String signature(Method method) {
    return signature(method.getName(), method.getParameterTypes());
  }

  /** Appends a semicolon-separated list of parameter types to the given name. */
  private static String signature(String name, Class<?>[] parameterTypes) {
    StringBuilder signature = new StringBuilder(name);
    for (Class<?> type : parameterTypes) {
      signature.append(';').append(type.getName());
    }
    return signature.toString();
  }

  /** Returns true if the given member can be enhanced using bytecode. */
  public static boolean canEnhance(Executable member) {
    return canAccess(member, hasPackageAccess());
  }

  /** Builder of enhancers that provide method interception via bytecode generation. */
  public static BytecodeGen.EnhancerBuilder buildEnhancerBuilder(Class<?> hostClass) {
    Map<String, Object> methodPartitions = new HashMap<>();

    visitMethodHierarchy(
        hostClass,
        method -> {
          // exclude static methods, but keep final methods for bridge analysis
          if ((method.getModifiers() & STATIC) == 0) {
            partitionMethod(method, methodPartitions);
          }
        });

    Map<String, Method> enhanceableMethods = new TreeMap<>();
    Map<Method, Method> bridgeDelegates = new HashMap<>();

    TypeLiteral<?> hostType = TypeLiteral.get(hostClass);
    for (Object partition : methodPartitions.values()) {
      if (partition instanceof Method) {
        // common case, partition is just one method; exclude if it turns out to be final
        Method method = (Method) partition;
        if ((method.getModifiers() & FINAL) == 0) {
          enhanceableMethods.put(signature(method), method);
        }
      } else {
        ((MethodPartition) partition)
            .collectEnhanceableMethods(
                hostType,
                method -> enhanceableMethods.put(signature(method), method),
                bridgeDelegates);
      }
    }

    return new EnhancerBuilderImpl(hostClass, enhanceableMethods.values(), bridgeDelegates);
  }

  /**
   * Methods are partitioned by name and parameter count. This helps focus the search for bridge
   * delegates that involve type-erasure of generic parameter types, since the parameter count will
   * be the same for the bridge method and its delegate.
   */
  private static void partitionMethod(Method method, Map<String, Object> partitions) {
    String partitionKey = method.getName() + '/' + method.getParameterCount();
    partitions.merge(partitionKey, method, ClassBuilding::mergeMethods);
  }

  /** Add the new method to an existing partition or create a new one. */
  private static Object mergeMethods(Object existing, Object added) {
    Method newMethod = (Method) added;
    if (existing instanceof Method) {
      return new MethodPartition((Method) existing, newMethod);
    }
    return ((MethodPartition) existing).addCandidate(newMethod);
  }

  /** Visit the method hierarchy for the host class. */
  private static void visitMethodHierarchy(Class<?> hostClass, Consumer<Method> visitor) {
    // this is an iterative form of the following recursive search:
    // 1. visit declared methods
    // 2. recursively visit superclass
    // 3. visit declared interfaces

    // stack of interface declarations, from host class (bottom) to superclass (top)
    Deque<Class<?>[]> interfaceStack = new ArrayDeque<>();

    // only try to match package-private methods if the class-definer has package-access
    String hostPackage = hasPackageAccess() ? packageName(hostClass.getName()) : null;

    for (Class<?> clazz = hostClass;
        clazz != Object.class && clazz != null;
        clazz = clazz.getSuperclass()) {

      // optionally visit package-private methods matching the same package as the host
      boolean samePackage = hostPackage != null && hostPackage.equals(packageName(clazz.getName()));

      visitMembers(clazz.getDeclaredMethods(), samePackage, visitor);
      pushInterfaces(interfaceStack, clazz.getInterfaces());
    }

    for (Method method : OVERRIDABLE_OBJECT_METHODS) {
      visitor.accept(method);
    }

    // work our way back down the class hierarchy, merging and flattening interfaces into a list
    List<Class<?>> interfaces = new ArrayList<>();
    while (!interfaceStack.isEmpty()) {
      for (Class<?> intf : interfaceStack.pop()) {
        if (mergeInterface(interfaces, intf)) {
          pushInterfaces(interfaceStack, intf.getInterfaces());
        }
      }
    }

    // finally visit the methods declared in the flattened interface hierarchy
    for (Class<?> intf : interfaces) {
      visitMembers(intf.getDeclaredMethods(), false, visitor);
    }
  }

  /** Pushes the interface declaration onto the stack if it's not empty. */
  private static void pushInterfaces(Deque<Class<?>[]> interfaceStack, Class<?>[] interfaces) {
    if (interfaces.length > 0) {
      interfaceStack.push(interfaces);
    }
  }

  /** Attempts to merge the interface with the current flattened hierarchy. */
  private static boolean mergeInterface(List<Class<?>> interfaces, Class<?> candidate) {
    // work along the flattened hierarchy to find the appropriate merge point
    for (int i = 0, len = interfaces.size(); i < len; i++) {
      Class<?> existingInterface = interfaces.get(i);
      if (existingInterface == candidate) {
        // already seen this interface, skip further processing
        return false;
      } else if (existingInterface.isAssignableFrom(candidate)) {
        // extends existing interface, insert just before it in the flattened hierarchy
        interfaces.add(i, candidate);
        return true;
      }
    }
    // unrelated or a superinterface, in both cases append to the flattened hierarchy
    return interfaces.add(candidate);
  }

  /** Extract the package name from a class name. */
  private static String packageName(String className) {
    return className.substring(0, className.lastIndexOf('.') + 1);
  }

  /** Cache common overridable Object methods. */
  private static Method[] getOverridableObjectMethods() {
    List<Method> objectMethods = new ArrayList<>();

    visitMembers(
        Object.class.getDeclaredMethods(),
        false, // no package-level access
        method -> {
          // skip methods that can't/shouldn't be overridden
          if ((method.getModifiers() & (STATIC | FINAL)) == 0
              && !"finalize".equals(method.getName())) {
            objectMethods.add(method);
          }
        });

    return objectMethods.toArray(new Method[0]);
  }

  /** Returns true if the given member can be fast-invoked. */
  public static boolean canFastInvoke(Executable member) {
    int modifiers = member.getModifiers() & (PUBLIC | PRIVATE);
    if (hasPackageAccess()) {
      // can fast-invoke anything except private members
      return modifiers != PRIVATE;
    }
    // can fast-invoke public members in public types whose parameters are all public
    boolean visible = (modifiers == PUBLIC) && isPublic(member.getDeclaringClass());
    if (visible) {
      for (Class<?> type : member.getParameterTypes()) {
        if (!isPublic(type)) {
          return false;
        }
      }
    }
    return visible;
  }

  private static boolean isPublic(Class<?> clazz) {
    return (clazz.getModifiers() & PUBLIC) != 0;
  }

  /** Builds a 'fast-class' invoker that uses bytecode generation in place of reflection. */
  public static Function<String, BiFunction<Object, Object[], Object>> buildFastClass(
      Class<?> hostClass) {
    NavigableMap<String, Executable> glueMap = new TreeMap<>();

    visitFastConstructors(hostClass, ctor -> glueMap.put(signature(ctor), ctor));
    visitFastMethods(hostClass, method -> glueMap.put(signature(method), method));

    return new FastClass(hostClass).glue(glueMap);
  }

  /** Visit all constructors for the host class that can be fast-invoked. */
  private static void visitFastConstructors(Class<?> hostClass, Consumer<Constructor<?>> visitor) {
    if (hasPackageAccess()) {
      // can fast-invoke all non-private constructors
      visitMembers(hostClass.getDeclaredConstructors(), true, visitor);
    } else {
      // can only fast-invoke public constructors
      for (Constructor<?> constructor : hostClass.getConstructors()) {
        visitor.accept(constructor);
      }
    }
  }

  /** Visit all methods declared by the host class that can be fast-invoked. */
  private static void visitFastMethods(Class<?> hostClass, Consumer<Method> visitor) {
    if (hasPackageAccess()) {
      // can fast-invoke all non-private methods declared by the class
      visitMembers(hostClass.getDeclaredMethods(), true, visitor);
    } else {
      // can only fast-invoke public methods
      for (Method method : hostClass.getMethods()) {
        // limit to those declared by this class; inherited methods have their own fast-class
        if (hostClass == method.getDeclaringClass()) {
          visitor.accept(method);
        }
      }
    }
  }

  /** Visit all subclass accessible members in the given array. */
  static <T extends Executable> void visitMembers(
      T[] members, boolean samePackage, Consumer<T> visitor) {
    for (T member : members) {
      if (canAccess(member, samePackage)) {
        visitor.accept(member);
      }
    }
  }

  /** Can we access this member from a subclass which may be in the same package? */
  private static boolean canAccess(Executable member, boolean samePackage) {
    int modifiers = member.getModifiers();

    // public and protected members are always ok, non-private also ok if in the same package
    return (modifiers & (PUBLIC | PROTECTED)) != 0 || (samePackage && (modifiers & PRIVATE) == 0);
  }
}
