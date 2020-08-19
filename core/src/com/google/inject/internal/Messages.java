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
import static java.util.stream.Collectors.joining;

import com.google.common.base.Equivalence;
import com.google.common.base.Objects;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.util.Classes;
import com.google.inject.internal.util.StackTraceElements;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.ElementSource;
import com.google.inject.spi.ErrorDetail;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.Message;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.util.Arrays;
import java.util.Collection;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Utility methods for {@link Message} objects */
public final class Messages {
  private Messages() {}

  /** Prepends the list of sources to the given {@link Message} */
  static Message mergeSources(List<Object> sources, Message message) {
    List<Object> messageSources = message.getSources();
    // It is possible that the end of getSources() and the beginning of message.getSources() are
    // equivalent, in this case we should drop the repeated source when joining the lists.  The
    // most likely scenario where this would happen is when a scoped binding throws an exception,
    // due to the fact that InternalFactoryToProviderAdapter applies the binding source when
    // merging errors.
    if (!sources.isEmpty()
        && !messageSources.isEmpty()
        && Objects.equal(messageSources.get(0), sources.get(sources.size() - 1))) {
      messageSources = messageSources.subList(1, messageSources.size());
    }
    return message.withSource(
        ImmutableList.builder().addAll(sources).addAll(messageSources).build());
  }

  /**
   * Calls {@link String#format} after converting the arguments using some standard guice formatting
   * for {@link Key}, {@link Class} and {@link Member} objects.
   */
  public static String format(String messageFormat, Object... arguments) {
    for (int i = 0; i < arguments.length; i++) {
      arguments[i] = convert(arguments[i]);
    }
    return String.format(messageFormat, arguments);
  }

  /** Returns the formatted message for an exception with the specified messages. */
  public static String formatMessages(String heading, Collection<Message> errorMessages) {
    Formatter fmt = new Formatter().format(heading).format(":%n%n");
    int index = 1;
    boolean displayCauses = getOnlyCause(errorMessages) == null;

    List<ErrorDetail<?>> remainingErrors =
        errorMessages.stream().map(Message::getErrorDetail).collect(Collectors.toList());

    Map<Equivalence.Wrapper<Throwable>, Integer> causes = Maps.newHashMap();
    while (!remainingErrors.isEmpty()) {
      ErrorDetail<?> currentError = remainingErrors.get(0);
      // Split the remaining errors into 2 groups, one that contains mergeable errors with
      // currentError and the other that need to be formatted separately in the next iteration.
      Map<Boolean, List<ErrorDetail<?>>> partitionedByMergeable =
          remainingErrors.subList(1, remainingErrors.size()).stream()
              .collect(Collectors.partitioningBy(currentError::isMergeable));

      remainingErrors = partitionedByMergeable.get(false);

      currentError.format(index, partitionedByMergeable.get(true), fmt);

      Throwable cause = currentError.getCause();
      if (displayCauses && cause != null) {
        Equivalence.Wrapper<Throwable> causeEquivalence = ThrowableEquivalence.INSTANCE.wrap(cause);
        if (!causes.containsKey(causeEquivalence)) {
          causes.put(causeEquivalence, index);
          fmt.format("Caused by: %s", Throwables.getStackTraceAsString(cause));
        } else {
          int causeIdx = causes.get(causeEquivalence);
          fmt.format(
              "Caused by: %s (same stack trace as error #%s)",
              cause.getClass().getName(), causeIdx);
        }
      }
      fmt.format("%n");
      index++;
    }

    if (index == 2) {
      fmt.format("1 error");
    } else {
      fmt.format("%s errors", index - 1);
    }

    String message = fmt.toString();
    if (InternalFlags.enableExperimentalErrorMessages()) {
      return PackageNameCompressor.compressPackagesInMessage(message);
    }
    return message;
  }

  /**
   * Creates a new Message without a cause.
   *
   * @param errorId The enum id for the error
   * @param messageFormat Format string
   * @param arguments format string arguments
   */
  public static Message create(ErrorId errorId, String messageFormat, Object... arguments) {
    return create(errorId, null, messageFormat, arguments);
  }

  /**
   * Creates a new Message with the given cause.
   *
   * @param errorId The enum id for the error
   * @param cause The exception that caused the error
   * @param messageFormat Format string
   * @param arguments format string arguments
   */
  public static Message create(
      ErrorId errorId, Throwable cause, String messageFormat, Object... arguments) {
    return create(errorId, cause, ImmutableList.of(), messageFormat, arguments);
  }

