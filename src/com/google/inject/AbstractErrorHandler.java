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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;

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
  static final Collection<Converter<?>> converters = Arrays.asList(
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
          return "field " + f.getDeclaringClass().getName() + "." + f.getName();
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
              ? k.getType().toString()
              : k.getType() + " named '" + k.getName() + "'";
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
