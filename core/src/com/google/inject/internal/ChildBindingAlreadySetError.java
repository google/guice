package com.google.inject.internal;

import com.google.common.collect.ImmutableList;
import com.google.inject.Key;
import com.google.inject.spi.ErrorDetail;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Error reported by Guice when a key is already bound in one or more child injectors or private
 * modules.
 */
final class ChildBindingAlreadySetError extends InternalErrorDetail<ChildBindingAlreadySetError> {
  private final Key<?> key;
  private final ImmutableList<Object> existingSoruces;

  ChildBindingAlreadySetError(Key<?> key, Iterable<Object> existingSoruces, List<Object> sources) {
    super(
        ErrorId.CHILD_BINDING_ALREADY_SET,
        String.format(
            "Unable to create binding for %s because it was already configured on one or more"
                + " child injectors or private modules.",
            Messages.convert(key)),
        sources,
        null);
    this.key = key;
    this.existingSoruces =
        existingSoruces == null ? ImmutableList.of() : ImmutableList.copyOf(existingSoruces);
  }

  @Override
  public boolean isMergeable(ErrorDetail<?> otherError) {
    return otherError instanceof ChildBindingAlreadySetError
        && ((ChildBindingAlreadySetError) otherError).key.equals(this.key);
  }

  @Override
  public void formatDetail(List<ErrorDetail<?>> mergeableErrors, Formatter formatter) {
    if (existingSoruces.isEmpty()) {
      formatter.format("%n%s%n", Messages.bold("Bound by a just-in-time binding"));
    } else {
      formatter.format("%n%s%n", Messages.bold("Bound at:"));
      int index = 1;
      for (Object source : existingSoruces) {
        formatter.format("%-2s: ", index++);
        new SourceFormatter(source, formatter, /* omitPreposition= */ true).format();
      }
    }

    List<List<Object>> sourcesList = new ArrayList<>();
    sourcesList.add(getSources());
    mergeableErrors.forEach(error -> sourcesList.add(error.getSources()));

    List<List<Object>> filteredSources =
        sourcesList.stream()
            .map(this::trimSource)
            .filter(list -> !list.isEmpty())
            .collect(Collectors.toList());
    if (!filteredSources.isEmpty()) {
      formatter.format("%n%s%n", Messages.bold("Requested by:"));
      for (int i = 0; i < sourcesList.size(); i++) {
        ErrorFormatter.formatSources(i + 1, sourcesList.get(i), formatter);
      }
    }

    // TODO(b/151482394): Detect if the key was bound in any PrivateModule and suggest exposing the
    // key in those cases.
  }

  @Override
  public ChildBindingAlreadySetError withSources(List<Object> newSources) {
    return new ChildBindingAlreadySetError(key, existingSoruces, newSources);
  }

  /** Omit the key itself in the source list since the information is redundant. */
  private List<Object> trimSource(List<Object> sources) {
    return sources.stream().filter(source -> !source.equals(this.key)).collect(Collectors.toList());
  }
}
