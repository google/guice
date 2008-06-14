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
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.TypeLiteral;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * An error message.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public final class ErrorMessage {

  static final Collection<Converter<?>> converters = createConverters();

  public static Object convert(Object o) {
    for (Converter<?> converter : converters) {
      if (converter.appliesTo(o)) {
        return converter.convert(o);
      }
    }
    return o;
  }

  @SuppressWarnings("unchecked") // for generic array creation.
  static Collection<Converter<?>> createConverters() {
    return Arrays.asList(new Converter<MatcherAndConverter>(MatcherAndConverter.class) {
      public String toString(MatcherAndConverter m) {
        return m.toString();
      }
    }, new Converter<Method>(Method.class) {
      public String toString(Method m) {
        return "method " + m.getDeclaringClass().getName() + "." + m.getName() + "()";
      }
    }, new Converter<Constructor>(Constructor.class) {
      public String toString(Constructor c) {
        return "constructor " + c.getDeclaringClass().getName() + "()";
      }
    }, new Converter<Field>(Field.class) {
      public String toString(Field f) {
        return "field " + f.getDeclaringClass().getName() + "." + f.getName();
      }
    }, new Converter<Class>(Class.class) {
      public String toString(Class c) {
        return c.getName();
      }
    }, new Converter<Key>(Key.class) {
      public String toString(Key k) {
        StringBuilder result = new StringBuilder();
        result.append(k.getTypeLiteral());
        if (k.getAnnotationType() != null) {
          result.append(" annotated with ");
          result.append(k.getAnnotation() != null ? k.getAnnotation() : k.getAnnotationType());
        }
        return result.toString();
      }
    });
  }

  public static ErrorMessage missingBinding(Object keyOrType) {
    return new ErrorMessage("Binding to %s not found. No bindings to that type were found.",
        keyOrType);
  }

  public static ErrorMessage missingBindingButOthersExist(Key<?> key, List<String> otherNames) {
    return new ErrorMessage(
        "Binding to %s not found. Annotations on other bindings to that type include: %s", key,
        otherNames);
  }

  public static ErrorMessage converterReturnedNull() {
    return new ErrorMessage("Converter returned null.");
  }

  public static ErrorMessage conversionTypeError(Object converted, TypeLiteral<?> type) {
    return new ErrorMessage("Converter returned %s but we expected a[n] %s.", converted, type);
  }

  public static ErrorMessage conversionError(String stringValue, Object source,
      TypeLiteral<?> type, MatcherAndConverter<?> matchingConverter, String message) {
    return new ErrorMessage("Error converting '%s' (bound at %s) to %s using %s. Reason: %s",
        stringValue, source, type, matchingConverter, message);
  }

  public static ErrorMessage ambiguousTypeConversion(String stringValue, TypeLiteral<?> type,
      MatcherAndConverter<?> matchingConverter, MatcherAndConverter<?> converter) {
    return new ErrorMessage("Error converting '%s' to  %s. "
        + "More than one type converter can apply: %s, and %s. "
        + "Please adjust your type converter configuration to avoid  overlapping matches.",
        stringValue, type, matchingConverter, converter);
  }

  public static ErrorMessage bindingNotFound(Key<?> key, String message) {
    return new ErrorMessage("Binding to %s not found: %s", key, message);
  }

  public static ErrorMessage bindingToProvider() {
    return new ErrorMessage("Binding to Provider is not allowed.");
  }

  public static ErrorMessage subtypeNotProvided(Class<? extends Provider<?>> providerType,
      Class<?> type) {
    return new ErrorMessage("%s doesn't provide instances of %s.", providerType, type);
  }

  public static ErrorMessage notASubtype(Class<?> implementationType, Class<?> type) {
    return new ErrorMessage("%s doesn't extend %s.", implementationType, type);
  }

  public static ErrorMessage recursiveImplementationType() {
    return new ErrorMessage("@ImplementedBy points to the same class it annotates.");
  }

  public static ErrorMessage recursiveProviderType() {
    return new ErrorMessage("@ProvidedBy points to the same class it annotates.");
  }

  public static ErrorMessage exceptionReportedByModules(String message) {
    return new ErrorMessage("An exception was caught and reported. Message: %s", message);
  }

  public static ErrorMessage exceptionReportedByModuleSeeLogs(String message) {
    return new ErrorMessage("An exception was caught and reported. "
        + "See log for details. Message: %s", message);
  }

  public static ErrorMessage missingImplementation() {
    return new ErrorMessage("No implementation was specified.");
  }

  public static ErrorMessage missingBindingAnnotation(Object source) {
    return new ErrorMessage("Please annotate with @BindingAnnotation. Bound at %s.", source);
  }

  public static ErrorMessage missingRuntimeRetention(Object source) {
    return new ErrorMessage("Please annotate with @Retention(RUNTIME). Bound at %s.", source);
  }

  public static ErrorMessage missingScopeAnnotation() {
    return new ErrorMessage("Please annotate with @ScopeAnnotation.");
  }

  public static ErrorMessage optionalConstructor() {
    return new ErrorMessage("@Inject(optional=true) is not allowed on constructors.");
  }

//  public static ErrorMessage constantConversionError() {
//    return new ErrorMessage("Error converting String constant bound at %s to %s: %s");
//  }
//
  public static ErrorMessage cannotBindToGuiceType(String simpleName) {
    return new ErrorMessage("Binding to core guice framework type is not allowed: %s.", simpleName);
  }

  public static ErrorMessage cannotBindToNullInstance() {
    return new ErrorMessage("Binding to null instances is not allowed. "
        + "Use toProvider(Providers.of(null)) if this is your intended behaviour.");
  }

  public static ErrorMessage scopeNotFound(String s) {
    return new ErrorMessage("No scope is bound to %s.", s);
  }

  private static final String CONSTRUCTOR_RULES =
      "Classes must have either one (and only one) constructor "
          + "annotated with @Inject or a zero-argument constructor.";

  public static ErrorMessage missingConstructor(Class<?> implementation) {
    return new ErrorMessage("Could not find a suitable constructor in %s. " + CONSTRUCTOR_RULES,
        implementation);
  }

  public static ErrorMessage tooManyConstructors() {
    return new ErrorMessage(
        "Found more than one constructor annotated with @Inject. " + CONSTRUCTOR_RULES);
  }

  public static ErrorMessage duplicateScopes(Scope existing,
      Class<? extends Annotation> annotationType, Scope scope) {
    return new ErrorMessage("Scope %s is already bound to %s. Cannot bind %s.", existing,
        annotationType, scope);
  }

  public static ErrorMessage missingConstantValues() {
    return new ErrorMessage("Missing constant value. Please call to(...).");
  }

  public static ErrorMessage cannotInjectAbstractType(Class<?> type) {
    return new ErrorMessage("Injecting into abstract types is not supported. "
        + "Please use a concrete type instead of %s.", type);
  }

  public static ErrorMessage cannotInjectInnerClass(Class<?> type) {
    return new ErrorMessage("Injecting into inner classes is not supported.  "
        + "Please use a 'static' class (top-level or nested) instead of %s.", type);
  }

  public static ErrorMessage duplicateBindingAnnotations(
      Class<? extends Annotation> a, Class<? extends Annotation> b) {
    return new ErrorMessage(
        "Found more than one annotation annotated with @BindingAnnotation: %s and %s", a, b);
  }

  public static ErrorMessage duplicateScopeAnnotations(
      Class<? extends Annotation> a, Class<? extends Annotation> b) {
    return new ErrorMessage("More than one scope annotation was found: %s and %s", a, b);
  }

  public static ErrorMessage recursiveBinding() {
    return new ErrorMessage("Binding points to itself.");
  }

  public static ErrorMessage bindingAlreadySet(Key<?> key, Object source) {
    return new ErrorMessage("A binding to %s was already configured at %s.", key, source);
  }

  public static ErrorMessage errorInjectingField() {
    return new ErrorMessage("Error injecting field");
  }

  public static ErrorMessage errorInjectingMethod() {
    return new ErrorMessage("Error injecting method");
  }

  public static ErrorMessage errorInjectingConstructor() {
    return new ErrorMessage("Error injecting constructor");
  }

  public static ErrorMessage errorInProvider() {
    return new ErrorMessage("Error in custom provider");
  }

  public static ErrorMessage whileLocatingField(Key key, Object source) {
    return new ErrorMessage("  while locating %s%n    for field at %s", key, source);
  }

  public static ErrorMessage whileLocatingParameter(Key key,
      int parameterIndex, Object source) {
    return new ErrorMessage("  while locating %s%n    for parameter %s at %s",
        key, parameterIndex, source);
  }

  public static ErrorMessage whileLocatingValue(Key key) {
    return new ErrorMessage("  while locating %s", key);
  }

  public static ErrorMessage cannotInjectNull(Object source) {
    return new ErrorMessage("null returned by binding at %s", source);
  }

  public static ErrorMessage cannotInjectNullIntoMember(Object source, Member member) {
    return new ErrorMessage("null returned by binding at %s%n but %s is not @Nullable",
        source, member);
  }

  public static ErrorMessage cannotInjectRawProvider() {
    return new ErrorMessage("Cannot inject a Provider that has no type parameter");
  }

  public static ErrorMessage cannotSatisfyCircularDependency(Class<?> expectedType) {
    return new ErrorMessage(
        "Tried proxying %s to support a circular dependency, but it is not an interface.",
        expectedType);
  }

  private final String formatted;

  private ErrorMessage(String messageFormat, Object... arguments) {
    for (int i = 0; i < arguments.length; i++) {
      arguments[i] = ErrorMessage.convert(arguments[i]);
    }
    this.formatted = String.format(messageFormat, arguments);
  }

  @Override public String toString() {
    return formatted;
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

  public static String getRootMessage(Throwable t) {
    Throwable cause = t.getCause();
    return cause == null ? t.toString() : getRootMessage(cause);
  }
}