  /**
   * Creates a new Message with the given cause and a binding source stack.
   *
   * @param errorId The enum id for the error
   * @param cause The exception that caused the error
   * @param sources The binding sources for the source stack
   * @param messageFormat Format string
   * @param arguments format string arguments
   */
  public static Message create(
      ErrorId errorId,
      Throwable cause,
      List<Object> sources,
      String messageFormat,
      Object... arguments) {
    String message = format(messageFormat, arguments);
    return new Message(errorId, sources, message, cause);
  }

  /** Formats an object in a user friendly way. */
  static Object convert(Object o) {
    ElementSource source = null;
    if (o instanceof ElementSource) {
      source = (ElementSource) o;
      o = source.getDeclaringSource();
    }
    return convert(o, source);
  }

  static Object convert(Object o, ElementSource source) {
    for (Converter<?> converter : converters) {
      if (converter.appliesTo(o)) {
        return appendModules(converter.convert(o), source);
      }
    }
    return appendModules(o, source);
  }

  private static Object appendModules(Object source, ElementSource elementSource) {
    String modules = moduleSourceString(elementSource);
    if (modules.length() == 0) {
      return source;
    } else {
      return source + modules;
    }
  }

  private static String moduleSourceString(ElementSource elementSource) {
    // if we only have one module (or don't know what they are), then don't bother
    // reporting it, because the source already is going to report exactly that module.
    if (elementSource == null) {
      return "";
    }
    List<String> modules = Lists.newArrayList(elementSource.getModuleClassNames());
    // Insert any original element sources w/ module info into the path.
    while (elementSource.getOriginalElementSource() != null) {
      elementSource = elementSource.getOriginalElementSource();
      modules.addAll(0, elementSource.getModuleClassNames());
    }
    if (modules.size() <= 1) {
      return "";
    }

    // Ideally we'd do:
    //    return Joiner.on(" -> ")
    //        .appendTo(new StringBuilder(" (via modules: "), Lists.reverse(modules))
    //        .append(")").toString();
    // ... but for some reason we can't find Lists.reverse, so do it the boring way.
    StringBuilder builder = new StringBuilder(" (via modules: ");
    for (int i = modules.size() - 1; i >= 0; i--) {
      builder.append(modules.get(i));
      if (i != 0) {
        builder.append(" -> ");
      }
    }
    builder.append(")");
    return builder.toString();
  }

  static void formatSource(Formatter formatter, Object source) {
    ElementSource elementSource = null;
    if (source instanceof ElementSource) {
      elementSource = (ElementSource) source;
      source = elementSource.getDeclaringSource();
    }
    formatSource(formatter, source, elementSource);
  }

  static void formatSource(Formatter formatter, Object source, ElementSource elementSource) {
    String modules = moduleSourceString(elementSource);
    if (source instanceof Dependency) {
      Dependency<?> dependency = (Dependency<?>) source;
      InjectionPoint injectionPoint = dependency.getInjectionPoint();
      if (injectionPoint != null) {
        formatInjectionPoint(formatter, dependency, injectionPoint, elementSource);
      } else {
        formatSource(formatter, dependency.getKey(), elementSource);
      }

    } else if (source instanceof InjectionPoint) {
      formatInjectionPoint(formatter, null, (InjectionPoint) source, elementSource);

    } else if (source instanceof Class) {
      formatter.format("  at %s%s%n", StackTraceElements.forType((Class<?>) source), modules);

    } else if (source instanceof Member) {
      formatter.format("  at %s%s%n", StackTraceElements.forMember((Member) source), modules);

    } else if (source instanceof TypeLiteral) {
      formatter.format("  while locating %s%s%n", source, modules);

    } else if (source instanceof Key) {
      Key<?> key = (Key<?>) source;
      formatter.format("  while locating %s%n", convert(key, elementSource));

    } else if (source instanceof Thread) {
      formatter.format("  in thread %s%n", source);

    } else {
      formatter.format("  at %s%s%n", source, modules);
    }
  }

  private static void formatInjectionPoint(
      Formatter formatter,
      Dependency<?> dependency,
      InjectionPoint injectionPoint,
      ElementSource elementSource) {
    Member member = injectionPoint.getMember();
    Class<? extends Member> memberType = Classes.memberType(member);

    if (memberType == Field.class) {
      dependency = injectionPoint.getDependencies().get(0);
      formatter.format("  while locating %s%n", convert(dependency.getKey(), elementSource));
      formatter.format("    for field at %s%n", StackTraceElements.forMember(member));

    } else if (dependency != null) {
      formatter.format("  while locating %s%n", convert(dependency.getKey(), elementSource));
      formatter.format("    for %s%n", formatParameter(dependency));

    } else {
      formatSource(formatter, injectionPoint.getMember());
    }
  }

