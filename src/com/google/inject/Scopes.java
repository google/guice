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

import com.google.inject.internal.Classes;
import com.google.inject.internal.Errors;
import java.lang.annotation.Annotation;

/**
 * Built in scope implementations.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class Scopes {

  private Scopes() {}

  /**
   * One instance per {@link Injector}. Also see {@code @}{@link Singleton}.
   */
  public static final Scope SINGLETON = new Scope() {
    public <T> Provider<T> scope(Key<T> key, final Provider<T> creator) {
      return new Provider<T>() {

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
            synchronized (Injector.class) {
              if (instance == null) {
                instance = creator.get();
              }
            }
          }
          return instance;
        }

        public String toString() {
          return String.format("%s[%s]", creator, SINGLETON);
        }
      };
    }

    @Override public String toString() {
      return "Scopes.SINGLETON";
    }
  };

  /**
   * No scope; the same as not applying any scope at all.  Each time the
   * Injector obtains an instance of an object with "no scope", it injects this
   * instance then immediately forgets it.  When the next request for the same
   * binding arrives it will need to obtain the instance over again.
   *
   * <p>This exists only in case a class has been annotated with a scope
   * annotation such as {@link Singleton @Singleton}, and you need to override
   * this to "no scope" in your binding.
   */
  public static final Scope NO_SCOPE = new Scope() {
    public <T> Provider<T> scope(Key<T> key, Provider<T> unscoped) {
      return unscoped;
    }
    @Override public String toString() {
      return "Scopes.NO_SCOPE";
    }
  };

  /**
   * Returns the scope annotation on {@code type}, or null if none is specified.
   */
  static Class<? extends Annotation> findScopeAnnotation(
      Errors errors, Class<?> implementation) {
    return findScopeAnnotation(errors, implementation.getAnnotations());
  }


  /**
   * Returns the scoping annotation, or null if there isn't one.
   */
  static Class<? extends Annotation> findScopeAnnotation(Errors errors, Annotation[] annotations) {
    Class<? extends Annotation> found = null;

    for (Annotation annotation : annotations) {
      if (annotation.annotationType()
          .isAnnotationPresent(ScopeAnnotation.class)) {
        if (found != null) {
          errors.duplicateScopeAnnotations(found, annotation.annotationType());
        } else {
          found = annotation.annotationType();
        }
      }
    }

    return found;
  }

  static boolean isScopeAnnotation(Annotation annotation) {
    return isScopeAnnotation(annotation.annotationType());
  }

  static boolean isScopeAnnotation(Class<? extends Annotation> annotationType) {
    return annotationType.isAnnotationPresent(ScopeAnnotation.class);
  }

  /**
   * Adds an error if there is a misplaced annotations on {@code type}. Scoping
   * annotations are not allowed on abstract classes or interfaces.
   */
  static void checkForMisplacedScopeAnnotations(Class<?> type, Object source, Errors errors) {
    if (Classes.isConcrete(type)) {
      return;
    }

    Class<? extends Annotation> scopeAnnotation = findScopeAnnotation(errors, type);
    if (scopeAnnotation != null) {
      errors.withSource(type).scopeAnnotationOnAbstractType(scopeAnnotation, type, source);
    }
  }

  /**
   * Scopes an internal factory.
   */
  static <T> InternalFactory<? extends T> scope(Key<T> key,
      InjectorImpl injector, InternalFactory<? extends T> creator,
      Scope scope) {
    // No scope does nothing.
    if (scope == null || scope == Scopes.NO_SCOPE) {
      return creator;
    }
    Provider<T> scoped = scope.scope(key,
        new ProviderToInternalFactoryAdapter<T>(injector, creator));
    return new InternalFactoryToProviderAdapter<T>(scoped);
  }
}
