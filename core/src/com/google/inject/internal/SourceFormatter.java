package com.google.inject.internal;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.util.Classes;
import com.google.inject.internal.util.StackTraceElements;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.ElementSource;
import com.google.inject.spi.InjectionPoint;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Formatter;
import java.util.List;

/** Formatting a single source in Guice error message. */
final class SourceFormatter {
  static final String INDENT = Strings.repeat(" ", 5);

  private final Object source;
  private final Formatter formatter;
  private final boolean omitPreposition;
  private final String moduleStack;

  SourceFormatter(Object source, Formatter formatter, boolean omitPreposition) {
    if (source instanceof ElementSource) {
      ElementSource elementSource = (ElementSource) source;
      this.source = elementSource.getDeclaringSource();
      this.moduleStack = getModuleStack(elementSource);
    } else {
      this.source = source;
      this.moduleStack = "";
    }
    this.formatter = formatter;
    this.omitPreposition = omitPreposition;
  }

  void format() {
    boolean appendModuleSource = !moduleStack.isEmpty();
    if (source instanceof Dependency) {
      formatDependency((Dependency<?>) source);
    } else if (source instanceof InjectionPoint) {
      formatInjectionPoint(null, (InjectionPoint) source);
    } else if (source instanceof Class) {
      formatter.format("%s%s%n", preposition("at "), StackTraceElements.forType((Class<?>) source));
    } else if (source instanceof Member) {
      formatMember((Member) source);
    } else if (source instanceof TypeLiteral) {
      formatter.format("%s%s%n", preposition("while locating "), source);
    } else if (source instanceof Key) {
      formatKey((Key<?>) source);
    } else if (source instanceof Thread) {
      appendModuleSource = false;
      formatter.format("%s%s%n", preposition("in thread "), source);
    } else {
      formatter.format("%s%s%n", preposition("at "), source);
    }

    if (appendModuleSource) {
      formatter.format("%s \\_ installed by: %s%n", INDENT, moduleStack);
    }
  }

  private String preposition(String prepostition) {
    if (omitPreposition) {
      return "";
    }
    return prepostition;
  }

  private void formatDependency(Dependency<?> dependency) {
    InjectionPoint injectionPoint = dependency.getInjectionPoint();
    if (injectionPoint != null) {
      formatInjectionPoint(dependency, injectionPoint);
    } else {
      formatKey(dependency.getKey());
    }
  }

  private void formatKey(Key<?> key) {
    formatter.format("%s%s%n", preposition("while locating "), Messages.convert(key));
  }

  private void formatMember(Member member) {
    formatter.format("%s%s%n", preposition("at "), StackTraceElements.forMember(member));
  }

  private void formatInjectionPoint(Dependency<?> dependency, InjectionPoint injectionPoint) {
    Member member = injectionPoint.getMember();
    Class<? extends Member> memberType = Classes.memberType(member);
    formatMember(injectionPoint.getMember());
    if (memberType == Field.class) {
      formatter.format("%s \\_ for field %s%n", INDENT, Messages.redBold(member.getName()));
    } else if (dependency != null) {

      formatter.format("%s \\_ for %s%n", INDENT, getParameterName(dependency));
    }
  }

  static String getModuleStack(ElementSource elementSource) {
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
    return String.join(" -> ", Lists.reverse(modules));
  }

  static String getParameterName(Dependency<?> dependency) {
    int parameterIndex = dependency.getParameterIndex();
    int ordinal = parameterIndex + 1;
    Member member = dependency.getInjectionPoint().getMember();
    Parameter parameter = null;
    if (member instanceof Constructor) {
      parameter = ((Constructor<?>) member).getParameters()[parameterIndex];
    } else if (member instanceof Method) {
      parameter = ((Method) member).getParameters()[parameterIndex];
    }
    String parameterName = "";
    if (parameter != null && parameter.isNamePresent()) {
      parameterName = parameter.getName();
    }
    return String.format(
        "%s%s parameter%s",
        ordinal,
        getOrdinalSuffix(ordinal),
        parameterName.isEmpty() ? "" : " " + Messages.redBold(parameterName));
  }

  /**
   * Maps {@code 1} to the string {@code "1st"} ditto for all non-negative numbers
   *
   * @see <a href="https://en.wikipedia.org/wiki/English_numerals#Ordinal_numbers">
   *     https://en.wikipedia.org/wiki/English_numerals#Ordinal_numbers</a>
   */
  private static String getOrdinalSuffix(int ordinal) {
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
}
