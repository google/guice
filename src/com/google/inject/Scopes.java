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

import java.lang.annotation.Annotation;
import java.util.Map;

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

    public String toString() {
      return DEFAULT_NAME;
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
      return CONTAINER_NAME;
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
      Map<String, Scope> scopes, ErrorHandler errorHandler) {
    return getScopeForName(
        getScopeNameForType(implementation, errorHandler),
        scopes,
        errorHandler
    );

  }

  /**
   * Finds scope for the given name. Returns {@code null} if the name is
   * {@code null}. Otherwise, records an error if a scope isn't found.
   */
  static Scope getScopeForName(String name, Map<String, Scope> scopes,
      ErrorHandler errorHandler) {
    // None found.
    if (name == null) {
      return null;
    }

    // Look up scope for name.
    Scope scope = scopes.get(name);
    if (scope == null) {
      errorHandler.handle(
          ErrorMessages.SCOPE_NOT_FOUND, name, scopes.keySet());
    }
    return scope;
  }

  /**
   * Gets the scope name from annotations on the given type. Records errors if
   * multiple names are found.
   */
  static String getScopeNameForType(Class<?> implementation,
      ErrorHandler errorHandler) {
    // The first name and annotation type we come to. We hold on to the
    // annotation type in case we need it in an error message.
    String firstName = null;
    Class<? extends Annotation> firstType = null;

    for (Annotation annotation : implementation.getAnnotations()) {
      // Look for @Scoped on the class itself and on annotations.
      Scoped scoped = findScoped(annotation);

      // If we found an @Scoped, record its value or an error if we already
      // recorded a value.
      if (scoped != null) {
        String name = scoped.value();
        Class<? extends Annotation> type = annotation.annotationType();
        if (firstName == null) {
          firstName = name;
          firstType = type;
        } else {
          // Scope already set.
          errorHandler.handle(ErrorMessages.SCOPE_ALREADY_SET_BY_ANNOTATION,
              implementation, type.getSimpleName(), name,
              firstType.getSimpleName(), firstName);
        }
      }
    }

    return firstName;
  }

  /**
   * The given annotation may be an instance of {@code Scoped} or it may be
   * annotated with {@code Scoped}.
   */
  static Scoped findScoped(Annotation annotation) {
    Class<? extends Annotation> annotationType = annotation.annotationType();
    return annotationType == Scoped.class
        ? (Scoped) annotation
        : annotationType.getAnnotation(Scoped.class);
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
