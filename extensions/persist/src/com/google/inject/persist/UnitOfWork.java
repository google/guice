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
 * Enumerates all the supported units-of-work (i.e. atomic lifespan of a persistence session).
 *
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */
public enum UnitOfWork {
  /**
   * Logical arbitrary unit of work that is managed manually and generally specific to a running
   * thread.
   */
  CUSTOM,

  /**
   *  Logical unit of work (ObjectContainer or JPA EntityManager) that spans an HTTP request.
   */
  REQUEST,

  /**
   * Logical unit of work (Session, ObjectContainer or JPA EntityManager) that spans a transaction
   * demarcated with {@code @Transactional}.
   */
  TRANSACTION
}
