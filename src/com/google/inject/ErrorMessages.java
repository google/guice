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

import com.google.inject.util.StackTraceElements;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Error message templates.
 *
 * @author crazybob@google.com (Bob Lee)
 */
class ErrorMessages {

  private static final String MISSING_BINDING =
      "Binding to %s not found. No bindings to that"
          + " type were found.";

  private static final String MISSING_BINDING_BUT_OTHERS_EXIST =
      "Binding to %s not found. Annotations on other"
          + " bindings to that type include: %s";

  static void handleMissingBinding(ErrorHandler errorHandler, Member member,
      Key<?> key, List<String> otherNames) {
    if (otherNames.isEmpty()) {
      errorHandler.handle(StackTraceElements.forMember(member),
          MISSING_BINDING, key);
    }
    else {
      errorHandler.handle(StackTraceElements.forMember(member),
          MISSING_BINDING_BUT_OTHERS_EXIST, key, otherNames);
    }
  }

  static final String SUBTYPE_NOT_PROVIDED
      = "%s doesn't provide instances of %s.";

  static final String NOT_A_SUBTYPE = "%s doesn't extend %s.";

  static final String RECURSIVE_IMPLEMENTATION_TYPE = "@DefaultImplementation"
      + " points to the same class it annotates.";

  static final String RECURSIVE_PROVIDER_TYPE = "@DefaultProvider"
      + " points to the same class it annotates.";

  static final String ERROR_INJECTING_MEMBERS = "An error occurred"
      + " while injecting members of %s. Error message: %s";

  static final String ERROR_INJECTING_MEMBERS_SEE_LOG = "An error of type %s"
      + " occurred while injecting members of %s. See log for details. Error"
      + " message: %s";

  static final String EXCEPTION_REPORTED_BY_MODULE = "An exception was"
      + " caught and reported. Message: %s";

  static final String EXCEPTION_REPORTED_BY_MODULE_SEE_LOG = "An exception"
      + " was caught and reported. See log for details. Message: %s";

  static final String MISSING_BINDING_ANNOTATION = "Please annotate with"
      + " @BindingAnnotation. Bound at %s.";

  static final String MISSING_RUNTIME_RETENTION = "Please annotate with"
      + " @Retention(RUNTIME). Bound at %s.";

  static final String MISSING_SCOPE_ANNOTATION = "Please annotate with"
      + " @ScopeAnnotation.";

  static final String OPTIONAL_CONSTRUCTOR = "@Inject(optional=true) is"
      + " not allowed on constructors.";

  static final String CONSTANT_CONVERSION_ERROR = "Error converting String"
      + " constant bound at %s to %s: %s";

  static final String CANNOT_BIND_TO_GUICE_TYPE = "Binding to core guice" 
      + " framework type is not allowed: %s.";

  static final String SCOPE_NOT_FOUND = "No scope is bound to %s.";

  static final String SINGLE_INSTANCE_AND_SCOPE = "Setting the scope is not"
      + " permitted when binding to a single instance.";

  static final String CONSTRUCTOR_RULES = "Classes must have either one (and"
      + " only one) constructor annotated with @Inject or a zero-argument"
      + " constructor.";

  static final String MISSING_CONSTRUCTOR = "Could not find a suitable"
      + " constructor in %s. " + CONSTRUCTOR_RULES;

  static final String TOO_MANY_CONSTRUCTORS = "Found more than one constructor"
      + " annotated with @Inject. " + CONSTRUCTOR_RULES;

  static final String DUPLICATE_SCOPES = "Scope %s is already bound to %s."
      + " Cannot bind %s.";

  static final String MISSING_CONSTANT_VALUE = "Missing constant value. Please"
      + " call to(...).";

  static final String CANNOT_INJECT_ABSTRACT_TYPE =
      "Injecting into abstract types is not supported. Please use a concrete"
          + " type instead of %s.";

  static final String ANNOTATION_ALREADY_SPECIFIED =
      "More than one annotation is specified for this binding.";

  static final String IMPLEMENTATION_ALREADY_SET = "Implementation is set more"
      + " than once.";

  static final String SCOPE_ALREADY_SET = "Scope is set more than once.";

  static final String DUPLICATE_ANNOTATIONS = "Found more than one annotation"
      + " annotated with @BindingAnnotation: %s and %s";

  static final String DUPLICATE_SCOPE_ANNOTATIONS = "More than one scope"
      + " annotation was found: %s and %s";

  static final String CONSTANT_VALUE_ALREADY_SET = "Constant value is set more"
      + " than once.";

  static final String RECURSIVE_BINDING = "Binding points to itself.";

  static final String BINDING_ALREADY_SET = "A binding to %s was already"
      + " configured at %s.";

  static final String PRELOAD_NOT_ALLOWED = "Preloading is only supported for"
      + " singleton-scoped bindings.";

  static final String EXCEPTION_WHILE_CREATING = "Error while locating"
      + " instance%n  bound to %s%n  for member at %s";
  
  static final String NULL_PROVIDED = "Null value returned by custom provider"
      + " bound at %s";

  static String getRootMessage(Throwable t) {
    Throwable cause = t.getCause();
    return cause == null
        ? t.toString()
        : getRootMessage(cause);
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
          return k.hasAnnotationType()
              ? k.getTypeLiteral() + " annotated with " + k.getAnnotationName()
              : k.getTypeLiteral().toString();
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
