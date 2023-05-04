/*
 * Copyright (C) 2023 Google Inc.
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

final class UnsafeGetter {

  private UnsafeGetter() {}

  static sun.misc.Unsafe getUnsafe() throws ReflectiveOperationException {
    try {
      return sun.misc.Unsafe.getUnsafe();
    } catch (SecurityException unusedFallbackToReflection) {
    }
    // Note that we do not do this in a privileged action because we expect we're already in a
    // privileged block (from UnsafeClassDefiner).
    Class<sun.misc.Unsafe> k = sun.misc.Unsafe.class;
    for (java.lang.reflect.Field f : k.getDeclaredFields()) {
      f.setAccessible(true);
      Object x = f.get(null);
      if (k.isInstance(x)) {
        return k.cast(x);
      }
    }
    throw new NoSuchFieldError("the Unsafe");
  }
}
