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

import com.google.common.collect.Ordering;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Utility class for retrieving declared fields or methods in a particular order, because the JVM
 * doesn't guarantee ordering for listing declared methods. We don't externally guarantee an
 * ordering, but having a consistent ordering allows deterministic behavior and simpler tests.
 *
 * <p>For class ordering, within a single classloader there can only be one class with a given name,
 * so we just compare the names.
 *
 * <p>For method ordering, within a single class it is sufficient to compare the non-generic method
 * signature which consists of the name, return type and parameter types.
 */
public final class DeclaredMembers {

  private DeclaredMembers() {}

  public static Field[] getDeclaredFields(Class<?> type) {
    return Arrays.stream(type.getDeclaredFields())
        .sorted(
            Comparator.comparing(Field::getName)
                .thenComparing(Field::getType, Comparator.comparing(Class::getName)))
        .toArray(Field[]::new);
  }

  public static Method[] getDeclaredMethods(Class<?> type) {
    return Arrays.stream(type.getDeclaredMethods())
        .sorted(
            Comparator.comparing(Method::getName)
                .thenComparing(Method::getReturnType, Comparator.comparing(Class::getName))
                .thenComparing(
                    method -> Arrays.asList(method.getParameterTypes()),
                    // TODO: use Comparators.lexicographical when it's not @Beta.
                    Ordering.<Class<?>>from(Comparator.comparing(Class::getName))
                        .lexicographical()))
        .toArray(Method[]::new);
  }
}
