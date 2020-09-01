/*
 * Copyright (C) 2017 Google Inc.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.Guice;
import com.google.inject.Key;
import com.google.inject.MembersInjector;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.util.SourceProvider;
import com.google.inject.internal.util.StackTraceElements;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.InjectionListener;
import com.google.inject.spi.Message;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A checked exception for provisioning errors.
 *
 * <p>This is the internal dual of {@link ProvisionException}, similar to the relationship between
 * {@link com.google.inject.ConfigurationException} and {@link ErrorsException}. This is useful for
 * several reasons:
 *
 * <ul>
 *   <li>Since it is a checked exception, we get some assistance from the java compiler in ensuring
 *       that we correctly handle it everywhere. ProvisionException is unchecked.
 *   <li>Since this is an internal package, we can add useful construction and mutation APIs that
 *       would be undesirable in a public supported API.
 * </ul>
 *
 * <p>This exception will be thrown when errors are encountered during provisioning, ErrorsException
 * will continue to be used for errors that are encountered during provisioning and both make use of
 * the {@link Message} as the core model.
 *
 * <p>NOTE: this object stores a list of messages but in the most common case the cardinality will
 * be 1. The only time that multiple errors might be reported via this mechanism is when {@link
 * #errorInUserCode} is called with an exception that holds multiple errors (like
 * ProvisionException).
 */
public final class InternalProvisionException extends Exception {
  private static final Logger logger = Logger.getLogger(Guice.class.getName());
  private static final Set<Dependency<?>> warnedDependencies =
      Collections.newSetFromMap(new ConcurrentHashMap<Dependency<?>, Boolean>());


  public static InternalProvisionException circularDependenciesDisabled(Class<?> expectedType) {
    return create(
        ErrorId.CIRCULAR_PROXY_DISABLED,
        "Found a circular dependency involving %s, and circular dependencies are disabled.",
        expectedType);
  }

  public static InternalProvisionException cannotProxyClass(Class<?> expectedType) {
    return create(
        ErrorId.CAN_NOT_PROXY_CLASS,
        "Tried proxying %s to support a circular dependency, but it is not an interface.",
        expectedType);
  }

  public static InternalProvisionException create(
      ErrorId errorId, String format, Object... arguments) {
    return new InternalProvisionException(Messages.create(errorId, format, arguments));
  }

  public static InternalProvisionException errorInUserCode(
      ErrorId errorId, Throwable cause, String messageFormat, Object... arguments) {
    Collection<Message> messages = Errors.getMessagesFromThrowable(cause);
    if (!messages.isEmpty()) {
      // TODO(lukes): it seems like we are dropping some valuable context here..
      // consider eliminating this special case
      return new InternalProvisionException(messages);
    } else {
      return new InternalProvisionException(
          Messages.create(errorId, cause, messageFormat, arguments));
    }
  }

  public static InternalProvisionException subtypeNotProvided(
      Class<? extends javax.inject.Provider<?>> providerType, Class<?> type) {
    return create(
        ErrorId.SUBTYPE_NOT_PROVIDED, "%s doesn't provide instances of %s.", providerType, type);
  }

  public static InternalProvisionException errorInProvider(Throwable cause) {
    if (InternalFlags.enableExperimentalErrorMessages()) {
      return errorInUserCode(ErrorId.ERROR_IN_CUSTOM_PROVIDER, cause, "%s", cause);
    }
    return errorInUserCode(
        ErrorId.ERROR_IN_CUSTOM_PROVIDER, cause, "Error in custom provider, %s", cause);
  }

  public static InternalProvisionException errorInjectingMethod(Throwable cause) {
    if (InternalFlags.enableExperimentalErrorMessages()) {
      return errorInUserCode(ErrorId.ERROR_INJECTING_METHOD, cause, "%s", cause);
    }
    return errorInUserCode(
        ErrorId.ERROR_INJECTING_METHOD, cause, "Error injecting method, %s", cause);
  }

  public static InternalProvisionException errorInjectingConstructor(Throwable cause) {
    if (InternalFlags.enableExperimentalErrorMessages()) {
      return errorInUserCode(ErrorId.ERROR_INJECTING_CONSTRUCTOR, cause, "%s", cause);
    }
    return errorInUserCode(
        ErrorId.ERROR_INJECTING_CONSTRUCTOR, cause, "Error injecting constructor, %s", cause);
  }

