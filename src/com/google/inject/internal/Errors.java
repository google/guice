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
import static com.google.common.base.Preconditions.checkState;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Scope;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.Message;
import com.google.inject.spi.SourceProviders;
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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A collection of error messages.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public final class Errors implements Serializable {

  private static final Logger logger = Logger.getLogger(Guice.class.getName());

  private boolean isMutable = true;
  private List<Message> errors = Lists.newArrayList();
  private List<InjectionPoint> injectionPoints = Lists.newArrayList();
  private List<Object> sources = Lists.newArrayList();
  private Object sourceForNextError = null;

  public Errors userReportedError(String messageFormat, List<Object> arguments) {
    return addMessage(messageFormat, arguments);
  }

  public void pushInjectionPoint(InjectionPoint<?> injectionPoint) {
    injectionPoints.add(injectionPoint);
  }

  public void popInjectionPoint(InjectionPoint<?> injectionPoint) {
    InjectionPoint popped = injectionPoints.remove(injectionPoints.size() - 1);
    checkArgument(injectionPoint == popped);
  }

  public Errors pushSource(Object source) {
    sources.add(source);
    return this;
  }

  public Errors popSource(Object source) {
    Object popped = sources.remove(sources.size() - 1);
    checkArgument(popped == source);
    return this;
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

  public Errors missingBindingButOthersExist(Key<?> key, List<String> otherNames) {
    return addMessage(
        "Binding to %s not found. Annotations on other bindings to that type include: %s", key,
        otherNames);
  }

  public Errors converterReturnedNull(String stringValue, Object source,
      TypeLiteral<?> type, MatcherAndConverter<?> matchingConverter) {
    return addMessage("Received null converting '%s' (bound at %s) to %s%n"
        + " using %s.",
        stringValue, source, type, matchingConverter);
  }

  public Errors conversionTypeError(String stringValue, Object source, TypeLiteral<?> type,
      MatcherAndConverter<?> matchingConverter, Object converted) {
    return addMessage("Type mismatch converting '%s' (bound at %s) to %s%n"
        + " using %s.%n"
        + " Converter returned %s.",
        stringValue, source, type, matchingConverter, converted);
  }

  public Errors conversionError(String stringValue, Object source,
      TypeLiteral<?> type, MatcherAndConverter<?> matchingConverter, Exception cause) {
    return addMessage(cause, "Error converting '%s' (bound at %s) to %s%n" 
        + " using %s.%n"
        + " Reason: %s",
        stringValue, source, type, matchingConverter, cause);
  }

  public Errors ambiguousTypeConversion(String stringValue, TypeLiteral<?> type,
      MatcherAndConverter<?> matchingConverter, MatcherAndConverter<?> converter) {
    return addMessage("Error converting '%s' to  %s. "
        + "More than one type converter can apply: %s, and %s. "
        + "Please adjust your type converter configuration to avoid  overlapping matches.",
        stringValue, type, matchingConverter, converter);
  }

  public Errors bindingNotFound(Key<?> key, String message) {
    return addMessage("Binding to %s not found: %s", key, message);
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

  public Errors exceptionReportedByModuleSeeLogs(Throwable throwable) {
    String rootMessage = getRootMessage(throwable);

    logger.log(Level.INFO, format("An exception was caught and reported. Message: %s", rootMessage),
        throwable);
    return addMessage(throwable,
        "An exception was caught and reported. See log for details. Message: %s", rootMessage);
  }

  public Errors missingBindingAnnotation(Object source) {
    return addMessage("Please annotate with @BindingAnnotation. Bound at %s.", source);
  }

  public Errors missingRuntimeRetention(Object source) {
    return addMessage("Please annotate with @Retention(RUNTIME). Bound at %s.", source);
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

  private static final String CONSTRUCTOR_RULES =
      "Classes must have either one (and only one) constructor "
          + "annotated with @Inject or a zero-argument constructor.";

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

  public Errors errorInProvider(RuntimeException runtimeException) {
    Errors newErrors = ProvisionException.getErrors(runtimeException);
    if (newErrors != null) {
      return merge(newErrors);
    } else {
      return addMessage(runtimeException, "Error in custom provider, %s", runtimeException);
    }
  }

  public Errors cannotInjectNull(Object source) {
    return addMessage("null returned by binding at %s", source);
  }

  public Errors cannotInjectNull(Object source, Member member, int parameterIndex) {
    String parameterName = (parameterIndex != -1)
        ? "parameter " + parameterIndex + " of "
        : "";
    return addMessage("null returned by binding at %s%n but %s%s is not @Nullable",
        source, parameterName, member);
  }

  public Errors cannotInjectRawProvider() {
    return addMessage("Cannot inject a Provider that has no type parameter");
  }

  public Errors cannotSatisfyCircularDependency(Class<?> expectedType) {
    return addMessage(
        "Tried proxying %s to support a circular dependency, but it is not an interface.",
        expectedType);
  }

  public Errors at(Object source) {
    sourceForNextError = source;
    return this;
  }

  public Errors makeImmutable() {
    isMutable = false;
    return this;
  }

  public void throwProvisionExceptionIfNecessary() {
    makeImmutable();
    if (!hasErrors()) {
      return;
    }

    throw toProvisionException();
  }

  public void throwCreationExceptionIfErrorsExist() {
    if (!hasErrors()) {
      return;
    }

    makeImmutable();
    throw new CreationException(getMessages());
  }


  public ProvisionException toProvisionException() {
    throw new ProvisionException(this);
  }

  public Errors merge(Errors moreErrors) {
    checkState(isMutable);

    if (moreErrors != this) {
      for (Message message : moreErrors.errors) {
        List<InjectionPoint> injectionPoints = Lists.newArrayList();
        injectionPoints.addAll(this.injectionPoints);
        injectionPoints.addAll(message.getInjectionPoints());
        errors.add(new Message(message.getSource(), message.getMessage(), injectionPoints,
            message.getCause()));
      }
    }

    return this;
  }

  public void throwIfNecessary() throws ResolveFailedException {
    if (!hasErrors()) {
      return;
    }

    throw toException();
  }

  public ResolveFailedException toException() {
    return new ResolveFailedException(this);
  }

  public boolean hasErrors() {
    return !errors.isEmpty();
  }

  private Errors addMessage(String messageFormat, Object... arguments) {
    return addMessage(null, messageFormat, arguments);
  }

  private Errors addMessage(Throwable cause, String messageFormat, Object... arguments) {
    if (!isMutable) {
      throw new AssertionError();
    }

    String message = format(messageFormat, arguments);

    Object source;
    if (sourceForNextError != null) {
      source = sourceForNextError;
      sourceForNextError = null;
    } else if (!sources.isEmpty()) {
      source = sources.get(sources.size() - 1);
    } else {
      source = SourceProviders.UNKNOWN_SOURCE;
    }

    errors.add(new Message(source, message, ImmutableList.copyOf(injectionPoints), cause));
    return this;
  }

  private String format(String messageFormat, Object... arguments) {
    for (int i = 0; i < arguments.length; i++) {
      arguments[i] = Errors.convert(arguments[i]);
    }
    return String.format(messageFormat, arguments);
  }

  /**
   * Returns a mutable copy.
   */
  public Errors copy() {
    return new Errors().merge(this);
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

      List<InjectionPoint> injectionPoints = errorMessage.getInjectionPoints();
      for (int i = injectionPoints.size() - 1; i >= 0; i--) {
        InjectionPoint injectionPoint = injectionPoints.get(i);

        Key key = injectionPoint.getKey();
        fmt.format("  while locating %s%n", convert(key));

        Member member = injectionPoint.getMember();
        if (member == null) {
          continue;
        }

        Class<? extends Member> memberType = MoreTypes.memberType(member);
        if (memberType == Field.class) {
          fmt.format("    for field at %s%n", StackTraceElements.forMember(member));

        } else if (memberType == Method.class || memberType == Constructor.class) {
          fmt.format("    for parameter %s at %s%n",
              injectionPoint.getParameterIndex(), StackTraceElements.forMember(member));
        }
      }

      fmt.format("%n");
    }

    return fmt.format("%s error[s]", errorMessages.size()).toString();
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

  static final Collection<Converter<?>> converters = ImmutableList.of(
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
}
