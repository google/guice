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

import com.google.inject.util.SurrogateAnnotations;
import com.google.inject.util.DuplicateAnnotationException;

import java.util.Map;
import java.lang.annotation.Annotation;

/**
 * Built in scope implementations.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class Scopes {

  private Scopes() {}

  /**
   * The default scope, one instance per injection.
   */
  public static final Scope DEFAULT = new Scope() {
    public <T> Factory<T> scope(Key<T> key, Factory<T> creator) {
      return creator;
    }

    public String toString() {
      return "Scopes.DEFAULT";
    }
  };

  /**
   * One instance per container.
   */
  public static final Scope CONTAINER = new Scope() {
    public <T> Factory<T> scope(Key<T> key, final Factory<T> creator) {
      return new Factory<T>() {

        private volatile T instance;

        // DCL on a volatile is safe as of Java 5, which we obviously require.
        @SuppressWarnings("DoubleCheckedLocking")
        public T get() {
          if (instance == null) {
            /*
             * Use a pretty coarse lock. We don't want to run into deadlocks
             * when two threads try to load circularly-dependent objects.
             * Maybe one of these days we will identify independent graphs of
             * objects and offer to load them in parallel.
             */
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

    public String toString() {
      return "Scopes.CONTAINER";
    }
  };

  /**
   * Gets the scope for a type based on its annotations. Returns {@code null}
   * if none specified.
   *
   * @param implementation type
   * @param scopes map of scope names to scopes
   * @param errorHandler handles errors
   */
  static Scope getScopeForType(Class<?> implementation,
      Map<Class<? extends Annotation>, Scope> scopes,
      ErrorHandler errorHandler) {
    Scope found = null;
    for (Annotation annotation : implementation.getAnnotations()) {
      Scope scope = scopes.get(annotation.annotationType());
      if (scope != null) {
        if (found != null) {
          errorHandler.handle(ErrorMessages.DUPLICATE_SCOPE_ANNOTATIONS,
              implementation, found, scope);
        } else {
          found = scope;
        }
      }
    }
    return found;
  }

  /**
   * Scopes an internal factory.
   */
  static <T> InternalFactory<? extends T> scope(Key<T> key,
      ContainerImpl container, InternalFactory<? extends T> creator,
      Scope scope) {
    // Default scope does nothing.
    if (scope == null || scope == DEFAULT) {
      return creator;
    }
    Factory<T> scoped = scope.scope(key,
        new FactoryToInternalFactoryAdapter<T>(container, creator));
    return new InternalFactoryToFactoryAdapter<T>(scoped);
  }
}
