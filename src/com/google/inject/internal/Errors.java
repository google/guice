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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.CreationException;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.Message;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.List;

/**
 * A collection of error messages. If this type is passed as a method parameter, the method is
 * considered to have executed succesfully only if new errors were not added to this collection.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public final class Errors implements Serializable {

  // TODO(kevinb): gee, ya think we might want to remove this?
  private static final boolean allowNullsBadBadBad
      = "I'm a bad hack".equals(System.getProperty("guice.allow.nulls.bad.bad.bad"));

  // TODO: Provide a policy on what the source line should be for a given member.
  //       Should we prefer the line where the binding was made?
  //       Should we prefer the member?
  // For example, if we bind a class with two scope annotations, is that a problem
  // with the binding, or a problem with the bound class? The catch being that if it's
  // a problem with the binding, then we report a different source line if the class
  // is retrieved via a JIT binding.
  //
  // What about a missing implementation? Is that at the caller?
  // What about injection points?

  /** the stacktrace or member that will be the reference location for new errors */
  private final Object source;

  /** false indicates that new errors should not be added */
  private boolean isMutable = true;
  private final List<Message> errors;
  private final List<Dependency> dependencies;

  public Errors() {
    this(SourceProvider.UNKNOWN_SOURCE);
  }

  public Errors(Object source) {
    this.source = source;
    isMutable = true;
    errors = Lists.newArrayList();
    dependencies = Lists.newArrayList();
  }

  public Errors(Errors parent, Object source) {
    this.source = source;
    isMutable = parent.isMutable;
    errors = parent.errors;
    dependencies = Lists.newArrayList(parent.dependencies);
  }

  public Errors userReportedError(String messageFormat, List<Object> arguments) {
    return addMessage(messageFormat, arguments);
  }

  public void pushInjectionPoint(Dependency<?> dependency) {
    dependencies.add(dependency);
  }

  public void popInjectionPoint(Dependency<?> dependency) {
    Dependency popped = dependencies.remove(dependencies.size() - 1);
    checkArgument(dependency == popped);
  }

  /**
   * Returns a new instance that uses {@code source} as a reference point for
   * newly added errors.
   */
  public Errors withSource(Object source) {
    return new Errors(this, source);
  }

  /**
   * We use a fairly generic error message here. The motivation is to share the
   * same message for both bind time errors:
   * <pre><code>Guice.createInjector(new AbstractModule() {
   *   public void configure() {
   *     bind(Runnable.class);
   *   }
   * }</code></pre>
   * ...and at provide-time errors:
   * <pre><code>Guice.createInjector().getInstance(Runnable.class);</code></pre>
   * Otherwise we need to know who's calling when resolving a just-in-time
   * binding, which makes things unnecessarily complex.
   */
  public Errors missingImplementation(Object keyOrType) {
    return addMessage("No implementation for %s was bound.", keyOrType);
  }

  public Errors converterReturnedNull(String stringValue, Object source,
      TypeLiteral<?> type, MatcherAndConverter matchingConverter) {
    return addMessage("Received null converting '%s' (bound at %s) to %s%n"
        + " using %s.",
        stringValue, source, type, matchingConverter);
  }

  public Errors conversionTypeError(String stringValue, Object source, TypeLiteral<?> type,
      MatcherAndConverter matchingConverter, Object converted) {
    return addMessage("Type mismatch converting '%s' (bound at %s) to %s%n"
        + " using %s.%n"
        + " Converter returned %s.",
        stringValue, source, type, matchingConverter, converted);
  }

  public Errors conversionError(String stringValue, Object source,
      TypeLiteral<?> type, MatcherAndConverter matchingConverter, Exception cause) {
    return addMessage(cause, "Error converting '%s' (bound at %s) to %s%n" 
        + " using %s.%n"
        + " Reason: %s",
        stringValue, source, type, matchingConverter, cause);
  }

  public Errors ambiguousTypeConversion(String stringValue, Object source, TypeLiteral<?> type,
      MatcherAndConverter a, MatcherAndConverter b) {
    return addMessage("Multiple converters can convert '%s' (bound at %s) to %s:%n"
        + " %s and%n"
        + " %s.%n"
        + " Please adjust your type converter configuration to avoid overlapping matches.",
        stringValue, source, type, a, b);
  }

  public Errors bindingToProvider() {
    return addMessage("Binding to Provider is not allowed.");
  }

  public Errors subtypeNotProvided(Class<? extends Provider<?>> providerType,
      Class<?> type) {
    return addMessage("%s doesn't provide instances of %s.", providerType, type);
  }

  public Errors notASubtype(Class<?> implementationType, Class<?> type) {
    return addMessage("%s doesn't extend %s.", implementationType, type);
  }

  public Errors recursiveImplementationType() {
    return addMessage("@ImplementedBy points to the same class it annotates.");
  }

  public Errors recursiveProviderType() {
    return addMessage("@ProvidedBy points to the same class it annotates.");
  }

  public Errors missingRuntimeRetention(Object source) {
    return addMessage("Please annotate with @Retention(RUNTIME).%n"
        + " Bound at %s.", source);
  }

  public Errors missingScopeAnnotation() {
    return addMessage("Please annotate with @ScopeAnnotation.");
  }

  public Errors optionalConstructor(Constructor constructor) {
    return addMessage("%s is annotated @Inject(optional=true), "
        + "but constructors cannot be optional.", constructor);
  }

  public Errors cannotBindToGuiceType(String simpleName) {
    return addMessage("Binding to core guice framework type is not allowed: %s.", simpleName);
  }

  public Errors cannotBindToNullInstance() {
    return addMessage("Binding to null instances is not allowed. "
        + "Use toProvider(Providers.of(null)) if this is your intended behaviour.");
  }

  public Errors scopeNotFound(Class<? extends Annotation> scopeAnnotation) {
    return addMessage("No scope is bound to %s.", scopeAnnotation);
  }

  public Errors scopeAnnotationOnAbstractType(
      Class<? extends Annotation> scopeAnnotation, Class<?> type, Object source) {
    return addMessage("%s is annotated with %s, but scope annotations are not supported "
        + "for abstract types.%n Bound at %s.", type, scopeAnnotation, source);
  }

  public Errors misplacedBindingAnnotation(Member member, Annotation bindingAnnotation) {
    return addMessage("%s is annotated with %s, but binding annotations should be applied "
        + "to its parameters instead.", member, bindingAnnotation);
  }

  private static final String CONSTRUCTOR_RULES =
      "Classes must have either one (and only one) constructor "
          + "annotated with @Inject or a zero-argument constructor that is not private.";

  public Errors missingConstructor(Class<?> implementation) {
    return addMessage("Could not find a suitable constructor in %s. " + CONSTRUCTOR_RULES,
        implementation);
  }

  public Errors tooManyConstructors(Class<?> implementation) {
    return addMessage("%s has more than one constructor annotated with @Inject. "
        + CONSTRUCTOR_RULES, implementation);
  }

  public Errors duplicateScopes(Scope existing,
      Class<? extends Annotation> annotationType, Scope scope) {
    return addMessage("Scope %s is already bound to %s. Cannot bind %s.", existing,
        annotationType, scope);
  }

  public Errors missingConstantValues() {
    return addMessage("Missing constant value. Please call to(...).");
  }

  public Errors cannotInjectInnerClass(Class<?> type) {
    return addMessage("Injecting into inner classes is not supported.  "
        + "Please use a 'static' class (top-level or nested) instead of %s.", type);
  }

  public Errors duplicateBindingAnnotations(Member member,
      Class<? extends Annotation> a, Class<? extends Annotation> b) {
    return addMessage("%s has more than one annotation annotated with @BindingAnnotation: "
        + "%s and %s", member, a, b);
  }

  public Errors duplicateScopeAnnotations(
      Class<? extends Annotation> a, Class<? extends Annotation> b) {
    return addMessage("More than one scope annotation was found: %s and %s", a, b);
  }

  public Errors recursiveBinding() {
    return addMessage("Binding points to itself.");
  }

  public Errors bindingAlreadySet(Key<?> key, Object source) {
    return addMessage("A binding to %s was already configured at %s.", key, source);
  }

  public Errors errorInjectingMethod(Throwable cause) {
    return addMessage(cause, "Error injecting method, %s", cause);
  }

  public Errors errorInjectingConstructor(Throwable cause) {
    return addMessage(cause, "Error injecting constructor, %s", cause);
  }

  public Errors errorInProvider(RuntimeException runtimeException, Errors errorsFromException) {
    if (errorsFromException != null) {
      return merge(errorsFromException);
    } else {
      return addMessage(runtimeException, "Error in custom provider, %s", runtimeException);
    }
  }

  public Errors cannotInjectRawProvider() {
    return addMessage("Cannot inject a Provider that has no type parameter");
  }

  public Errors cannotSatisfyCircularDependency(Class<?> expectedType) {
    return addMessage(
        "Tried proxying %s to support a circular dependency, but it is not an interface.",
        expectedType);
  }

  public Errors makeImmutable() {
    isMutable = false;
    return this;
  }

  public void throwCreationExceptionIfErrorsExist() {
    if (!hasErrors()) {
      return;
    }

    makeImmutable();
    throw new CreationException(getMessages());
  }

  public Errors merge(Errors moreErrors) {
    checkState(isMutable);

    if (moreErrors.errors != this.errors) {
      for (Message message : moreErrors.errors) {
        List<Dependency> dependencies = Lists.newArrayList();
        dependencies.addAll(this.dependencies);
        dependencies.addAll(message.getDependencies());
        Object source = message.getSource() != SourceProvider.UNKNOWN_SOURCE
            ? message.getSource()
            : this.source;
        errors.add(new Message(source, message.getMessage(), dependencies, message.getCause()));
      }
    }

    return this;
  }

  public void throwIfNecessary() throws ErrorsException {
    if (!hasErrors()) {
      return;
    }

    throw toException();
  }

  public ErrorsException toException() {
    return new ErrorsException(this);
  }

  public boolean hasErrors() {
    return !errors.isEmpty();
  }

  private Errors addMessage(String messageFormat, Object... arguments) {
    return addMessage(null, messageFormat, arguments);
  }

  private Errors addMessage(Throwable cause, String messageFormat, Object... arguments) {
    String message = format(messageFormat, arguments);
    addMessage(new Message(source, message, ImmutableList.copyOf(dependencies), cause));
    return this;
  }

  public Errors addMessage(Message message) {
    if (!isMutable) {
      throw new AssertionError();
    }

    errors.add(message);
    return this;
  }

  private String format(String messageFormat, Object... arguments) {
    for (int i = 0; i < arguments.length; i++) {
      arguments[i] = Errors.convert(arguments[i]);
    }
    return String.format(messageFormat, arguments);
  }

  public List<Message> getMessages() {
    List<Message> result = Lists.newArrayList(errors);

    Collections.sort(result, new Comparator<Message>() {
      public int compare(Message a, Message b) {
        return a.getSource().compareTo(b.getSource());
      }
    });

    return result;
  }

  public static String format(String heading, Collection<? extends Message> errorMessages) {
    Formatter fmt = new Formatter().format(heading).format(":%n%n");
    int index = 1;
    for (Message errorMessage : errorMessages) {
      fmt.format("%s) Error at %s:%n", index++, errorMessage.getSource())
         .format(" %s%n", errorMessage.getMessage());

      List<Dependency> dependencies = errorMessage.getDependencies();
      for (int i = dependencies.size() - 1; i >= 0; i--) {
        Dependency dependency = dependencies.get(i);

        Key key = dependency.getKey();
        fmt.format("  while locating %s%n", convert(key));

        InjectionPoint injectionPoint = dependency.getInjectionPoint();
        if (injectionPoint == null) {
          continue;
        }

        Member member = injectionPoint.getMember();
        Class<? extends Member> memberType = MoreTypes.memberType(member);
        if (memberType == Field.class) {
          fmt.format("    for field at %s%n", StackTraceElements.forMember(member));

        } else if (memberType == Method.class || memberType == Constructor.class) {
          fmt.format("    for parameter %s at %s%n",
              dependency.getParameterIndex(), StackTraceElements.forMember(member));
        }
      }

      fmt.format("%n");
    }

    return fmt.format("%s error[s]", errorMessages.size()).toString();
  }

  /**
   * Returns {@code value} if it is non-null allowed to be null. Otherwise a message is added and
   * an {@code ErrorsException} is thrown.
   */
  public <T> T checkForNull(T value, Object source, Dependency<?> dependency)
      throws ErrorsException {
    if (value != null
        || dependency.isNullable()
        || allowNullsBadBadBad) {
      return value;
    }

    int parameterIndex = dependency.getParameterIndex();
    String parameterName = (parameterIndex != -1)
        ? "parameter " + parameterIndex + " of "
        : "";
    addMessage("null returned by binding at %s%n but %s%s is not @Nullable",
        source, parameterName, dependency.getInjectionPoint().getMember());

    throw toException();
  }

  private static abstract class Converter<T> {

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

  private static final Collection<Converter<?>> converters = ImmutableList.of(
      new Converter<MatcherAndConverter>(MatcherAndConverter.class) {
        public String toString(MatcherAndConverter m) {
          return m.toString();
        }
      },
      new Converter<Class>(Class.class) {
        public String toString(Class c) {
          return c.getName();
        }
      },
      new Converter<Member>(Member.class) {
        public String toString(Member member) {
          return MoreTypes.toString(member);
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
      });

  public static Object convert(Object o) {
    for (Converter<?> converter : converters) {
      if (converter.appliesTo(o)) {
        return converter.convert(o);
      }
    }
    return o;
  }

  /**
   * This method returns a String that indicates an element source. We do a
   * best effort to include a line number in this String.
   */
  public static String sourceToString(Object source) {
    checkNotNull(source, "source");

    if (source instanceof Member) {
      return StackTraceElements.forMember((Member) source).toString();
    } else if (source instanceof Class) {
      return StackTraceElements.forType(((Class<?>) source)).toString();
    } else {
      return convert(source).toString();
    }
  }
}
