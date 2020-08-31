package com.google.inject.internal;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.spi.ErrorDetail;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.stream.Collectors;

/** Error reported by Guice when a key is not bound in the injector. */
final class MissingImplementationError<T>
    extends InternalErrorDetail<MissingImplementationError<T>> {

  private final Key<T> key;
  private final ImmutableList<String> suggestions;

  public MissingImplementationError(Key<T> key, Injector injector, List<Object> sources) {
    this(key, MissingImplementationErrorHints.getSuggestions(key, injector), sources);
  }

  private MissingImplementationError(
      Key<T> key, ImmutableList<String> suggestions, List<Object> sources) {
    super(
        ErrorId.MISSING_IMPLEMENTATION,
        String.format("No implementation for %s was bound.", Messages.convert(key)),
        sources,
        null);
    this.key = key;
    this.suggestions = suggestions;
  }

  @Override
  public boolean isMergeable(ErrorDetail<?> otherError) {
    return otherError instanceof MissingImplementationError
        && ((MissingImplementationError) otherError).key.equals(this.key);
  }

  @Override
  public void formatDetail(List<ErrorDetail<?>> mergeableErrors, Formatter formatter) {
    if (!suggestions.isEmpty()) {
      suggestions.forEach(formatter::format);
    }
    List<List<Object>> sourcesList = new ArrayList<>();
    sourcesList.add(getSources());
    sourcesList.addAll(
        mergeableErrors.stream().map(ErrorDetail::getSources).collect(Collectors.toList()));

    List<List<Object>> filteredSourcesList =
        sourcesList.stream()
            .map(this::trimSource)
            .filter(sources -> !sources.isEmpty())
            .collect(Collectors.toList());

    if (!filteredSourcesList.isEmpty()) {
      formatter.format("%n%s%n", Messages.bold("Requested by:"));
      int sourceListIndex = 1;
      for (List<Object> sources : filteredSourcesList) {
        ErrorFormatter.formatSources(sourceListIndex++, Lists.reverse(sources), formatter);
      }
    }
  }

  @Override
  public MissingImplementationError<T> withSources(List<Object> newSources) {
    return new MissingImplementationError<T>(key, suggestions, newSources);
  }

  /** Omit the key itself in the source list since the information is redundant. */
  private List<Object> trimSource(List<Object> sources) {
    return sources.stream().filter(source -> !source.equals(this.key)).collect(Collectors.toList());
  }
}
