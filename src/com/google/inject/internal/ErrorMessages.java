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

package com.google.inject.internal;

import com.google.inject.Key;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;

/**
 * Error message templates.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class ErrorMessages {

  static final Collection<Converter<?>> converters = createConverters();

  public static Object convert(Object o) {
    for (Converter<?> converter : converters) {
      if (converter.appliesTo(o)) {
        return converter.convert(o);
      }
    }
    return o;
  }

  public static String format(String message, Object... arguments) {
    for (int i = 0; i < arguments.length; i++) {
      arguments[i] = ErrorMessages.convert(arguments[i]);
    }
    return String.format(message, arguments);
  }
  
  @SuppressWarnings("unchecked") // for generic array creation.
  static Collection<Converter<?>> createConverters() {
    return Arrays.asList(
      new Converter<MatcherAndConverter>(MatcherAndConverter.class) {
        public String toString(MatcherAndConverter m) {
          return m.toString();
        }
      },
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
          StringBuilder result = new StringBuilder();
          result.append(k.getTypeLiteral());
          if (k.getAnnotationType() != null) {
            result.append(" annotated with ");
            result.append(k.getAnnotation() != null ? k.getAnnotation() : k.getAnnotationType());
          }
          return result.toString();
        }
      }
    );
  }

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

  public static final String MISSING_BINDING =
      "Binding to %s not found. No bindings to that"
          + " type were found.";

  public static final String MISSING_BINDING_BUT_OTHERS_EXIST =
      "Binding to %s not found. Annotations on other"
          + " bindings to that type include: %s";

  public static final String CONVERTER_RETURNED_NULL
      = "Converter returned null.";

  public static final String CONVERSION_TYPE_ERROR
      = "Converter returned %s but we expected a[n] %s.";

  public static final String CONVERSION_ERROR = "Error converting '%s'"
      + " (bound at %s) to %s using %s. Reason: %s";

  public static final String AMBIGUOUS_TYPE_CONVERSION = "Error converting '%s' to "
      + " %s. More than one type converter can apply: %s, and"
      + " %s. Please adjust your type converter configuration to avoid "
      + " overlapping matches.";

  public static final String BINDING_NOT_FOUND = "Binding to %s not found.";

  public static final String LOGGER_ALREADY_BOUND = "Logger is already bound.";

  public static final String BINDING_TO_PROVIDER
      = "Binding to Provider is not allowed.";

  public static final String SUBTYPE_NOT_PROVIDED
      = "%s doesn't provide instances of %s.";

  public static final String NOT_A_SUBTYPE = "%s doesn't extend %s.";

  public static final String RECURSIVE_IMPLEMENTATION_TYPE = "@ImplementedBy"
      + " points to the same class it annotates.";

  public static final String RECURSIVE_PROVIDER_TYPE = "@ProvidedBy"
      + " points to the same class it annotates.";

  public static final String ERROR_INJECTING_MEMBERS_SEE_LOG = "An error of type %s"
      + " occurred while injecting members of %s. See log for details. Error"
      + " message: %s";

  public static final String EXCEPTION_REPORTED_BY_MODULE = "An exception was"
      + " caught and reported. Message: %s";

  public static final String EXCEPTION_REPORTED_BY_MODULE_SEE_LOG = "An exception"
      + " was caught and reported. See log for details. Message: %s";

  public static final String MISSING_IMPLEMENTATION
      = "No implementation was specified.";

  public static final String MISSING_BINDING_ANNOTATION = "Please annotate with"
      + " @BindingAnnotation. Bound at %s.";

  public static final String MISSING_RUNTIME_RETENTION = "Please annotate with"
      + " @Retention(RUNTIME). Bound at %s.";

  public static final String MISSING_SCOPE_ANNOTATION = "Please annotate with"
      + " @ScopeAnnotation.";

  public static final String OPTIONAL_CONSTRUCTOR = "@Inject(optional=true) is"
      + " not allowed on constructors.";

  public static final String CONSTANT_CONVERSION_ERROR = "Error converting String"
      + " constant bound at %s to %s: %s";

  public static final String CANNOT_BIND_TO_GUICE_TYPE = "Binding to core guice"
      + " framework type is not allowed: %s.";

  public static final String CANNOT_BIND_TO_NULL_INSTANCE = "Binding to null "
      + "instances is not allowed. Use toProvider(Providers.of(null)) if this "
      + "is your intended behaviour.";

  public static final String SCOPE_NOT_FOUND = "No scope is bound to %s.";

  public static final String CONSTRUCTOR_RULES = "Classes must have either one (and"
      + " only one) constructor annotated with @Inject or a zero-argument"
      + " constructor.";

  public static final String MISSING_CONSTRUCTOR = "Could not find a suitable"
      + " constructor in %s. " + CONSTRUCTOR_RULES;

  public static final String TOO_MANY_CONSTRUCTORS = "Found more than one constructor"
      + " annotated with @Inject. " + CONSTRUCTOR_RULES;

  public static final String DUPLICATE_SCOPES = "Scope %s is already bound to %s."
      + " Cannot bind %s.";

  public static final String MISSING_CONSTANT_VALUE = "Missing constant value. Please"
      + " call to(...).";

  public static final String CANNOT_INJECT_ABSTRACT_TYPE = "Injecting into abstract"
      + " types is not supported. Please use a concrete type instead of %s.";

  public static final String CANNOT_INJECT_INNER_CLASS = "Injecting into inner"
      + " classes is not supported.  Please use a 'static' class (top-level or"
      + " nested) instead.";

  public static final String DUPLICATE_BINDING_ANNOTATIONS =
      "Found more than one annotation annotated with @BindingAnnotation:"
          + " %s and %s";

  public static final String DUPLICATE_SCOPE_ANNOTATIONS = "More than one scope"
      + " annotation was found: %s and %s";

  public static final String RECURSIVE_BINDING = "Binding points to itself.";

  public static final String BINDING_ALREADY_SET = "A binding to %s was already"
      + " configured at %s.";

  public static final String PRELOAD_NOT_ALLOWED = "Preloading is only supported for"
      + " singleton-scoped bindings.";

  public static final String ERROR_INJECTING_FIELD = "Error injecting field";

  public static final String ERROR_INJECTING_METHOD = "Error injecting method";

  public static final String ERROR_INJECTING_CONSTRUCTOR =
      "Error injecting constructor";

  public static final String ERROR_IN_PROVIDER = "Error in custom provider";

  public static final String ERROR_WHILE_LOCATING_FIELD =
      "  while locating %s%n    for field at %s";

  public static final String ERROR_WHILE_LOCATING_PARAMETER =
      "  while locating %s%n    for parameter %s at %s";

  public static final String ERROR_WHILE_LOCATING_VALUE =
      "  while locating %s";

  public static final String CANNOT_INJECT_NULL =
      "null returned by binding at %s";

  public static final String CANNOT_INJECT_NULL_INTO_MEMBER =
      "null returned by binding at %s%n but %s is not @Nullable";

  public static final String CANNOT_INJECT_RAW_PROVIDER =
      "Cannot inject a Provider that has no type parameter";

  public static String getRootMessage(Throwable t) {
    Throwable cause = t.getCause();
    return cause == null
        ? t.toString()
        : getRootMessage(cause);
  }
}
