/**
 * Copyright (C) 2010 Google, Inc.
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

package com.google.inject.persist;

import com.google.inject.persist.finder.Finder;
import java.lang.reflect.Method;

/**
 * <p> This is the core guice-persist artifact. It providers factories for generating guice modules
 * for your persistence configuration. It also must be injected into your code later as a service
 * abstraction for starting the underlying persistence engine (Hibernate or JPA). <p>
 * Implementations of this type should make sure {@link #start()} and {@link #shutdown()} are thread
 * safe.
 *
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */
public abstract class PersistenceService {

  /**
   * Starts the underlying persistence engine and makes guice-persist ready for use. For instance,
   * with hibernate, it creates a SessionFactory and may open connection pools. This method *must*
   * be called by your code prior to using any guice-persist or hibernate artifacts. If already
   * started, calling this method does nothing, if already stopped, it also does nothing.
   */
  public abstract void start();

  /**
   * Stops the underlying persistence engine. For instance, with Hibernate, it closes the
   * SessionFactory. If already stopped, calling this method does nothing. If not yet started, it
   * also does nothing.
   */
  public abstract void shutdown();

  /**
   * A utility for testing if a given method is a dynamic finder.
   *
   * @param method A method you suspect is a Dynamic Finder.
   * @return Returns true if the method is annotated {@code @Finder}
   */
  public static boolean isDynamicFinder(Method method) {
    return method.isAnnotationPresent(Finder.class);
  }
}