  public static InternalProvisionException errorInUserInjector(
      MembersInjector<?> listener, TypeLiteral<?> type, RuntimeException cause) {
    return errorInUserCode(
        ErrorId.ERROR_IN_USER_INJECTOR,
        cause,
        "Error injecting %s using %s.%n Reason: %s",
        type,
        listener,
        cause);
  }

  public static InternalProvisionException jitDisabled(Key<?> key) {
    return create(
        ErrorId.JIT_DISABLED,
        "Explicit bindings are required and %s is not explicitly bound.",
        key);
  }

  public static InternalProvisionException errorNotifyingInjectionListener(
      InjectionListener<?> listener, TypeLiteral<?> type, RuntimeException cause) {
    return errorInUserCode(
        ErrorId.OTHER,
        cause,
        "Error notifying InjectionListener %s of %s.%n Reason: %s",
        listener,
        type,
        cause);
  }

  /**
   * Returns {@code value} if it is non-null or allowed to be null. Otherwise a message is added and
   * an {@code InternalProvisionException} is thrown.
   */
  static void onNullInjectedIntoNonNullableDependency(Object source, Dependency<?> dependency)
      throws InternalProvisionException {
    // Hack to allow null parameters to @Provides methods, for backwards compatibility.
    if (dependency.getInjectionPoint().getMember() instanceof Method) {
      Method annotated = (Method) dependency.getInjectionPoint().getMember();
      if (annotated.isAnnotationPresent(Provides.class)) {
        switch (InternalFlags.getNullableProvidesOption()) {
          case ERROR:
            break; // break out & let the below exception happen
          case IGNORE:
            return; // user doesn't care about injecting nulls to non-@Nullables.
          case WARN:
            // Warn only once, otherwise we spam logs too much.
            if (warnedDependencies.add(dependency)) {
              logger.log(
                  Level.WARNING,
                  "Guice injected null into {0} (a {1}), please mark it @Nullable."
                      + " Use -Dguice_check_nullable_provides_params=ERROR to turn this into an"
                      + " error.",
                  new Object[] {
                    Messages.formatParameter(dependency), Messages.convert(dependency.getKey())
                  });
            }
            return;
        }
      }
    }

    Object formattedDependency =
        (dependency.getParameterIndex() != -1)
            ? Messages.formatParameter(dependency)
            : StackTraceElements.forMember(dependency.getInjectionPoint().getMember());

    throw InternalProvisionException.create(
            ErrorId.NULL_INJECTED_INTO_NON_NULLABLE,
            "null returned by binding at %s%n but %s is not @Nullable",
            source,
            formattedDependency)
        .addSource(source);
  }

  private final List<Object> sourcesToPrepend = new ArrayList<>();
  private final ImmutableList<Message> errors;

  InternalProvisionException(Message error) {
    this(ImmutableList.of(error));
  }

  private InternalProvisionException(Iterable<Message> errors) {
    this.errors = ImmutableList.copyOf(errors);
    checkArgument(!this.errors.isEmpty(), "Can't create a provision exception with no errors");
  }

  /**
   * Prepends the given {@code source} to the stack of binding sources for the errors reported in
   * this exception.
   *
   * <p>See {@link Errors#withSource(Object)}
   *
   * <p>It is expected that this method is called as the exception propagates up the stack.
   *
   * @param source
   * @return {@code this}
   */
  InternalProvisionException addSource(Object source) {
    if (source == SourceProvider.UNKNOWN_SOURCE) {
      return this;
    }
    int sz = sourcesToPrepend.size();
    if (sz > 0 && sourcesToPrepend.get(sz - 1) == source) {
      // This is for when there are two identical sources added in a row.  This behavior is copied
      // from Errors.withSource where it can happen when an constructor/provider method throws an
      // exception
      return this;
    }
    sourcesToPrepend.add(source);
    return this;
  }

  ImmutableList<Message> getErrors() {
    ImmutableList.Builder<Message> builder = ImmutableList.builder();
    // reverse them since sources are added as the exception propagates (so the first source is the
    // last one added)
    List<Object> newSources = Lists.reverse(sourcesToPrepend);
    for (Message error : errors) {
      builder.add(Messages.mergeSources(newSources, error));
    }
    return builder.build();
  }

  /** Returns this exception convered to a ProvisionException. */
  public ProvisionException toProvisionException() {
    ProvisionException exception = new ProvisionException(getErrors());
    return exception;
  }
}
