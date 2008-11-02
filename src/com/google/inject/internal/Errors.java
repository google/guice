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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.ConfigurationException;
import com.google.inject.CreationException;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Scope;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.Message;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.Iterator;
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

  private List<Message> errors;
  private final List<Object> sources;

  public Errors() {
    sources = Lists.newArrayList();
    errors = Lists.newArrayList();
  }

  public Errors(Object source) {
    sources = Lists.newArrayList(source);
    errors = Lists.newArrayList();
  }

  private Errors(Errors parent, Object source) {
    errors = parent.errors;
    sources = Lists.newArrayList(parent.sources);
    sources.add(source);
  }

  /**
   * Returns an instance that uses {@code source} as a reference point for newly added errors.
   */
  public Errors withSource(Object source) {
    return source == SourceProvider.UNKNOWN_SOURCE
        ? this
        : new Errors(this, source);
  }

  public void pushSource(Object source) {
    sources.add(source);
  }

  public void popSource(Object source) {
    Object popped = sources.remove(sources.size() - 1);
    checkArgument(source == popped);
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
        stringValue, sourceToString(source), type, matchingConverter);
  }

  public Errors conversionTypeError(String stringValue, Object source, TypeLiteral<?> type,
      MatcherAndConverter matchingConverter, Object converted) {
    return addMessage("Type mismatch converting '%s' (bound at %s) to %s%n"
        + " using %s.%n"
        + " Converter returned %s.",
        stringValue, sourceToString(source), type, matchingConverter, converted);
  }

  public Errors conversionError(String stringValue, Object source,
      TypeLiteral<?> type, MatcherAndConverter matchingConverter, Exception cause) {
    return addMessage(cause, "Error converting '%s' (bound at %s) to %s%n" 
        + " using %s.%n"
        + " Reason: %s",
        stringValue, sourceToString(source), type, matchingConverter, cause);
  }

  public Errors ambiguousTypeConversion(String stringValue, Object source, TypeLiteral<?> type,
      MatcherAndConverter a, MatcherAndConverter b) {
    return addMessage("Multiple converters can convert '%s' (bound at %s) to %s:%n"
        + " %s and%n"
        + " %s.%n"
        + " Please adjust your type converter configuration to avoid overlapping matches.",
        stringValue, sourceToString(source), type, a, b);
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
        + " Bound at %s.", sourceToString(source));
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

  public Errors scopeNotFound(Class<? extends Annotation> scopeAnnotation) {
    return addMessage("No scope is bound to %s.", scopeAnnotation);
  }

  public Errors scopeAnnotationOnAbstractType(
      Class<? extends Annotation> scopeAnnotation, Class<?> type, Object source) {
    return addMessage("%s is annotated with %s, but scope annotations are not supported "
        + "for abstract types.%n Bound at %s.", type, scopeAnnotation, sourceToString(source));
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
    return addMessage("A binding to %s was already configured at %s.", key, sourceToString(source));
  }

  public Errors childBindingAlreadySet(Key<?> key) {
    return addMessage("A binding to %s already exists on a child injector.", key);
  }

  public Errors errorInjectingMethod(Throwable cause) {
    return errorInUserCode("Error injecting method, %s", cause);
  }

  public Errors errorInjectingConstructor(Throwable cause) {
    return errorInUserCode("Error injecting constructor, %s", cause);
  }

  public Errors errorInProvider(RuntimeException runtimeException) {
    return errorInUserCode("Error in custom provider, %s", runtimeException);
  }

  public Errors errorInUserCode(String message, Throwable cause) {
    if (cause instanceof ProvisionException) {
      return merge(((ProvisionException) cause).getErrorMessages());

    } else if (cause instanceof ConfigurationException) {
      return merge(((ConfigurationException) cause).getErrorMessages());

    } else if (cause instanceof CreationException) {
      return merge(((CreationException) cause).getErrorMessages());

    } else {
      return addMessage(cause, message, cause);
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
    errors = ImmutableList.copyOf(errors);
    return this;
  }

  public void throwConfigurationExceptionIfNecessary() {
    if (!hasErrors()) {
      return;
    }

    throw new ConfigurationException(getMessages());
  }

  public void throwProvisionExceptionIfErrorsExist() {
    if (!hasErrors()) {
      return;
    }

    throw new ProvisionException(getMessages());
  }

  public void throwCreationExceptionIfErrorsExist() {
    if (!hasErrors()) {
      return;
    }

    makeImmutable();
    throw new CreationException(getMessages());
  }

  private Message merge(Message message) {
    List<Object> sources = Lists.newArrayList();
    sources.addAll(this.sources);
    sources.addAll(message.getSources());
    return new Message(stripDuplicates(sources), message.getMessage(), message.getCause());
  }

  public Errors merge(Collection<Message> messages) {
    if (messages != this.errors) {
      for (Message message : messages) {
        errors.add(merge(message));
      }
    }
    return this;
  }

  public Errors merge(Errors moreErrors) {
    merge(moreErrors.errors);
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
    addMessage(new Message(stripDuplicates(sources), message, cause));
    return this;
  }

  public Errors addMessage(Message message) {
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

  /** Returns the formatted message for an exception with the specified messages. */
  public static String format(String heading, Collection<Message> errorMessages) {
    Formatter fmt = new Formatter().format(heading).format(":%n%n");
    int index = 1;
    boolean displayCauses = getOnlyCause(errorMessages) == null;

    for (Message errorMessage : errorMessages) {
      fmt.format("%s) %s%n", index++, errorMessage.getMessage());

      List<Object> dependencies = errorMessage.getSources();
      for (int i = dependencies.size() - 1; i >= 0; i--) {
        Object source = dependencies.get(i);

        if (source instanceof Dependency) {
          Dependency<?> dependency = (Dependency<?>) source;

          InjectionPoint injectionPoint = dependency.getInjectionPoint();
          if (injectionPoint != null) {
            Member member = injectionPoint.getMember();
            Class<? extends Member> memberType = MoreTypes.memberType(member);
            if (memberType == Field.class) {
              fmt.format("  for field at %s%n", StackTraceElements.forMember(member));
            } else if (memberType == Method.class || memberType == Constructor.class) {
              fmt.format("  for parameter %s at %s%n",
                  dependency.getParameterIndex(), StackTraceElements.forMember(member));
            } else {
              throw new AssertionError();
            }
          } else {
            fmt.format("  while locating %s%n", convert(dependency.getKey()));
          }
        }

        fmt.format("  at %s%n", sourceToString(source));
      }

      Throwable cause = errorMessage.getCause();
      if (displayCauses && cause != null) {
        StringWriter writer = new StringWriter();
        cause.printStackTrace(new PrintWriter(writer));
        fmt.format("Caused by: %s", writer.getBuffer());
      }

      fmt.format("%n");
    }

    if (errorMessages.size() == 1) {
      fmt.format("1 error");
    } else {
      fmt.format("%s errors", errorMessages.size());
    }

    return fmt.toString();
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

  /**
   * Returns the cause throwable if there is exactly one cause in {@code messages}. If there are
   * zero or multiple messages with causes, null is returned.
   */
  public static Throwable getOnlyCause(Collection<Message> messages) {
    Throwable onlyCause = null;
    for (Message message : messages) {
      Throwable messageCause = message.getCause();
      if (messageCause == null) {
        continue;
      }

      if (onlyCause != null) {
        return null;
      }

      onlyCause = messageCause;
    }

    return onlyCause;
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

    if (source instanceof InjectionPoint) {
      return sourceToString(((InjectionPoint) source).getMember());
    } else if (source instanceof Member) {
      return StackTraceElements.forMember((Member) source).toString();
    } else if (source instanceof Class) {
      return StackTraceElements.forType(((Class<?>) source)).toString();
    } else {
      return convert(source).toString();
    }
  }
  
  /**
   * Removes consecutive duplicates, so that [A B B C D A] becomes [A B C D A].
   */
  private <T> List<T> stripDuplicates(List<T> list) {
    list = Lists.newArrayList(list);

    Iterator i = list.iterator();
    if (i.hasNext()) {
      for (Object last = i.next(), current; i.hasNext(); last = current) {
        current = i.next();
        if (last.equals(current)) {
          i.remove();
        }
      }
    }

    return ImmutableList.copyOf(list);
  }
}
