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

package com.google.inject.matcher;

import com.google.inject.util.Objects;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;

/**
 * Matcher implementations. Supports matching classes and methods.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class Matchers {

  private Matchers() {}

  static Matcher<Object> ANY = new AbstractMatcher<Object>() {
    public boolean matches(Object o) {
      return true;
    }

    public String toString() {
      return "any()";
    }
  };

  /**
   * Returns a matcher which matches any input.
   */
  public static Matcher<Object> any() {
    return ANY;
  }

  /**
   * Inverts the given matcher.
   */
  public static <T> Matcher<T> not(final Matcher<? super T> p) {
    Objects.nonNull(p, "p");
    return new AbstractMatcher<T>() {
      public boolean matches(T t) {
        return !p.matches(t);
      }

      public String toString() {
        return "not(" + p + ")";
      }
    };
  }

  /**
   * Returns a matcher which matches elements (methods, classes, etc.)
   * with a given annotation.
   */
  public static Matcher<AnnotatedElement> annotatedWith(
      final Class<? extends Annotation> annotationType) {
    Objects.nonNull(annotationType, "annotation type");
    return new AbstractMatcher<AnnotatedElement>() {
      public boolean matches(AnnotatedElement element) {
        Annotation annotation = element.getAnnotation(annotationType);
        return annotation != null;
      }

      public String toString() {
        return "annotatedWith(" + annotationType.getSimpleName() + ".class)";
      }
    };
  }

  /**
   * Returns a matcher which matches elements (methods, classes, etc.)
   * with a given annotation.
   */
  public static Matcher<AnnotatedElement> annotatedWith(
      final Annotation annotation) {
    Objects.nonNull(annotation, "annotation");
    return new AbstractMatcher<AnnotatedElement>() {
      public boolean matches(AnnotatedElement element) {
        Annotation fromElement
            = element.getAnnotation(annotation.annotationType());
        return fromElement != null && annotation.equals(fromElement);
      }

      public String toString() {
        return "annotatedWith(" + annotation + ")";
      }
    };
  }

  /**
   * Returns a matcher which matches subclasses of the given type (as well as
   * the given type).
   */
  public static Matcher<Class> subclassesOf(final Class<?> superclass) {
    Objects.nonNull(superclass, "superclass");
    return new AbstractMatcher<Class>() {
      public boolean matches(Class subclass) {
        return superclass.isAssignableFrom(subclass);
      }

      public String toString() {
        return "subclassesOf(" + superclass.getSimpleName() + ".class)";
      }
    };
  }

  /**
   * Returns a matcher which matches objects equal to the given object.
   */
  public static Matcher<Object> only(final Object o) {
    Objects.nonNull(o, "o");
    return new AbstractMatcher<Object>() {
      public boolean matches(Object other) {
        return o.equals(other);
      }

      public String toString() {
        return "only(" + o + ")";
      }
    };
  }

  /**
   * Returns a matcher which matches only the given object.
   */
  public static Matcher<Object> identicalTo(final Object o) {
    Objects.nonNull(o, "o");
    return new AbstractMatcher<Object>() {
      public boolean matches(Object other) {
        return o == other;
      }

      public String toString() {
        return "identicalTo(" + o + ")";
      }
    };
  }

  /**
   * Returns a matcher which matches classes in the given package.
   */
  public static Matcher<Class> inPackage(final Package p) {
    Objects.nonNull(p, "package");
    return new AbstractMatcher<Class>() {
      public boolean matches(Class c) {
        return c.getPackage().equals(p);
      }

      public String toString() {
        return "package(" + p.getName() + ")";
      }
    };
  }

  /**
   * Returns a matcher which matches methods with matching return types.
   */
  public static Matcher<Method> returns(
      final Matcher<? super Class<?>> returnType) {
    Objects.nonNull(returnType, "return type matcher");
    return new AbstractMatcher<Method>() {
      public boolean matches(Method m) {
        return returnType.matches(m.getReturnType());
      }

      public String toString() {
        return "returns(" + returnType + ")";
      }
    };
  }
}
