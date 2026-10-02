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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Method;
/**
 * {@link ClassDefiner} that defines classes using {@code MethodHandles.Lookup#defineHiddenClass}.
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
final class HiddenClassDefiner implements ClassDefiner {

  @Override
  public Class<?> define(Class<?> hostClass, byte[] bytecode) throws Exception {
    Module guiceModule = HiddenClassDefiner.class.getModule();
    Module hostModule = hostClass.getModule();
    if (guiceModule.isNamed() && hostModule.isNamed()) {
      if (!guiceModule.canRead(hostModule)) {
        guiceModule.addReads(hostModule);
      }
      if (!hostModule.isOpen(hostClass.getPackageName(), guiceModule)) {
        hostModule.addOpens(hostClass.getPackageName(), guiceModule);
      }
    }

    Lookup initialLookup;
    try {
      initialLookup = MethodHandles.privateLookupIn(hostClass, MethodHandles.lookup());
    } catch (IllegalAccessException e) {
      initialLookup = MethodHandles.lookup().in(hostClass);
    }
    return defineClass(initialLookup, bytecode, hostClass);
  }

  private Class<?> defineClass(Lookup lookup, byte[] bytecode, Class<?> hostClass) throws Exception {
    try {
      return lookup.defineClass(bytecode);
    } catch (IllegalAccessException e) {
      // 1) Try hostClass.getModuleLookup() if the host exposes one.
      try {
        Method getModuleLookup = hostClass.getDeclaredMethod("getModuleLookup");
        getModuleLookup.setAccessible(true);
        Lookup nextLookup = (Lookup) getModuleLookup.invoke(null);
        if (nextLookup != null && !nextLookup.equals(lookup)) {
          return nextLookup.defineClass(bytecode);
        }
      } catch (Throwable ignored) {
        // Ignore and continue with other lookup strategies.
      }

      // 2) Retry with a private lookup.
      try {
        Lookup nextLookup = MethodHandles.privateLookupIn(hostClass, MethodHandles.lookup());
        if (nextLookup != null && !nextLookup.equals(lookup)) {
          return nextLookup.defineClass(bytecode);
        }
      } catch (IllegalAccessException ignored) {
        // Ignore and continue with other lookup strategies.
      }

      // 3) Last attempt with lookup().in(hostClass).
      Lookup fallbackLookup = MethodHandles.lookup().in(hostClass);
      if (!fallbackLookup.equals(lookup)) {
        try {
          return fallbackLookup.defineClass(bytecode);
        } catch (IllegalAccessException ignored) {
          // Fall through to rethrow the original access error.
        }
      }

      throw e;
    }
  }
}
