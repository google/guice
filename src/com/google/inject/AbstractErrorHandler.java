// Copyright 2006 Google Inc. All Rights Reserved.

package com.google.inject;

import java.util.Arrays;
import java.util.Collection;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * Implements formatting. Converts known types to readable strings.
 *
 * @author crazybob@google.com (Bob Lee)
 */
abstract class AbstractErrorHandler implements ErrorHandler {

  public final void handle(String message, Object... arguments) {
    for (int i = 0; i < arguments.length; i++) {
      arguments[i] = convert(arguments[i]);
    }
    handle(String.format(message, arguments));
  }

  static Object convert(Object o) {
    for (Converter<?> converter : converters) {
      if (converter.appliesTo(o)) {
        return converter.convert(o);
      }
    }
    return o;
  }

  @SuppressWarnings("unchecked")
  static Collection<Converter<?>> converters = Arrays.asList(
      new Converter<Method>(Method.class) {
        public String toString(Method m) {
          return "method " + m.getDeclaringClass().getName() + "."
              + m.getName() + "()";
        }
      },
      new Converter<Constructor>(Constructor.class) {
        public String toString(Constructor c) {
          return "constructor " + c.getDeclaringClass().getName() + "()";
        }
      },
      new Converter<Field>(Field.class) {
        public String toString(Field f) {
          return "field " + f.getDeclaringClass().getName() + "."
              + f.getName();
        }
      },
      new Converter<Class>(Class.class) {
        public String toString(Class c) {
          return c.getName();
        }
      },
      new Converter<Key>(Key.class) {
        public String toString(Key k) {
          return k.hasDefaultName()
              ? k.getTypeToken().toString()
              : k.getTypeToken() + " named '" + k.getName() + "'";
        }
      }
  );

  static abstract class Converter<T> {

    final Class<T> type;

    Converter(Class<T> type) {
      this.type = type;
    }

    boolean appliesTo(Object o) {
      return type.isAssignableFrom(o.getClass());
    }

    String convert(Object o) {
      return toString(type.cast(o));
    }

    abstract String toString(T t);
  }
}
