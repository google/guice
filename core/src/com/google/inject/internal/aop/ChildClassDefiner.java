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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.logging.Logger;

/**
 * {@link ClassDefiner} that defines classes using child {@link ClassLoader}s.
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
final class ChildClassDefiner implements ClassDefiner {

  private static final Logger logger = Logger.getLogger(ChildClassDefiner.class.getName());

  // initialization-on-demand...
  private static class SystemChildLoaderHolder {
    static final ChildLoader SYSTEM_CHILD_LOADER = doPrivileged(ChildLoader::new);
  }

  // initialization-on-demand...
  private static class ChildLoaderCacheHolder {
    static final LoadingCache<ClassLoader, ChildLoader> CHILD_LOADER_CACHE =
        CacheBuilder.newBuilder()
            .weakKeys()
            .weakValues()
            .build(CacheLoader.from(ChildClassDefiner::childLoader));
  }

  @Override
  public Class<?> define(Class<?> hostClass, byte[] bytecode) throws Exception {
    ClassLoader hostLoader = hostClass.getClassLoader();

    ChildLoader childLoader =
        hostLoader != null
            ? ChildLoaderCacheHolder.CHILD_LOADER_CACHE.get(hostLoader)
            : SystemChildLoaderHolder.SYSTEM_CHILD_LOADER;

    return childLoader.defineInChild(bytecode);
  }

  /** Utility method to remove doPrivileged ambiguity */
  static <T> T doPrivileged(PrivilegedAction<T> action) {
    return AccessController.doPrivileged(action);
  }

  /** Creates a child loader for the given host loader */
  static ChildLoader childLoader(ClassLoader hostLoader) {
    logger.fine("Creating a child loader for " + hostLoader);
    return doPrivileged(() -> new ChildLoader(hostLoader));
  }

  /** Custom class loader that grants access to defineClass */
  private static final class ChildLoader extends ClassLoader {
    ChildLoader(ClassLoader parent) {
      super(parent);
    }

    ChildLoader() {
      // delegate to system loader
    }

    Class<?> defineInChild(byte[] bytecode) {
      Class<?> type = defineClass(null, bytecode, 0, bytecode.length, null);
      resolveClass(type);
      return type;
    }
  }
}
