package com.google.inject.internal;

import com.google.common.collect.Lists;
import com.google.inject.Key;
import com.google.inject.spi.ErrorDetail;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.stream.Collectors;

/** Error reported by Guice when a key is not bound in the injector. */
final class MissingImplementationError extends ErrorDetail<MissingImplementationError> {
  private final Key<?> key;

  public MissingImplementationError(Key<?> key, List<Object> sources) {
    super(
        String.format("No implementation for %s was bound.", Messages.convert(key)), sources, null);
    this.key = key;
  }

  @Override
  public boolean isMergeable(ErrorDetail<?> otherError) {
    return otherError instanceof MissingImplementationError
        && ((MissingImplementationError) otherError).key.equals(this.key);
  }

  @Override
  public void format(int index, List<ErrorDetail<?>> mergeableErrors, Formatter formatter) {
    List<List<Object>> sourcesList = new ArrayList<>();
    sourcesList.add(getSources());
    sourcesList.addAll(
        mergeableErrors.stream().map(ErrorDetail::getSources).collect(Collectors.toList()));
    List<List<Object>> filteredSourcesList =
        sourcesList.stream()
            .map(this::trimSource)
            .filter(sources -> !sources.isEmpty())
            .collect(Collectors.toList());

    formatter.format("%s) %s: %s%n", index, "[Guice/MissingImplementation]", getMessage());
    if (!filteredSourcesList.isEmpty()) {
      formatter.format("%n%s%n", "Requested by:");
      int sourceListIndex = 1;
      for (List<Object> sources : filteredSourcesList) {
        ErrorFormatter.formatSources(sourceListIndex++, Lists.reverse(sources), formatter);
      }
    }
    formatter.format("%n");
  }

  @Override
  public MissingImplementationError withSources(List<Object> newSources) {
    return new MissingImplementationError(key, newSources);
  }

  /** Omit the key itself in the source list since the information is redundant. */
  private List<Object> trimSource(List<Object> sources) {
    return sources.stream().filter(source -> !source.equals(this.key)).collect(Collectors.toList());
  }
}
