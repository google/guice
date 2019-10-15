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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link ClassDefiner} that defines classes using {@code sun.misc.Unsafe}.
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
final class UnsafeClassDefiner implements ClassDefiner {

  private static final Logger logger = Logger.getLogger(UnsafeClassDefiner.class.getName());

  private static final Object THE_UNSAFE = tryPrivileged(UnsafeClassDefiner::bindUnsafe);

  private static final Method DEFINE_METHOD = tryPrivileged(UnsafeClassDefiner::bindDefineMethod);

  @Override
  public boolean canAccess(Class<?> host, Visibility visibility) {
    return THE_UNSAFE != null && DEFINE_METHOD != null;
  }

  @Override
  public Class<?> define(Class<?> host, byte[] bytecode) throws Exception {
    return (Class<?>) DEFINE_METHOD.invoke(THE_UNSAFE, host, bytecode, null);
  }

  private static Object bindUnsafe() throws Exception {
    Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
    Field theUnsafeField = unsafeType.getDeclaredField("theUnsafe");
    theUnsafeField.setAccessible(true);
    return theUnsafeField.get(null);
  }

  private static Method bindDefineMethod() throws Exception {
    Class<?> unsafeType = THE_UNSAFE.getClass();
    return unsafeType.getMethod("defineAnonymousClass", Class.class, byte[].class, Object[].class);
  }

  private static <T> T tryPrivileged(PrivilegedExceptionAction<T> action) {
    try {
      return AccessController.doPrivileged(action);
    } catch (Throwable e) {
      logger.log(Level.FINE, "Cannot bind Unsafe.defineAnonymousClass", e);
      return null;
    }
  }
}
