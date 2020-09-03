/*
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
 * Persistence provider service. Use this to manage the overall startup and stop of the persistence
 * module(s).
 *
 * <p>TODO(user): Integrate with Service API when appropriate.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public interface PersistService {

  /**
   * Starts the underlying persistence engine and makes guice-persist ready for use. For instance,
   * with JPA, it creates an EntityManagerFactory and may open connection pools. This method must be
   * called by your code prior to using any guice-persist or JPA artifacts. If already started,
   * calling this method does nothing, if already stopped, it also does nothing.
   */
  void start();

  /**
   * Stops the underlying persistence engine. For instance, with JPA, it closes the {@code
   * EntityManagerFactory}. If already stopped, calling this method does nothing. If not yet
   * started, it also does nothing.
   */
  void stop();
}
