/**
 * Copyright (C) 2006 Google Inc.
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

package com.google.inject;

/**
 * Built in scope implementations.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class Scopes {

  private Scopes() {}

  /**
   * Name of the default scope.
   */
  public static final String DEFAULT_NAME = "DEFAULT";

  /**
   * The default scope, one instance per injection.
   */
  public static final Scope DEFAULT = new Scope() {
    public <T> Factory<T> scope(Key<T> key, Factory<T> creator) {
      return creator;
    }
  };

  /**
   * Name of container scope.
   */
  public static final String CONTAINER_NAME = "CONTAINER";

  /**
   * One instance per container.
   */
  public static final Scope CONTAINER = new Scope() {
    public <T> Factory<T> scope(Key<T> key, final Factory<T> creator) {
      return new Factory<T>() {

        private volatile T instance;

        public T get() {
          // Double checked locking improves performance and is safe as of
          // Java 5.
          if (instance == null) {
            // Use a pretty coarse lock. We don't want to run into deadlocks
            // when two threads try to load circularly-dependent objects.
            // Maybe one of these days we will identify independent graphs of
            // objects and offer to load them in parallel.
            synchronized (Container.class) {
              if (instance == null) {
                instance = creator.get();
              }
            }
          }
          return instance;
        }

        public String toString() {
          return creator.toString();
        }
      };
    }
  };
}