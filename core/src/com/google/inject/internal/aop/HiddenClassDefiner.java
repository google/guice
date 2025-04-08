/*
 * Copyright (C) 2021 Google Inc.
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

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * {@link ClassDefiner} that defines classes using {@code MethodHandles.Lookup#defineHiddenClass}.
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
@SuppressWarnings("SunApi")
final class HiddenClassDefiner implements ClassDefiner {

  private static final sun.misc.Unsafe THE_UNSAFE;
  private static final Object TRUSTED_LOOKUP_BASE;
  private static final long TRUSTED_LOOKUP_OFFSET;
  private static final Object HIDDEN_CLASS_OPTIONS;
  private static final Method HIDDEN_DEFINE_METHOD;

  /** True if this class err'd during initialization and should not be used. */
  static final boolean HAS_ERROR;

  static {
    sun.misc.Unsafe theUnsafe;
    Object trustedLookupBase;
    long trustedLookupOffset;
    Object hiddenClassOptions;
    Method hiddenDefineMethod;
    try {
      theUnsafe = UnsafeGetter.getUnsafe();
      Field trustedLookupField = Lookup.class.getDeclaredField("IMPL_LOOKUP");
      trustedLookupBase = theUnsafe.staticFieldBase(trustedLookupField);
      trustedLookupOffset = theUnsafe.staticFieldOffset(trustedLookupField);
      hiddenClassOptions = classOptions("NESTMATE");
      hiddenDefineMethod =
          Lookup.class.getMethod(
              "defineHiddenClass", byte[].class, boolean.class, hiddenClassOptions.getClass());
    } catch (Throwable e) {
      // Allow the static initialization to complete without
      // throwing an exception.
      theUnsafe = null;
      trustedLookupBase = null;
      trustedLookupOffset = 0;
      hiddenClassOptions = null;
      hiddenDefineMethod = null;
    }

    THE_UNSAFE = theUnsafe;
    TRUSTED_LOOKUP_BASE = trustedLookupBase;
    TRUSTED_LOOKUP_OFFSET = trustedLookupOffset;
    HIDDEN_CLASS_OPTIONS = hiddenClassOptions;
    HIDDEN_DEFINE_METHOD = hiddenDefineMethod;
    HAS_ERROR = theUnsafe == null;
  }

  @Override
  public Class<?> define(Class<?> hostClass, byte[] bytecode) throws Exception {
    if (HAS_ERROR) {
      throw new IllegalStateException(
          "Should not be called. An earlier error occurred during HiddenClassDefiner static"
              + " initialization.");
    }

    Lookup trustedLookup =
        (Lookup) THE_UNSAFE.getObject(TRUSTED_LOOKUP_BASE, TRUSTED_LOOKUP_OFFSET);
    Lookup definedLookup =
        (Lookup)
            HIDDEN_DEFINE_METHOD.invoke(
                trustedLookup.in(hostClass), bytecode, false, HIDDEN_CLASS_OPTIONS);
    return definedLookup.lookupClass();
  }

  /** Creates {@link MethodHandles.Lookup.ClassOption} array with the named options. */
  @SuppressWarnings("unchecked")
  private static Object classOptions(String... options) throws ClassNotFoundException {
    @SuppressWarnings("rawtypes") // Unavoidable, only way to use Enum.valueOf
    Class optionClass = Class.forName(Lookup.class.getName() + "$ClassOption");
    Object classOptions = Array.newInstance(optionClass, options.length);
    for (int i = 0; i < options.length; i++) {
      Array.set(classOptions, i, Enum.valueOf(optionClass, options[i]));
    }
    return classOptions;
  }
}
