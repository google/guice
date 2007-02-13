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

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Collection;
import java.util.Arrays;

/**
 * Error message templates.
 *
 * @author crazybob@google.com (Bob Lee)
 */
class ErrorMessages {

  private static final String MISSING_BINDING =
      "Binding to %s not found, but %s requires it. No bindings to that"
          + " type were found.";

  private static final String MISSING_BINDING_BUT_OTHERS_EXIST =
      "Binding to %s not found, but %s requires it. Names of other"
          + " bindings to that type: %s";

  static void handleMissingBinding(ErrorHandler errorHandler, Member member,
      Key<?> key, List<String> otherNames) {
    if (otherNames.isEmpty()) {
      errorHandler.handle(MISSING_BINDING, key, member);
    }
    else {
      errorHandler.handle(
          MISSING_BINDING_BUT_OTHERS_EXIST, key, member, otherNames);
    }
  }

  static final String SINGLE_INSTANCE_AND_SCOPE = "Setting the scope is not"
      + " permitted when binding to a single instance.";

  static final String CONSTRUCTOR_RULES = "Classes must have either one (and"
      + " only one) constructor annotated with @Inject or a zero-argument"
      + " constructor.";

  static final String MISSING_CONSTRUCTOR = "Could not find a suitable"
      + " constructor in %s. " + CONSTRUCTOR_RULES;

  static final String TOO_MANY_CONSTRUCTORS = "More than one constructor"
      + " annotated with @Inject found in %s. " + CONSTRUCTOR_RULES;

  static final String DUPLICATE_SCOPES = "A scope named '%s' already exists.";

  static final String MISSING_CONSTANT_VALUE = "Missing constant value. Please"
      + " call to(...).";

  static final String MISSING_LINK_DESTINATION = "Missing link destination."
      + " Please call to(Key<?>).";

  static final String LINK_DESTINATION_NOT_FOUND = "Binding to %s not found.";

  static final String CANNOT_INJECT_INTERFACE = "Injecting into interfaces is"
      + " not supported. Please use a concrete type instead of %s.";

  static final String NAME_ALREADY_SET = "Binding name is set more than once.";

  static final String IMPLEMENTATION_ALREADY_SET = "Implementation is set more"
      + " than once.";

  static final String SCOPE_NOT_FOUND = "Scope named '%s' not found."
      + " Available scope names: %s";

  static final String SCOPE_ALREADY_SET = "Scope is set more than once."
      + " You can set the scope by calling in(...), by annotating the"
      + " implementation class with @Scoped, or by annotating the"
      + " implementation with with an annotation which is annotated with"
      + " @Scoped.";

  static final String SCOPE_ALREADY_SET_BY_ANNOTATION = "Scope is set more than"
      + " once by annotations on %s. @%s says the scope should be '%s'"
      + " while @%s says it should be '%s'.";

  static final String CONSTANT_VALUE_ALREADY_SET = "Constant value is set more"
      + " than once.";

  static final String LINK_DESTINATION_ALREADY_SET = "Link destination is"
      + " set more than once.";

  static final String BINDING_ALREADY_SET = "A binding to %s was already"
      + " configured at %s.";

  static final String NAME_ON_MEMBER_WITH_MULTIPLE_PARAMS = "Member-level"
      + " @Inject name is not allowed when the member has more than one"
      + " parameter: %s";

  static final String NAME_ON_MEMBER_AND_PARAMETER = "Ambiguous binding name"
      + " between @Inject on member and parameter: %s. Please remove the name"
      + " from the member-level @Inject or remove @Inject from the parameter.";

  static final String PRELOAD_NOT_ALLOWED = "Preloading is only supported for"
      + " container-scoped bindings.";

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
