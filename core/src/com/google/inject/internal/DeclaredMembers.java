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
package com.google.inject.internal;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Utility class for retrieving declared fields or methods in a particular order, because the JVM
 * doesn't guarantee ordering for listing declared methods. We don't externally guarantee an
 * ordering, but having a consistent ordering allows deterministic behavior and simpler tests.
 */
public final class DeclaredMembers {

  private DeclaredMembers() {}

  public static Field[] getDeclaredFields(Class<?> type) {
    Field[] fields = type.getDeclaredFields();
    Arrays.sort(fields, FIELD_ORDERING);
    return fields;
  }

  public static Method[] getDeclaredMethods(Class<?> type) {
    Method[] methods = type.getDeclaredMethods();
    Arrays.sort(methods, METHOD_ORDERING);
    return methods;
  }

  /**
   * An ordering suitable for comparing two classes if they are loaded by the same classloader
   *
   * <p>Within a single classloader there can only be one class with a given name, so we just
   * compare the names.
   */
  private static final Ordering<Class<?>> CLASS_ORDERING =
      new Ordering<Class<?>>() {
        @Override
        public int compare(Class<?> o1, Class<?> o2) {
          return o1.getName().compareTo(o2.getName());
        }
      };

  /**
   * An ordering suitable for comparing two fields if they are owned by the same class.
   *
   * <p>Within a single class it is sufficent to compare the non-generic field signature which
   * consists of the field name and type.
   */
  private static final Ordering<Field> FIELD_ORDERING =
      new Ordering<Field>() {
        @Override
        public int compare(Field left, Field right) {
          return ComparisonChain.start()
              .compare(left.getName(), right.getName())
              .compare(left.getType(), right.getType(), CLASS_ORDERING)
              .result();
        }
      };

  /**
   * An ordering suitable for comparing two methods if they are owned by the same class.
   *
   * <p>Within a single class it is sufficient to compare the non-generic method signature which
   * consists of the name, return type and parameter types.
   */
  private static final Ordering<Method> METHOD_ORDERING =
      new Ordering<Method>() {
        @Override
        public int compare(Method left, Method right) {
          return ComparisonChain.start()
              .compare(left.getName(), right.getName())
              .compare(left.getReturnType(), right.getReturnType(), CLASS_ORDERING)
              .compare(
                  Arrays.asList(left.getParameterTypes()),
                  Arrays.asList(right.getParameterTypes()),
                  CLASS_ORDERING.lexicographical())
              .result();
        }
      };
}