  static String formatParameter(Dependency<?> dependency) {
    int ordinal = dependency.getParameterIndex() + 1;
    return String.format(
        "the %s%s parameter of %s",
        ordinal,
        getOrdinalSuffix(ordinal),
        StackTraceElements.forMember(dependency.getInjectionPoint().getMember()));
  }

  /**
   * Maps {@code 1} to the string {@code "1st"} ditto for all non-negative numbers
   *
   * @see <a href="https://en.wikipedia.org/wiki/English_numerals#Ordinal_numbers">
   *     https://en.wikipedia.org/wiki/English_numerals#Ordinal_numbers</a>
   */
  static String getOrdinalSuffix(int ordinal) {
    // negative ordinals don't make sense, we allow zero though because we are programmers
    checkArgument(ordinal >= 0);
    if ((ordinal / 10) % 10 == 1) {
      // all the 'teens' are weird
      return "th";
    } else {
      // could use a lookup table? any better?
      switch (ordinal % 10) {
        case 1:
          return "st";
        case 2:
          return "nd";
        case 3:
          return "rd";
        default:
          return "th";
      }
    }
  }

  private abstract static class Converter<T> {

    final Class<T> type;

    Converter(Class<T> type) {
      this.type = type;
    }

    boolean appliesTo(Object o) {
      return o != null && type.isAssignableFrom(o.getClass());
    }

    String convert(Object o) {
      return toString(type.cast(o));
    }

    abstract String toString(T t);
  }

  @SuppressWarnings({"unchecked", "rawtypes"}) // rawtypes aren't avoidable
  private static final Collection<Converter<?>> converters =
      ImmutableList.of(
          new Converter<Class>(Class.class) {
            @Override
            public String toString(Class c) {
              return c.getName();
            }
          },
          new Converter<Member>(Member.class) {
            @Override
            public String toString(Member member) {
              return Classes.toString(member);
            }
          },
          new Converter<Key>(Key.class) {
            @Override
            public String toString(Key key) {
              if (key.getAnnotationType() != null) {
                return key.getTypeLiteral()
                    + " annotated with "
                    + (key.getAnnotation() != null ? key.getAnnotation() : key.getAnnotationType());
              } else {
                return key.getTypeLiteral().toString();
              }
            }
          });

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

      if (onlyCause != null && !ThrowableEquivalence.INSTANCE.equivalent(onlyCause, messageCause)) {
        return null;
      }

      onlyCause = messageCause;
    }

    return onlyCause;
  }

  private static final class ThrowableEquivalence extends Equivalence<Throwable> {
    static final ThrowableEquivalence INSTANCE = new ThrowableEquivalence();

    @Override
    protected boolean doEquivalent(Throwable a, Throwable b) {
      return a.getClass().equals(b.getClass())
          && Objects.equal(a.getMessage(), b.getMessage())
          && Arrays.equals(a.getStackTrace(), b.getStackTrace())
          && equivalent(a.getCause(), b.getCause());
    }

    @Override
    protected int doHash(Throwable t) {
      return Objects.hashCode(t.getClass().hashCode(), t.getMessage(), hash(t.getCause()));
    }
  }

  private enum FormatOptions {
    RED("\u001B[31m"),
    BOLD("\u001B[1m"),
    FAINT("\u001B[2m"),
    ITALIC("\u001B[3m"),
    UNDERLINE("\u001B[4m"),
    RESET("\u001B[0m");

    private final String ansiCode;

    FormatOptions(String ansiCode) {
      this.ansiCode = ansiCode;
    }
  }

  private static final String formatText(String text, FormatOptions... options) {
    if (!InternalFlags.enableColorizeErrorMessages()) {
      return text;
    }
    return String.format(
        "%s%s%s",
        Arrays.stream(options).map(option -> option.ansiCode).collect(joining()),
        text,
        FormatOptions.RESET.ansiCode);
  }

  public static final String bold(String text) {
    return formatText(text, FormatOptions.BOLD);
  }

  public static final String redBold(String text) {
    return formatText(text, FormatOptions.RED, FormatOptions.BOLD);
  }

  public static final String underline(String text) {
    return formatText(text, FormatOptions.UNDERLINE);
  }

  public static final String faint(String text) {
    return formatText(text, FormatOptions.FAINT);
  }
}
