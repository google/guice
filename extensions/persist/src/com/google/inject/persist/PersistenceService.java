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

/**
 * This is the core guice-persist artifact. It providers factories for
 * generating guice modules for your persistence configuration. It also must
 * be injected into your code later as a service abstraction for starting the
 * underlying persistence engine (Hibernate or JPA).
 * <p>
 * Implementations of this type should make sure {@link #start()} and
 * {@link #shutdown()} are threadsafe.
 *
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */
public abstract class PersistenceService {

  /**
   * Starts the underlying persistence engine and makes guice-persist ready for
   * use. For instance, with hibernate, it creates a SessionFactory and may
   * open connection pools. This method must be called by your code prior to
   * using any guice-persist or hibernate artifacts. If already started,
   * calling this method does nothing, if already stopped, it also does
   * nothing.
   */
  public abstract void start();

  /**
   * Stops the underlying persistence engine. For instance, with JPA, it
   * closes the {@code EntityManagerFactory}. If already stopped, calling this
   * method does nothing. If not yet started, it also does nothing.
   */
  public abstract void shutdown();
}
