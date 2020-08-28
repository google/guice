package com.google.inject.internal;

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
import java.util.Optional;

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
      int ordinal = dependency.getParameterIndex() + 1;
      Optional<String> name = getParameterName(member, dependency.getParameterIndex());
      formatter.format(
          "%s \\_ for %s parameter %s%n",
          INDENT, ordinal + Messages.getOrdinalSuffix(ordinal), Messages.redBold(name.orElse("")));
    }
  }

  private static Optional<String> getParameterName(Member member, int parameterIndex) {
    Parameter parameter = null;
    if (member instanceof Constructor) {
      parameter = ((Constructor<?>) member).getParameters()[parameterIndex];
    } else if (member instanceof Method) {
      parameter = ((Method) member).getParameters()[parameterIndex];
    }
    if (parameter != null && parameter.isNamePresent()) {
      return Optional.of(parameter.getName());
    }
    return Optional.empty();
  }

  private static String getModuleStack(ElementSource elementSource) {
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
}
