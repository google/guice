package com.google.inject.internal;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.ErrorDetail;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

/** Error reported when Guice can't find an useable constructor to create objects. */
final class MissingConstructorError extends InternalErrorDetail<MissingConstructorError> {
  private final TypeLiteral<?> type;
  private final boolean atInjectRequired;

  MissingConstructorError(TypeLiteral<?> type, boolean atInjectRequired, List<Object> sources) {
    super(
        ErrorId.MISSING_CONSTRUCTOR,
        "No injectable constructor for type " + type + ".",
        sources,
        null);
    this.type = type;
    this.atInjectRequired = atInjectRequired;
  }

  @Override
  public boolean isMergeable(ErrorDetail<?> other) {
    if (other instanceof MissingConstructorError) {
      MissingConstructorError otherMissing = (MissingConstructorError) other;
      return Objects.equal(type, otherMissing.type)
          && Objects.equal(atInjectRequired, otherMissing.atInjectRequired);
    }
    return false;
  }

  @Override
  protected void formatDetail(List<ErrorDetail<?>> mergeableErrors, Formatter formatter) {
    formatter.format("%n");
    Class<?> rawType = type.getRawType();
    if (atInjectRequired) {
      formatter.format(
          "Injector is configured to require @Inject constructors but %s does not have a @Inject"
              + " annotated constructor.%n",
          rawType);
    } else {
      Constructor<?> noArgConstructor = null;
      try {
        noArgConstructor = type.getRawType().getDeclaredConstructor();
      } catch (NoSuchMethodException e) {
        // Ignore
      }
      if (noArgConstructor == null) {
        formatter.format(
            "%s does not have a @Inject annotated constructor or a no-arg constructor.%n", rawType);
      } else if (Modifier.isPrivate(noArgConstructor.getModifiers())
          && !Modifier.isPrivate(rawType.getModifiers())) {
        formatter.format(
            "%s has a private no-arg constructor but it's not private. Guice can only use private"
                + " no-arg constructor if it is defined in a private class.%n",
            rawType);
      }
    }
    formatter.format("%n");

    List<List<Object>> sourcesList = new ArrayList<>();
    sourcesList.add(getSources());
    mergeableErrors.forEach(error -> sourcesList.add(error.getSources()));

    formatter.format("%s%n", Messages.bold("Requested by:"));
    int sourceListIndex = 1;
    for (List<Object> sources : sourcesList) {
      ErrorFormatter.formatSources(sourceListIndex++, Lists.reverse(sources), formatter);
    }
  }

  @Override
  public MissingConstructorError withSources(List<Object> newSources) {
    return new MissingConstructorError(type, atInjectRequired, newSources);
  }
}
