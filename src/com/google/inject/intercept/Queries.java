// Copyright 2006 Google Inc. All Rights Reserved.

package com.google.inject.intercept;

import com.google.inject.util.Objects;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;

/**
 * Query implementations.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class Queries {

  private Queries() {}

  static Query<?> ANY = new AbstractQuery<Object>() {
    public boolean matches(Object o) {
      return true;
    }
  };

  /**
   * Returns a query which matches any input.
   */
  @SuppressWarnings({"unchecked"})
  public static <T> Query<T> any() {
    return (Query<T>) ANY;
  }

  /**
   * Inverts the given query.
   */
  public static <T> Query<T> not(final Query<? super T> p) {
    Objects.nonNull(p, "p");
    return new AbstractQuery<T>() {
      public boolean matches(T t) {
        return !p.matches(t);
      }
    };
  }

  /**
   * Returns a query which matches elements (methods, classes, etc.)
   * with a given annotation.
   */
  public static Query<AnnotatedElement> annotatedWith(
      final Class<? extends Annotation> annotationType) {
    Objects.nonNull(annotationType, "annotation type");
    return new AbstractQuery<AnnotatedElement>() {
      public boolean matches(AnnotatedElement element) {
        Annotation annotation = element.getAnnotation(annotationType);
        return annotation != null;
      }
    };
  }

  /**
   * Returns a query which matches subclasses of the given type.
   */
  public static Query<Class> subclassesOf(final Class<?> superclass) {
    Objects.nonNull(superclass, "superclass");
    return new AbstractQuery<Class>() {
      public boolean matches(Class subclass) {
        return superclass.isAssignableFrom(subclass);
      }
    };
  }

  /**
   * Returns a query which matches objects equal to the given object.
   */
  public static <T> Query<T> equalTo(final T t) {
    Objects.nonNull(t, "t");
    return new AbstractQuery<T>() {
      public boolean matches(T other) {
        return t.equals(other);
      }
    };
  }

  /**
   * Returns a query which matches only the given object.
   */
  public static <T> Query<T> sameAs(final T t) {
    Objects.nonNull(t, "t");
    return new AbstractQuery<T>() {
      public boolean matches(T other) {
        return t == other;
      }
    };
  }

  /**
   * Returns a query which matches classes in the given package.
   */
  public static Query<Class> inPackage(final Package p) {
    Objects.nonNull(p, "package");
    return new AbstractQuery<Class>() {
      public boolean matches(Class c) {
        return c.getPackage().equals(p);
      }
    };
  }

// Can't get the types to work.
//
//  /**
//   * Returns a predicate which matches methods with matching return types.
//   */
//  public static Query<Method> returns(
//      final Query<? super Class<?>> returnType) {
//    Objects.nonNull(returnType, "return type query");
//    return new AbstractQuery<Method>() {
//      public boolean matches(Method m) {
//        return returnType.matches(m.getReturnType());
//      }
//    };
//  }
}
